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
import com.google.common.base.Charsets

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, AnyContentAsMultipartFormData, MessagesControllerComponents, MultipartFormData, Request, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}
import config.AppConfig
import connectors.{AuthConnector, UpscanInitiateConnector}
import controllers.ComposeEmailForm.form
import models.{ErrorResponse, GatekeeperRole, InProgress, OutgoingEmail, UploadInfo, UploadedFailedWithErrors, UploadedSuccessfully}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData.DataPart
import services.{ComposeEmailService, UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}
import utils.UploadProxyController.TemporaryFilePart
import utils.UploadProxyController.TemporaryFilePart.partitionTrys
import utils.GatekeeperAuthWrapper
import utils.{ErrorAction, ErrorHelper, MultipartFormExtractor, ProxyRequestor}
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, ErrorTemplate, FileSizeMimeChecks, ForbiddenView}

import java.nio.file.Path
import java.util.Base64

@Singleton
class ComposeEmailController @Inject()(mcc: MessagesControllerComponents,
                                       composeEmail: ComposeEmail,
                                       emailPreview: EmailPreview,
                                       fileChecksPreview: FileSizeMimeChecks,
                                       emailService: ComposeEmailService,
                                       sentEmail: EmailSentConfirmation,
                                       upscanInitiateConnector: UpscanInitiateConnector,
                                       wsClient: WSClient,
                                       httpClient: HttpClient,
                                       proxyRequestor: ProxyRequestor,
                                       override val forbiddenView: ForbiddenView,
                                       override val authConnector: AuthConnector,
                                       override val errorTemplate: ErrorTemplate)
                                      (implicit  val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with ErrorHelper with GatekeeperAuthWrapper with Logging {

  def email: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    for {
      upscanInitiateResponse <- upscanInitiateConnector.initiateV2(None, None)
      _ <- emailService.inProgressUploadStatus(upscanInitiateResponse.fileReference.reference)
    } yield Ok(composeEmail(upscanInitiateResponse, controllers.ComposeEmailForm.form.fill(ComposeEmailForm("","",""))))
  }

  def sentEmailConfirmation: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => Future.successful(Ok(sentEmail()))
  }

//  def sendEmail(): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
//    implicit request => {
//      def handleValidForm(form: ComposeEmailForm) = {
//        logger.info(s"ComposeEmailForm: $form")
//        logger.info(s"Body is ${form.emailBody}, toAddress is ${form.emailRecipient}, subject is ${form.emailSubject}")
//        emailService.saveEmail(form)
//        upload()
//      }
//
//      def handleInvalidForm(formWithErrors: Form[ComposeEmailForm]) = {
//        logger.warn(s"Error in form: ${formWithErrors.errors}")
//        Future.successful(BadRequest(composeEmail(UpscanInitiateResponse(UpscanFileReference(""), "", Map()), formWithErrors)))
//      }
//      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
//    }
//  }

  def upload(): Action[MultipartFormData[TemporaryFile]] = requiresAtLeast3(GatekeeperRole.USER) {
    implicit request =>
      val form = MultipartFormExtractor.extractComposeEmailForm(request.body)
      def handleValidForm(form: ComposeEmailForm) = {
        logger.info(s"ComposeEmailForm: $form")
        logger.info(s"Body is ${form.emailBody}, toAddress is ${form.emailRecipient}, subject is ${form.emailSubject}")
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

      def handleInvalidForm(formWithErrors: Form[ComposeEmailForm]) = {
        logger.warn(s"Error in form: ${formWithErrors.errors}")
        val upscanInitiateResponse = MultipartFormExtractor.extractUpscanInitiateResponse(request.body)
        Future.successful(BadRequest(composeEmail(upscanInitiateResponse, formWithErrors)))
      }
      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
  }

  private def queryFileUploadStatusRecursively(emailService: ComposeEmailService,
                                               key: String, counter: Int = 0)
                                              (implicit hc: HeaderCarrier): Future[UploadInfo] = {
    val sleepDurationInMilliSec = 200
    val retries = 5
    val uploadInfo = emailService.fetchFileuploadStatus(key).flatMap(
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
            queryFileUploadStatusRecursively(emailService, key, counter + 1)
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
      val uploadBody = Source(dataParts(body.dataParts) ++ fileAdoptionSuccesses.map(TemporaryFilePart.toUploadSource))
      val upscanS3buckerURL = MultipartFormExtractor.extractUpscanUrl(body).get
      proxyRequestor.proxyRequest(errorAction, uploadBody, upscanS3buckerURL)
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
            logger.info(s"Failed to delete TemporaryFile for Key [${errorAction.key}] " +
              s"at [${filePart.ref}]", err),
          didExist =>
            if (didExist) {
              logger.info(s"Deleted TemporaryFile for Key [${errorAction.key}] at " +
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
      val uploadInfo = queryFileUploadStatusRecursively(emailService, keyEither.getOrElse(""))
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
    emailService.saveEmail(emailForm)
  }

  def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList
}
