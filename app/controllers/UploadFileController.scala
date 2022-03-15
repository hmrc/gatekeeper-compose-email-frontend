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

import config.AppConfig
import connectors.{AuthConnector, GatekeeperEmailFileUploadConnector}
import forms.UploadFileFormProvider
import models.GatekeeperRole
import models.upscan.{FileUpload, FileUploadInfo}
import play.api.Logging
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import services.UpScanService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.{ApplicationLogger, GatekeeperAuthWrapper}
import views.html.{FileUploadProgressView, ForbiddenView, UploadFileView}

import javax.inject.{Inject, Singleton}
import scala.:+
import scala.concurrent.ExecutionContext
import scala.util.Try

@Singleton
class UploadFileController @Inject() (
  mcc: MessagesControllerComponents,
  upScanService: UpScanService,
  view: UploadFileView,
  progressView: FileUploadProgressView,
  formProvider: UploadFileFormProvider,
  val fileUploadConnector: GatekeeperEmailFileUploadConnector,
  override val forbiddenView: ForbiddenView,
  override val authConnector: AuthConnector,
  implicit val appConfig: AppConfig,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc)
    with I18nSupport with GatekeeperAuthWrapper
    with FileUploadHandler[FileUploadInfo] with ApplicationLogger {

  def onLoad(emailUUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>

    logger.info(s"*****************************************onLoad for emailUUID : $emailUUID")
    val form = request.flash.get("uploadError") match {
      case Some("TooSmall")    => formProvider().withError("file", Messages("uploadFile.error.tooSmall"))
      case Some("TooBig")      => formProvider().withError("file", Messages("uploadFile.error.tooBig"))
      case Some("Unknown")     => formProvider().withError("file", Messages("uploadFile.error.unknown"))
      case Some("Rejected")    => formProvider().withError("file", Messages("uploadFile.error.rejected"))
      case Some("Quarantined") => formProvider().withError("file", Messages("uploadFile.error.quarantined"))
      case _                   => formProvider()
    }

    upScanService.initiateNewJourney().map { response =>
      fileUploadConnector.inProgressUploadStatus(response.reference.value, emailUUID)
      Ok(view(form, response))
        .removingFromSession("UpscanReference")
        .addingToSession("UpscanReference" -> response.reference.value)
    }
  }

  def upscanResponseHandler(
    key: String,
    errorCode: Option[String] = None,
    errorMessage: Option[String] = None,
    errorResource: Option[String] = None,
    errorRequestId: Option[String] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    logger.info("*****************Inside upscanResponseHandler outside fetchFileuploadStatus")
    fileUploadConnector.fetchFileuploadStatus(Some(key).getOrElse("this will never be used")).flatMap { uploadInfo =>
      logger.info("*********************Inside upscanResponseHandler fetchFileuploadStatus")
      val upscanError = buildUpscanError(errorCode, errorMessage, errorResource, errorRequestId)
      val errorRoute = Redirect(controllers.routes.UploadFileController.onLoad(uploadInfo.emailUUID))
      val successRoute = Redirect(
        controllers.routes.UploadFileController.uploadProgress(Some(key).getOrElse("this will never be used"))
      )
      handleUpscanResponse(Some(key), upscanError, successRoute, errorRoute)(ec)
    }
  }

  def uploadProgress(key: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request =>
      fileUploadConnector.fetchFileuploadStatus(key).flatMap { uploadInfo =>
        logger.info(s"*********In uploadProgress for key: $key")
        val uploadCompleteRoute = Redirect(controllers.routes.UploadFileController.onLoad(uploadInfo.emailUUID))
        val uploadFailedRoute = Redirect(controllers.routes.UploadFileController.onLoad(uploadInfo.emailUUID))
        val uploadInProgressRoute = Ok(
          progressView(
            key,
            action = controllers.routes.UploadFileController.uploadProgress(key).url
          )
        )
        //      val updateFilesList: FileUpload => Seq[FileUploadInfo] = { file =>
        //        val upload = extractFileDetails(file, key)
        //        request.userAnswers.get(FileUploadPage).getOrElse(Seq.empty) :+ upload
        //      }
        //      val saveFilesList: Seq[FileUploadInfo] => Try[UserAnswers] = { list =>
        //        request.userAnswers.set(FileUploadPage, list)(FileUploadPage.queryWrites)
        //      }

        handleUpscanFileProcessing(
          key,
          uploadCompleteRoute,
          uploadInProgressRoute,
          uploadFailedRoute
        )
      }
  }

}
