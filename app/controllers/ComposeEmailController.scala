/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import config.AppConfig
import connectors.{GatekeeperEmailConnector, UpscanInitiateConnector}
import util.UploadProxyController.ErrorResponseHandler.proxyErrorResponse
import util.UploadProxyController.TemporaryFilePart.partitionTrys
import util.UploadProxyController.TemporaryFilePart
import models.{ErrorResponse, InProgress, OutgoingEmail, UploadInfo, UploadedFailedWithErrors, UploadedSuccessfully}
import org.apache.commons.io.Charsets
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData.DataPart
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MultipartFormData, Request, RequestHeader, Result, Results}

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import services.{UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import util.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, FileSizeMimeChecks}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import util.{ErrorAction, MultipartFormExtractor}

import java.nio.file.Path

@Singleton
class ComposeEmailController @Inject()(mcc: MessagesControllerComponents,
                                       composeEmail: ComposeEmail,
                                       emailPreview: EmailPreview,
                                       fileChecksPreview: FileSizeMimeChecks,
                                       emailConnector: GatekeeperEmailConnector,
                                       upscanInitiateConnector: UpscanInitiateConnector,
                                       sentEmail: EmailSentConfirmation,
                                       wsClient: WSClient,
                                       httpClient: HttpClient
                                      )(implicit val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with I18nSupport with Logging {

  lazy val serviceUrl = appConfig.emailBaseUrl

  def email: Action[AnyContent] = Action.async { implicit request =>
    for {
      upscanInitiateResponse <- upscanInitiateConnector.initiateV2(None, None)
      _ <- httpClient.POSTEmpty[UploadInfo](s"$serviceUrl/gatekeeperemail/insertfileuploadstatus?key=${upscanInitiateResponse.fileReference.reference}")
    } yield Ok(composeEmail(upscanInitiateResponse, controllers.ComposeEmailForm.form.fill(ComposeEmailForm("","",""))))
  }

  def sentEmailConfirmation: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(sentEmail()))
  }

  def sendEmail(): Action[AnyContent] = Action.async {
    implicit request => {
      def handleValidForm(form: ComposeEmailForm) = {
        logger.info(s"ComposeEmailForm: $form")
        logger.info(s"Body is ${form.emailBody}, toAddress is ${form.emailRecipient}, subject is ${form.emailSubject}")
        emailConnector.saveEmail(form)
        Future.successful(Redirect(routes.ComposeEmailController.sentEmailConfirmation()))
      }

      def handleInvalidForm(formWithErrors: Form[ComposeEmailForm]) = {
        logger.warn(s"Error in form: ${formWithErrors.errors}")
        Future.successful(BadRequest(composeEmail(UpscanInitiateResponse(UpscanFileReference(""), "", Map()), formWithErrors)))
      }
      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
    }
  }

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    val body = request.body
    logger.info(
      s"Upload form contains dataParts=${summariseDataParts(body.dataParts)} and fileParts=${summariseFileParts(body.files)}")

    val keyEither: Either[Result, String] = MultipartFormExtractor.extractKey(body)
    val result = if(body.files.isEmpty) {
      noAttachmentEmail(body)
    }
    else {
      attachmentEmail(body, keyEither)
    }
    result
  }

  private def queryFileUploadStatusRecursively(connector: GatekeeperEmailConnector,
                                               key: String, counter: Int = 0)
                                              (implicit hc: HeaderCarrier): Future[UploadInfo] = {
    val sleepDurationInMilliSec = 200
    val retries = 5
    val uploadInfo = connector.fetchFileuploadStatus(key).flatMap(
      uploadInfo => uploadInfo.status match {
       case _ : UploadedSuccessfully =>
          logger.info(s"For key: $key, UploadStatus is UploadedSuccessfully.")
          Future {uploadInfo}
        case _: UploadedFailedWithErrors =>
          logger.info(s"For key: $key, UploadStatus is UploadedFailedWithErrors.")
          Future {uploadInfo}
        case InProgress =>
          logger.info(s"For key: $key, UploadStatus is still InProgress, " +
            s"Recurring $counter time in loop once again after sleeping 2 seconds")
          Thread.sleep(sleepDurationInMilliSec)
          if(counter > retries) {
            Future {uploadInfo}
          } else {
            queryFileUploadStatusRecursively(connector, key, counter + 1)
          }
      }
    )
    uploadInfo
  }

  private def base64Decode(result: String): String =
    new String(Base64.getDecoder.decode(result), Charsets.UTF_8)

  private def noAttachmentEmail(body: MultipartFormData[TemporaryFile])(implicit requestHeader: RequestHeader, request: Request[_]): Future[Result] = {
    val emailForm: ComposeEmailForm = MultipartFormExtractor.extractComposeEmailForm(body)
    val outgoingEmail: Future[OutgoingEmail] = saveEmail(emailForm)
    outgoingEmail.map {  email =>
      Ok(emailPreview(UploadedSuccessfully("", "", "", None, ""), base64Decode(email.htmlEmailBody),
        controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, email.subject))))
    }
  }

  def attachmentEmail(body: MultipartFormData[TemporaryFile], keyEither: Either[Result, String])
                             (implicit requestHeader: RequestHeader, request: Request[_]): Future[Result] = {
    MultipartFormExtractor
      .extractErrorAction(body)
      .fold(
        errorResult => Future.successful(errorResult),
        errorAction => {
          val (fileAdoptionFailures, fileAdoptionSuccesses) = partitionTrys {
            body.files.map { filePart =>
              for {
                adoptedFilePart <- TemporaryFilePart.adoptFile(filePart)
                _ = logger.debug(
                  s"Moved TemporaryFile for Key [${errorAction}] from [${filePart.ref.path}] to [${adoptedFilePart.ref}]")
              } yield adoptedFilePart
            }
          }
          val errorResponse = fetchFileErrorResponse(body, errorAction, fileAdoptionSuccesses, fileAdoptionFailures)

          val res = errorResponse.flatMap { errResp =>
            logger.info("Executing proxyRequest future")
            fetchResultFromErrorResponse(body, errorAction, fileAdoptionSuccesses, fileAdoptionFailures, errResp, keyEither)
          }
          res
        }
      )
  }

  private def fetchFileErrorResponse(body: MultipartFormData[TemporaryFile], errorAction: ErrorAction,
                                     fileAdoptionSuccesses: Seq[MultipartFormData.FilePart[Path]],
                                     fileAdoptionFailures: Seq[String])
                                    (implicit requestHeader: RequestHeader, request: Request[_])
                                    : Future[Option[ErrorResponse]] = {

    val errorResponse = fileAdoptionFailures.headOption.fold {
      val uploadBody =
        Source(dataParts(body.dataParts) ++ fileAdoptionSuccesses.map(TemporaryFilePart.toUploadSource))
      val upscanS3buckerURL = MultipartFormExtractor.extractUpscanUrl(body).get
      proxyRequest(errorAction, uploadBody, upscanS3buckerURL)
    }
    { _ => Future.successful(None) }
    errorResponse
  }

  private def fetchResultFromErrorResponse(body: MultipartFormData[TemporaryFile], errorAction: ErrorAction,
                                     fileAdoptionSuccesses: Seq[MultipartFormData.FilePart[Path]],
                                     fileAdoptionFailures: Seq[String],
                                     errResp: Option[ErrorResponse],
                                           keyEither: Either[Result, String])
                                    (implicit requestHeader: RequestHeader, request: Request[_])
                                    : Future[Result] = {

    fileAdoptionSuccesses.foreach { filePart =>
      TemporaryFilePart
        .deleteFile(filePart)
        .fold(
          err =>
            logger.warn(s"Failed to delete TemporaryFile for Key [${errorAction.key}] " +
              s"at [${filePart.ref}]", err),
          didExist =>
            if (didExist) {
              logger.debug(s"Deleted TemporaryFile for Key [${errorAction.key}] at " +
                s"[${filePart.ref}]")
            }
        )
    }
    val emailForm: ComposeEmailForm = MultipartFormExtractor.extractComposeEmailForm(body)

    if(errResp.isDefined) {
      val outgoingEmail: Future[OutgoingEmail] = saveEmail(emailForm)
      val errorPath = outgoingEmail.map { email =>
        val errorResponse = errResp.get
        Ok(fileChecksPreview(errorResponse.errorMessage, base64Decode(email.htmlEmailBody),
          controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, email.subject))))
      }
      errorPath
    }
    else {
      val uploadInfo = queryFileUploadStatusRecursively(emailConnector, keyEither.getOrElse(""))
      val result = uploadInfo.flatMap { info =>
        val emailFormModified = info.status match {
          case s: UploadedSuccessfully => emailForm.copy(emailBody = emailForm.emailBody + s"\n\n Attachment URL: **[${s.name}](${s.downloadUrl})**" )
          case _ => emailForm
        }
        val outgoingEmail: Future[OutgoingEmail] = saveEmail(emailFormModified)
        outgoingEmail.map { email =>
          Ok(emailPreview(info.status, base64Decode(email.htmlEmailBody),
            controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, email.subject))))
        }
      }
      result
    }
  }



  private def saveEmail(emailForm: ComposeEmailForm)(implicit request: RequestHeader) = {
    emailConnector.saveEmail(emailForm)
  }

  def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList

  def proxyRequest(errorAction: ErrorAction, body: Source[MultipartFormData.Part[Source[ByteString, _]], _],
                   upscanUrl: String): Future[Option[ErrorResponse]] = {
    for {
      response <- wsClient
        .url(upscanUrl)
        .withFollowRedirects(follow = false)
        .post(body)

      _ = logger.debug(
        s"Upload response for Key=[${errorAction.key}] has status=[${response.status}], " +
          s"headers=[${response.headers}], body=[${response.body}]")
    } yield
      response match {
        case r if r.status >= 200 && r.status < 299 =>
          logger.info(s"response status: ${response.status}")
          None
        case r                                      =>
          logger.info(s"response status for non 200 to 400 is : ${response.status} and " +
            s"body: ${response.body} and headers: ${response.headers}")
          proxyErrorResponse(errorAction, r.status, r.body, r.headers)
      }
  }
}
