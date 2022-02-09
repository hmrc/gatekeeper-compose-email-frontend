package controllers

import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Request}
import config.{AppConfig, FileUploadConfig, UploadDocumentsConfig, ViewConfig}
import connectors.UploadDocumentsConnector
import models.{GatekeeperRole, UploadDocumentsCallback}
import models.upscan.UploadDocumentType.AirWayBill
import models.upscan.{UploadDocumentType, UploadDocumentsSessionConfig, UploadedFile}

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import play.api.Logging
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper

import java.time.ZonedDateTime

@Singleton
class UploadFilesController @Inject() (
                                        val mcc: MessagesControllerComponents,
                                        uploadDocumentsConnector: UploadDocumentsConnector,
                                        uploadDocumentsConfig: UploadDocumentsConfig,
                                        fileUploadConfig: FileUploadConfig
                                      )(implicit val ec: ExecutionContext, appConfig: ViewConfig)
  extends FrontendController(mcc) with GatekeeperAuthWrapper with Logging {

  final val selfUrl: String = "http://localhost:9000/api-gatekeeper/"

  final val selectDocumentTypePageAction: Call = routes.ComposeEmailController.email()
  final val callbackAction: Call               = routes.UploadFilesController.submit()

  final def uploadDocumentsSessionConfig(continueUrl: String)
                                        (implicit
                                                        request: Request[_],
                                                        messages: Messages
  ): UploadDocumentsSessionConfig =
    UploadDocumentsSessionConfig(
      continueUrl = continueUrl,
      continueWhenFullUrl = selfUrl + routes.EmailPreviewController.sendEmail().url,
      backlinkUrl = selfUrl + selectDocumentTypePageAction.url,
      callbackUrl = uploadDocumentsConfig.callbackUrlPrefix + callbackAction.url,
      minimumNumberOfFiles = 0, // user can skip uploading the file
      maximumNumberOfFiles = fileUploadConfig.readMaxUploadsValue("supporting-evidence"),
      initialNumberOfEmptyRows = 3,
      maximumFileSizeBytes = fileUploadConfig.readMaxFileSize("supporting-evidence"),
      allowedContentTypes = "application/pdf,image/jpeg,image/png",
      allowedFileExtensions = "*.pdf,*.png,*.jpg,*.jpeg",
      cargo = AirWayBill,
      newFileDescription = documentTypeDescription(AirWayBill),
      content = uploadDocumentsContent(AirWayBill)
    )

  final def uploadDocumentsContent(dt: UploadDocumentType)(implicit
                                                           request: Request[_],
                                                           messages: Messages
  ): UploadDocumentsSessionConfig.Content = {

    UploadDocumentsSessionConfig.Content(
      serviceName = messages("service.title"),
      title = messages("choose-files.rejected-goods.title"),
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

  final val show: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER)  {
    implicit request =>
      val continueUrl = selfUrl + routes.EmailPreviewController.sendEmail().url

        uploadDocumentsConnector
          .initialize(
            UploadDocumentsConnector
              .Request(
                uploadDocumentsSessionConfig( continueUrl),
                Seq(UploadedFile("", "", ZonedDateTime.now(), "", "", "", None, None, None))
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
  final val submit: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => {
      request.asInstanceOf[Request[AnyContent]].body.asJson.map(_.as[UploadDocumentsCallback]) match {
        case None =>
          logger.warn("missing or invalid callback payload")
          BadRequest("missing or invalid callback payload")

        case Some(callback) =>
          logger.warn(s"*****Recieved Callback $callback")

      }
    }
  }

 }

