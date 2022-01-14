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

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import config.AppConfig
import connectors.{GatekeeperEmailConnector, PreparedUpload, UpscanInitiateConnector}
import controllers.UploadProxyController.ErrorResponseHandler.proxyErrorResponse
import controllers.UploadProxyController.MultipartFormExtractor.{extractKey, extractSingletonFormValue}
import controllers.UploadProxyController.TemporaryFilePart.partitionTrys
import controllers.UploadProxyController.{ErrorAction, ErrorResponseHandler, MultipartFormExtractor, TemporaryFilePart, asTuples}
import models.{ErrorResponse, InProgress, OutgoingEmail, Reference, UploadInfo, UploadStatus, UploadedFailedWithErrors, UploadedSuccessfully}
import org.apache.commons.io.Charsets
import play.api.http.Status
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData.DataPart
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents, MultipartFormData, RequestHeader, Result, Results}

import java.nio.file.Files
import scala.xml.Elem
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.libs.Files.{TemporaryFile, logger}
import play.api.libs.json.Json
import services.{UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import util.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, FileSizeMimeChecks}
import org.apache.http.client.utils.URIBuilder
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.Base64
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.runtime.Nothing$
import scala.util.{Failure, Success, Try}
import models.UploadInfo.errorResponse

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
//    val uploadId = UploadId.generate
    val successRedirectUrl = appConfig.uploadRedirectTargetBase + "/gatekeeper-compose-email-frontend/success"
    val errorRedirectUrl   = appConfig.uploadRedirectTargetBase + "/gatekeeper-compose-email-frontend/error"
    for {
      upscanInitiateResponse <- upscanInitiateConnector.initiateV2(None, None) //Some(successRedirectUrl), Some(errorRedirectUrl))
      _ <- httpClient.POSTEmpty[UploadInfo](s"$serviceUrl/gatekeeperemail/insertfileuploadstatus?key=${upscanInitiateResponse.fileReference.reference}")
    } yield Ok(composeEmail(upscanInitiateResponse, controllers.ComposeEmailForm.form.fill(ComposeEmailForm("","",""))))
  }

  def sentEmailConfirmation: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(sentEmail()))
  }

//  def showError(errorCode: String, errorMessage: String, errorRequestId: String, key: String): Action[AnyContent] = Action {
//    implicit request =>
//      Ok(error_template("Upload Error", errorMessage, s"Code: $errorCode, RequestId: $errorRequestId, FileReference: $key"))
//  }

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
    def queryFileUploadStatusRecursively(connector: GatekeeperEmailConnector, key: String, counter: Int = 0): Future[UploadInfo] = {
      val uploadInfo = connector.fetchFileuploadStatus(key).flatMap(
        uploadInfo => uploadInfo.status match {
          case _ : UploadedSuccessfully =>
            logger.info(s"****For key: $key, UploadStatus is UploadedSuccessfully. Sleeping 5 seconds")
            Future {uploadInfo}
          case _: UploadedFailedWithErrors =>
            logger.info(s">>>>>For key: $key, UploadStatus is still UploadedFailedWithErrors. Sleeping 5 seconds")
            Future {uploadInfo}
          case InProgress =>
            logger.info(s"------For key: $key, UploadStatus is still InProgress, Recursing $counter time in loop once again after sleeping 5 seconds")
            Thread.sleep(2000)
            if(counter > 5)
              Future {uploadInfo}
            else
              queryFileUploadStatusRecursively(connector, key, counter+1)
        }
      )
      uploadInfo
    }
    val body = request.body
    logger.info(
      s"Upload form contains dataParts=${summariseDataParts(body.dataParts)} and fileParts=${summariseFileParts(body.files)}")
    def base64Decode(result: String): String =
      new String(Base64.getDecoder.decode(result), Charsets.UTF_8)

    val keyEither: Either[Result, String] = MultipartFormExtractor.extractKey(body)
    val r = if(body.files.isEmpty) {
//      MultipartFormExtractor.extractKey(body).map { key =>
//        Future.successful(
//          ErrorResponseHandler.okResponse(ErrorAction(None, key), "No Error")
//        )
//      }
      val emailForm: ComposeEmailForm = MultipartFormExtractor.extractComposeEmailForm(body)
      val outgoingEmail: Future[OutgoingEmail] = postEmail(emailForm)
      outgoingEmail.map {  email =>
        Ok(emailPreview(UploadedSuccessfully("", "", "", None, ""), base64Decode(email.htmlEmailBody), controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, email.subject))))
      }    }
    else {
      MultipartFormExtractor
        .extractErrorAction(body)
        .fold(
          errorResult => Future.successful(errorResult),
          errorAction => {
            logger.info(s"******>>>> ${errorAction.redirectUrl}")
            val (fileAdoptionFailures, fileAdoptionSuccesses) = partitionTrys {
              body.files.map { filePart =>
                for {
                  adoptedFilePart <- TemporaryFilePart.adoptFile(filePart)
                  _ = logger.debug(
                    s"Moved TemporaryFile for Key [${errorAction.key}] from [${filePart.ref.path}] to [${adoptedFilePart.ref}]")
                } yield adoptedFilePart
              }
            }

            val futResult = fileAdoptionFailures.headOption.fold {
              val uploadBody =
                Source(dataParts(body.dataParts) ++ fileAdoptionSuccesses.map(TemporaryFilePart.toUploadSource))
              val upscanUrl = MultipartFormExtractor.extractUpscanUrl(body).get
              proxyRequest(errorAction, uploadBody, upscanUrl)
            }
            { errorMessage =>
              Future.successful(None)
            }

            val res = futResult.flatMap { fut =>
              logger.info("Executing proxyRequest future")
              fileAdoptionSuccesses.foreach { filePart =>
                TemporaryFilePart
                  .deleteFile(filePart)
                  .fold(
                    err =>
                      logger.warn(s"Failed to delete TemporaryFile for Key [${errorAction.key}] at [${filePart.ref}]", err),
                    didExist =>
                      if (didExist)
                        logger.debug(s"Deleted TemporaryFile for Key [${errorAction.key}] at [${filePart.ref}]")
                  )
              }
              val emailForm: ComposeEmailForm = MultipartFormExtractor.extractComposeEmailForm(body)

              if(fut.isDefined) {
                val outgoingEmail: Future[OutgoingEmail] = postEmail(emailForm)
                val errorPath = outgoingEmail.map { email =>
                  val errorResponse = fut.get
                  Ok(fileChecksPreview(errorResponse.errorMessage, base64Decode(email.htmlEmailBody), controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, email.subject))))
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
                  val outgoingEmail: Future[OutgoingEmail] = postEmail(emailFormModified)
                  outgoingEmail.map { email =>
                      Ok(emailPreview(info.status, base64Decode(email.htmlEmailBody), controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailId, email.subject))))
                  }
                }
                result
              }
            }
            res
          }
        )
    }
    r
  }

  private def postEmail(emailForm: ComposeEmailForm)(implicit request: RequestHeader) = {
//    val emailForm: ComposeEmailForm = MultipartFormExtractor.extractComposeEmailForm(emailForm)
    emailConnector.saveEmail(emailForm)
  }

  private def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList

  private def proxyRequest(errorAction: ErrorAction, body: Source[MultipartFormData.Part[Source[ByteString, _]], _], upscanUrl: String)(
    implicit request: RequestHeader): Future[Option[ErrorResponse]] =

    for {
      response <- wsClient
        .url(upscanUrl)
        .withFollowRedirects(follow = false)
        .post(body)

      _ = logger.debug(
        s"************Upload response for Key=[${errorAction.key}] has status=[${response.status}], " +
          s"headers=[${response.headers}], body=[${response.body}]")
    } yield
      response match {
        case r if r.status >= 200 && r.status < 299 =>
          logger.info(s"****>>>  response status: ${response.status}")
          None
        case r                                      =>
          logger.info(s"****>>>  response status for non 200 to 400 is : ${response.status} and body: ${response.body} and headers: ${response.headers}")
          r.headers.contains("")
          proxyErrorResponse(errorAction, r.status, r.body, r.headers)
      }

  private def toSuccessResult(response: WSResponse): Result =
    Results.Status(response.status)(response.body).withHeaders(asTuples(response.headers): _*)
}

private object UploadProxyController {

  case class ErrorAction(redirectUrl: Option[String], key: String)

  private val KeyName = "key"
  private val UpscanUrl = "upscan-url"
  private val EmailRecipient = "emailRecipient"
  private val EmailSubject = "emailSubject"
  private val EmailBody = "emailBody"

  object TemporaryFilePart {
    private val AdoptedFileSuffix = ".out"

    def partitionTrys[A](trys: Seq[Try[A]]): (Seq[String], Seq[A]) = {
      val failureResults = trys.collect { case Failure(err) => s"${err.getClass.getSimpleName}: ${err.getMessage}" }
      val successResults = trys.collect { case Success(a)   => a }
      (failureResults, successResults)
    }

    /*
     * Play creates a TemporaryFile as the MultipartFormData is parsed.
     * The file is "owned" by Play, is scoped to the request, and is subject to being deleted by a finalizer thread.
     * We "adopt" the file by moving it.
     * This gives us control of its lifetime at the expense of taking responsibility for cleaning it up.  If our
     * cleanup fails, another cleanup will be attempted on shutDown by virtue of the fact that we have not moved
     * the file outside of the playtempXXX folder.
     *
     * See: https://www.playframework.com/documentation/2.7.x/ScalaFileUpload#Uploading-files-in-a-form-using-multipart/form-data
     * See: play.api.libs.Files$DefaultTemporaryFileCreator's FinalizableReferenceQueue & stopHook
     */
    def adoptFile(filePart: FilePart[TemporaryFile]): Try[FilePart[Path]] = {
      val inPath  = filePart.ref.path
      val outPath = inPath.resolveSibling(inPath.getFileName + AdoptedFileSuffix)
      Try(filePart.copy(ref = filePart.ref.atomicMoveWithFallback(outPath)))
    }

    def toUploadSource(filePart: FilePart[Path]): FilePart[Source[ByteString, Future[IOResult]]] =
      filePart.copy(ref = FileIO.fromPath(filePart.ref))

    def deleteFile(filePart: FilePart[Path]): Try[Boolean] =
      Try(Files.deleteIfExists(filePart.ref))
  }

  object MultipartFormExtractor {

    private val missingKey = Results.BadRequest(
      Json.parse("""{"message":"Could not find key field in request"}""")
    )
    private val missingUpscanUrl = Results.BadRequest(
      Json.parse("""{"message":"Could not find upscan_url field in request"}""")
    )
    private val badRedirectUrl = Results.BadRequest(
      Json.parse("""{"message":"Unable to build valid redirect URL for error action"}""")
    )

    def extractErrorAction(multipartFormData: MultipartFormData[TemporaryFile]): Either[Result, ErrorAction] = {
      val maybeErrorActionRedirect = extractErrorActionRedirect(multipartFormData)
      extractKey(multipartFormData).flatMap { key =>
        maybeErrorActionRedirect
          .map { errorActionRedirect =>
            validateErrorActionRedirectUrl(errorActionRedirect, key).map(_ =>
              ErrorAction(maybeErrorActionRedirect, key))
          }
          .getOrElse(Right(ErrorAction(None, key)))
      }
    }

    private def extractErrorActionRedirect(multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
      extractSingletonFormValue("error_action_redirect", multiPartFormData)

    def extractKey(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
      extractSingletonFormValue(KeyName, multiPartFormData).toRight(left = missingKey)

    def extractUpscanUrl(multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
      extractSingletonFormValue(UpscanUrl, multiPartFormData)

    def extractComposeEmailForm(multiPartFormData: MultipartFormData[TemporaryFile]): ComposeEmailForm = {
      val to = extractSingletonFormValue(EmailRecipient, multiPartFormData)
      val subject = extractSingletonFormValue(EmailSubject, multiPartFormData)
      val body = extractSingletonFormValue(EmailBody, multiPartFormData)
      ComposeEmailForm(to.get, subject.get, body.get)
    }
    private def extractSingletonFormValue(
                                           key: String,
                                           multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
      multiPartFormData.dataParts
        .get(key)
        .flatMap(_.headOption)

    private def validateErrorActionRedirectUrl(redirectUrl: String, key: String): Either[Result, URI] =
      Try {
        new URIBuilder(redirectUrl, UTF_8).addParameter("key", key).build()
      }.toOption.toRight(left = badRedirectUrl)
  }

  object ErrorResponseHandler {

    private val MessageField = "Message"

//    def errorResponse(errorAction: ErrorAction, message: String): Result =
//      asErrorResult(errorAction, Status.INTERNAL_SERVER_ERROR, Map(fieldName(MessageField) -> message))
//
//    def okResponse(errorAction: ErrorAction, message: String): Result =
//      asErrorResult(errorAction, Status.OK, Map(fieldName(MessageField) -> message))

    def proxyErrorResponse(
                            errorAction: ErrorAction,
                            statusCode: Int,
                            jsonResponseBody: String,
                            responseHeaders: Map[String, Seq[String]]): Option[ErrorResponse] = {
      logger.info(s"JSON Response bits $jsonResponseBody")
      Some(jsonErrorFields(jsonResponseBody))
      //asErrorResult(errorAction, statusCode, jsonErrorFields(jsonResponseBody), responseHeaders)
    }

//    private def asErrorResult(
//                               errorAction: ErrorAction,
//                               statusCode: Int,
//                               errorFields: ErrorResponse,
//                               responseHeaders: Map[String, Seq[String]] = Map.empty): Result = {
//      val resultFields     = errorFields + (KeyName -> errorAction.key)
//      val exposableHeaders = responseHeaders.filter { case (name, _) => isExposableResponseHeader(name) }
//      logger.info(s"About to redirect to ${errorAction.redirectUrl}")
//      errorAction.redirectUrl
//        .fold(ifEmpty = jsonResult(statusCode, resultFields)) { redirectUrl =>
//          redirectResult(redirectUrl, queryParams = responseHeaders)
//        }
//        .withHeaders(asTuples(exposableHeaders): _*)
//    }

    /*
     * This is a dummy placeholder to draw attention to the fact that filtering of error response headers is
     * required in the real implementation.  We currently retain only CORS-related headers and custom Amazon
     * headers.  The implementation in this stub differs.  This stub has a CORS filter (the real implementation
     * does not), and our UploadController does not add any fake Amazon headers - so we actually have nothing
     * to do here ...
     */
    private def isExposableResponseHeader(name: String): Boolean = false

    private def jsonResult(statusCode: Int, fields: Map[String, String]): Result =
      Results.Status(statusCode)(Json.toJsObject(fields))

    private def redirectResult(url: String, queryParams: Map[String, Seq[String]]): Result = {
      logger.info(s"****>>>> redirectResult with $url")
      val urlBuilder = queryParams.foldLeft(new URIBuilder(url)) { (urlBuilder, param) =>
        urlBuilder.addParameter(param._1, param._2.head)
      }
      logger.info(s">>>>>*****URL for error redirection is ${urlBuilder.build().toASCIIString} and query params are ${queryParams}")
      Redirect(urlBuilder.build().toASCIIString)
    }

    private def jsonErrorFields(jsonBody: String): ErrorResponse = {
      val json = Json.parse(jsonBody)
      json.as[ErrorResponse]
    }


    private def makeOptionalField(elemType: String, xml: Elem): Option[(String, String)] =
      (xml \ elemType).headOption.map(node => fieldName(elemType) -> node.text)

    private def fieldName(elemType: String): String = s"error$elemType"
  }

  def asTuples(values: Map[String, Seq[String]]): Seq[(String, String)] =
    values.toList.flatMap { case (h, v) => v.map((h, _)) }
}
