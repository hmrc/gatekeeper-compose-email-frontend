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
import connectors.AuthConnector
import models._
import play.api.Logging
import play.api.data.Form
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import services.ComposeEmailService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper
import views.html._

import java.nio.file.Path
import java.util.{Base64, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.JsonFormatters._
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.util.control.NonFatal

@Singleton
class ComposeEmailController @Inject()(mcc: MessagesControllerComponents,
                                       composeEmail: ComposeEmail,
                                       emailPreview: EmailPreview,
                                       emailService: ComposeEmailService,
                                       sentEmail: EmailSentConfirmation,
                                       override val forbiddenView: ForbiddenView,
                                       override val authConnector: AuthConnector)
                                      (implicit  val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with GatekeeperAuthWrapper with Logging {

  def initialiseEmail: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>

    def persistEmailDetails(users: List[User], userSelection: Map[String, String]): Future[Result] = {
      val emailUID = UUID.randomUUID().toString
      for {
        email <- emailService.saveEmail(ComposeEmailForm("", "", false), emailUID, users)
      } yield Ok(composeEmail(email.emailUID, controllers.ComposeEmailForm.form.fill(ComposeEmailForm("","", false)), userSelection))
    }

    try {
      val body: Option[Map[String, Seq[String]]] = request.body.asInstanceOf[AnyContentAsFormUrlEncoded].asFormUrlEncoded
      body.map(elems => elems.get("email-recipients")).head match {
        case Some(recipients) => try {
          Json.parse(recipients.head).validate[List[User]] match {
            case JsSuccess(value: Seq[User], _) =>
              body.map(elems => elems.get("user-selection")).head match {
                case Some(userSelectedData) => Json.parse(userSelectedData.head).validate[Map[String, String]] match {
                  case JsSuccess(userSelection: Map[String, String], _) => persistEmailDetails(value, userSelection)
                  case JsError(errors) => Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD,
                    s"""Request payload does not contain gatekeeper user selected options: ${errors.mkString(", ")}""")))
                }
              }
            case JsError(errors) => Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD,
              s"""Request payload does not contain gatekeeper users: ${errors.mkString(", ")}""")))
          }
        } catch {
          case NonFatal(e) => {
            logger.error("Email recipients not valid JSON", e)
            Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, s"Request payload does not appear to be JSON: ${e.getMessage}"))
            )
          }
        }
        case None => Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, s"Request payload does not contain any email recipients")))
      }
    } catch {
      case e => {
        logger.error("Error")
        Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Request payload was not a URL encoded form")))

      }
    }
  }

  def sentEmailConfirmation: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => Future.successful(Ok(sentEmail()))
  }

  def emailPreview(emailUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request =>
      val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUID)
      fetchEmail.map { email =>
        Ok(emailPreview(base64Decode(email.htmlEmailBody),
          controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailUID, ComposeEmailForm(email.subject, email.markdownEmailBody, true)))))
      }
  }

  def upload(emailUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request =>
      def handleValidForm(form: ComposeEmailForm) = {
        val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUID)
        val outgoingEmail: Future[OutgoingEmail] = fetchEmail.flatMap(user =>
          emailService.updateEmail(form, emailUID, user.recipients, user.attachmentDetails))
        outgoingEmail.map {  email =>
          if(form.attachFiles) {
            Redirect(controllers.routes.FileUploadController.start(emailUID, false, true))
          }
          else Ok(emailPreview(base64Decode(email.htmlEmailBody), controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailUID, form))))
        }
      }

      def handleInvalidForm(formWithErrors: Form[ComposeEmailForm]) = {
        logger.warn(s"Error in form: ${formWithErrors.errors}")
        Future.successful(BadRequest(composeEmail(emailUID, formWithErrors, Map())))
      }
      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
  }

  private def base64Decode(result: String): String =
    new String(Base64.getDecoder.decode(result), Charsets.UTF_8)

}
