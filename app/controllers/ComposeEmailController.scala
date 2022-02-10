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
import config.AppConfig
import connectors.{AuthConnector, UpscanInitiateConnector}
import models._
import play.api.Logging
import play.api.data.Form
import play.api.libs.json.JsValue
<<<<<<< HEAD
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import scala.concurrent.{ExecutionContext, Future}
import config.AppConfig
import connectors.AuthConnector
import controllers.ComposeEmailForm.form
import models.GatekeeperRole
import play.api.libs.json.Json
=======
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.DataPart
import play.api.mvc._
>>>>>>> main
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}
import utils.UploadProxyController.TemporaryFilePart
import utils.UploadProxyController.TemporaryFilePart.partitionTrys
import utils.{ErrorAction, GatekeeperAuthWrapper, MultipartFormExtractor, ProxyRequestor}
import views.html._

import java.nio.file.Path
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ComposeEmailController @Inject()(mcc: MessagesControllerComponents,
                                       composeEmail: ComposeEmail,
                                       emailPreview: EmailPreview,
                                       fileChecksPreview: FileSizeMimeChecks,
                                       emailService: ComposeEmailService,
                                       sentEmail: EmailSentConfirmation,
                                       upscanInitiateConnector: UpscanInitiateConnector,
                                       proxyRequestor: ProxyRequestor,
                                       override val forbiddenView: ForbiddenView,
                                       override val authConnector: AuthConnector)
                                      (implicit  val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with GatekeeperAuthWrapper with Logging {

  def email: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    println(s"""Session cookie has for value 'emailRecipients': ${request.session.get("emailRecipients")}""")
    for {
      upscanInitiateResponse <- upscanInitiateConnector.initiateV2(None, None)
      _ <- emailService.inProgressUploadStatus(upscanInitiateResponse.fileReference.reference)
    } yield Ok(composeEmail(upscanInitiateResponse, controllers.ComposeEmailForm.form.fill(ComposeEmailForm("","",""))))
  }

  def sentEmailConfirmation: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => Future.successful(Ok(sentEmail()))
  }

  def upload(): Action[MultipartFormData[TemporaryFile]] = requiresAtLeastForMultiPartFormData(GatekeeperRole.USER) {
    implicit request =>
      def handleValidForm(form: ComposeEmailForm) = {
        val body = request.body
        logger.info(
          s"Upload form contains dataParts=${summariseDataParts(body.dataParts)} and fileParts=${summariseFileParts(body.files)}")

        val keyEither: Either[Result, String] = MultipartFormExtractor.extractKey(body)
        val result = if(body.files.isEmpty) {
          noAttachmentEmail(body, keyEither)
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

  private def noAttachmentEmail(body: MultipartFormData[TemporaryFile],
                                keyEither: Either[Result, String])
                               (implicit requestHeader: RequestHeader, request: Request[_]): Future[Result] = {
    val emailForm: ComposeEmailForm = MultipartFormExtractor.extractComposeEmailForm(body)
    val outgoingEmail: Future[OutgoingEmail] = saveEmail(emailForm, keyEither)
    outgoingEmail.map {  email =>
      Ok(emailPreview(UploadedSuccessfully("", "", "", None, ""), base64Decode(email.htmlEmailBody),
        controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, emailForm))))
    }
  }

  def processRecipients: Action[AnyContent] =  Action.async { implicit request =>
    val emailRecipients: String = Json.stringify(Json.toJson(request.body.asFormUrlEncoded.map(p => p.get("email-recipients"))))
    val emailRecipientsJson: JsValue = Json.toJson(request.body.asFormUrlEncoded.map(p => p.get("email-recipients")))

    Future.successful(Redirect("/api-gatekeeper/compose-email/email").addingToSession("emailRecipients" -> emailRecipients))
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
      val upscanS3bucketURL = MultipartFormExtractor.extractUpscanUrl(body).get
      proxyRequestor.proxyRequest(errorAction, uploadBody, upscanS3bucketURL)
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
      val outgoingEmail: Future[OutgoingEmail] = saveEmail(emailForm, keyEither)
      val errorPath = outgoingEmail.map { email =>
        val errorResponse = errResp.get
        Ok(fileChecksPreview(errorResponse.errorMessage, base64Decode(email.htmlEmailBody),
          controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, emailForm))))
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
        val outgoingEmail: Future[OutgoingEmail] = saveEmail(emailFormModified, keyEither)
        outgoingEmail.map { email =>
          Ok(emailPreview(info.status, base64Decode(email.htmlEmailBody),
            controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, emailForm))))
        }
      }
      result
    }
  }

  private def saveEmail(emailForm: ComposeEmailForm, keyEither: Either[Result, String])(implicit request: RequestHeader) = {
    emailService.saveEmail(emailForm, keyEither)
  }

  def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList
}
