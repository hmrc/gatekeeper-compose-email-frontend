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
import models.GatekeeperRole
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.Try

@Singleton
class UploadFileController @Inject() (
  mcc: MessagesControllerComponents,
  val fileUploadRepository: FileUploadRepository,
  val sessionRepository: SessionRepository,
  upScanService: UpScanService,
  view: UploadFileView,
  progressView: FileUploadProgressView,
  formProvider: UploadFileFormProvider,
  implicit val appConfig: AppConfig,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc)
    with I18nSupport
    with FileUploadHandler[FileUploadInfo] {

  def onLoad(): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    lazy val backLink =
      (request.userAnswers.get(FileUploadPage), request.userAnswers.get(AnyOtherSupportingDocsPage)) match {
        case (Some(files), _) if files.nonEmpty =>
          Some(controllers.docUpload.routes.UploadAnotherFileController.onLoad())
        case (_, Some(true)) if !request.checkMode =>
          Some(controllers.docUpload.routes.OptionalSupportingDocsController.onLoad())
        case (_, Some(false)) if !request.checkMode =>
          Some(controllers.docUpload.routes.AnyOtherSupportingDocsController.onLoad())
        case _ => None
      }

    val form = request.flash.get("uploadError") match {
      case Some("TooSmall")    => formProvider().withError("file", Messages("uploadFile.error.tooSmall"))
      case Some("TooBig")      => formProvider().withError("file", Messages("uploadFile.error.tooBig"))
      case Some("Unknown")     => formProvider().withError("file", Messages("uploadFile.error.unknown"))
      case Some("Rejected")    => formProvider().withError("file", Messages("uploadFile.error.rejected"))
      case Some("Quarantined") => formProvider().withError("file", Messages("uploadFile.error.quarantined"))
      case _                   => formProvider()
    }

    val anyOtherDocs = request.userAnswers.get(AnyOtherSupportingDocsPage).getOrElse(false)
    val otherDocs    = request.userAnswers.get(OptionalSupportingDocsPage).getOrElse(Seq.empty)
    val docs         = if (anyOtherDocs) otherDocs else Seq.empty

    upScanService.initiateNewJourney().map { response =>
      Ok(view(form, response, backLink, docs))
        .removingFromSession("UpscanReference")
        .addingToSession("UpscanReference" -> response.reference.value)
    }
  }

  def upscanResponseHandler(
    key: Option[String] = None,
    errorCode: Option[String] = None,
    errorMessage: Option[String] = None,
    errorResource: Option[String] = None,
    errorRequestId: Option[String] = None
  ): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val upscanError = buildUpscanError(errorCode, errorMessage, errorResource, errorRequestId)
    val errorRoute  = Redirect(controllers.docUpload.routes.UploadFileController.onLoad())
    val successRoute = Redirect(
      controllers.docUpload.routes.UploadFileController.uploadProgress(key.getOrElse("this will never be used"))
    )

    handleUpscanResponse(key, upscanError, successRoute, errorRoute)
  }

  def uploadProgress(key: String): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      val uploadCompleteRoute = Redirect(controllers.docUpload.routes.UploadAnotherFileController.onLoad())
      val uploadFailedRoute   = Redirect(controllers.docUpload.routes.UploadFileController.onLoad())
      val uploadInProgressRoute = Ok(
        progressView(
          key,
          action = controllers.docUpload.routes.UploadFileController.uploadProgress(key).url
        )
      )
      val updateFilesList: FileUpload => Seq[FileUploadInfo] = { file =>
        val upload = extractFileDetails(file, key)
        request.userAnswers.get(FileUploadPage).getOrElse(Seq.empty) :+ upload
      }
      val saveFilesList: Seq[FileUploadInfo] => Try[UserAnswers] = { list =>
        request.userAnswers.set(FileUploadPage, list)(FileUploadPage.queryWrites)
      }

      handleUpscanFileProcessing(
        key,
        uploadCompleteRoute,
        uploadInProgressRoute,
        uploadFailedRoute,
        updateFilesList,
        saveFilesList
      )
  }

}
