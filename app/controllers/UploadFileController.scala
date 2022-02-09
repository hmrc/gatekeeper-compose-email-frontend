package controllers

import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Request}
import config.UploadDocumentsConfig
import config.AppConfig
import connectors.UploadDocumentsConnector
import controllers.JourneyControllerComponents
import models.UploadDocumentsCallback
import models.UploadDocumentsSessionConfig
import models.upscan.{UploadDocumentType, UploadDocumentsSessionConfig}
import views.html.rejectedgoods.upload_files_description

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import config.FileUploadConfig
import models.Nonce
import play.api.Logging
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper

@Singleton
class UploadFilesController @Inject() (
                                        val mcc: MessagesControllerComponents,
                                        uploadDocumentsConnector: UploadDocumentsConnector,
                                        uploadDocumentsConfig: UploadDocumentsConfig,
                                        fileUploadConfig: FileUploadConfig,
                                        upload_files_description: upload_files_description
                                      )(implicit val ec: ExecutionContext, appConfig: AppConfig)
  extends FrontendController(mcc) with Logging {

  final val selfUrl: String = mcc.servicesConfig.getString("self.url")

  final val selectDocumentTypePageAction: Call = routes.ComposeEmailController.email()
  final val callbackAction: Call               = routes.UploadFilesController.submit()

  final def uploadDocumentsSessionConfig(documentType: UploadDocumentType, continueUrl: String)(implicit
                                                                                                              request: Request[_],
                                                                                                              messages: Messages
  ): UploadDocumentsSessionConfig =
    UploadDocumentsSessionConfig(
      continueUrl = continueUrl,
      continueWhenFullUrl = selfUrl + checkYourAnswers.url,
      backlinkUrl = selfUrl + selectDocumentTypePageAction.url,
      callbackUrl = uploadDocumentsConfig.callbackUrlPrefix + callbackAction.url,
      minimumNumberOfFiles = 0, // user can skip uploading the file
      maximumNumberOfFiles = fileUploadConfig.readMaxUploadsValue("supporting-evidence"),
      initialNumberOfEmptyRows = 3,
      maximumFileSizeBytes = fileUploadConfig.readMaxFileSize("supporting-evidence"),
      allowedContentTypes = "application/pdf,image/jpeg,image/png",
      allowedFileExtensions = "*.pdf,*.png,*.jpg,*.jpeg",
      cargo = documentType,
      newFileDescription = documentTypeDescription(documentType),
      content = uploadDocumentsContent(documentType)
    )

  final def uploadDocumentsContent(dt: UploadDocumentType)(implicit
                                                           request: Request[_],
                                                           messages: Messages
  ): UploadDocumentsSessionConfig.Content = {
    val descriptionHtml = upload_files_description(
      "choose-files.rejected-goods",
      documentTypeDescription(dt).toLowerCase(Locale.ENGLISH)
    ).body

    UploadDocumentsSessionConfig.Content(
      serviceName = messages("service.title"),
      title = messages("choose-files.rejected-goods.title"),
      descriptionHtml = descriptionHtml,
      serviceUrl = appConfig.homePageUrl,
      accessibilityStatementUrl = appConfig.accessibilityStatementUrl,
      phaseBanner = "alpha",
      phaseBannerUrl = appConfig.serviceFeedBackUrl,
      signOutUrl = appConfig.signOutUrl,
      timedOutUrl = appConfig.ggTimedOutUrl,
      keepAliveUrl = appConfig.ggKeepAliveUrl,
      timeoutSeconds = appConfig.ggTimeoutSeconds.toInt,
      countdownSeconds = appConfig.ggCountdownSeconds.toInt,
      showLanguageSelection = appConfig.enableLanguageSwitching,
      pageTitleClasses = "govuk-heading-xl",
      allowedFilesTypesHint = messages("choose-files.rejected-goods.allowed-file-types")
    )
  }

  final def documentTypeDescription(dt: UploadDocumentType)(implicit messages: Messages): String =
    messages(s"choose-file-type.file-type.${UploadDocumentType.keyOf(dt)}")

  final val show: Action[AnyContent] =  implicit request =>
    journey.answers.selectedDocumentType match {
      case None =>
        Redirect(selectDocumentTypePageAction).asFuture

      case Some(documentType) =>
        val continueUrl =
          if (journey.answers.checkYourAnswersChangeMode)
            selfUrl + checkYourAnswers.url
          else
            selfUrl + selectDocumentTypePageAction.url

        uploadDocumentsConnector
          .initialize(
            UploadDocumentsConnector
              .Request(
                uploadDocumentsSessionConfig(journey.answers.nonce, documentType, continueUrl),
                journey.answers.supportingEvidences
                  .map(file => file.copy(description = file.documentType.map(documentTypeDescription _)))
              )
          )
          .map {
            case Some(url) =>
              Redirect(s"${uploadDocumentsConfig.publicUrl}$url")
            case None      =>
              Redirect(
                s"${uploadDocumentsConfig.publicUrl}${uploadDocumentsConfig.contextPath}"
              )
          }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  final val submit: Action[AnyContent] =  implicit request =>
      request.asInstanceOf[Request[AnyContent]].body.asJson.map(_.as[UploadDocumentsCallback]) match {
        case None =>
          logger.warn("missing or invalid callback payload")
          BadRequest("missing or invalid callback payload")

        case Some(callback) =>
          logger.warn(s"*****Recieved Callback $callback")

            .receiveUploadedFiles(
              callback.documentType,
              callback.nonce,
              callback.uploadedFiles.map(_.copy(description = None))
            )
            .fold(
              error => (journey, BadRequest(error)),
              modifiedJourney => (modifiedJourney, NoContent)
            )
      }
 }

