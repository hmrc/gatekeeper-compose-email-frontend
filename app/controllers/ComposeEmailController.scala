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
import utils.{ErrorAction, GatekeeperAuthWrapper, MultipartFormExtractor}
import views.html._

import java.nio.file.Path
import java.util.{Base64, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.JsonFormatters._
import play.api.libs.json.Json

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

  def email(emailUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    Future.successful(Ok(composeEmail(emailUID, controllers.ComposeEmailForm.form.fill(ComposeEmailForm("","", true)))))
  }

  def processRecipients: Action[AnyContent] =  Action.async { implicit request =>
    val emailRecipients: String = Json.stringify(Json.toJson(request.body.asFormUrlEncoded.map(p => p.get("email-recipients"))))
    val users = List(User("srinivasalu.munagala@digital.hmrc.gov.uk", "Srinivasalu", "munagala", true),
      User("siva.isikella@digital.hmrc.gov.uk", "siva", "isikella", true))
    val emailUID = UUID.randomUUID().toString
     emailService.saveEmail(ComposeEmailForm("", "", true), emailUID, users).map(emailRec =>
      Redirect(routes.ComposeEmailController.email(emailRec.emailUID)))
  }

  def sentEmailConfirmation: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => Future.successful(Ok(sentEmail()))
  }

  def emailPreview(emailUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request =>
      val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUID)
      fetchEmail.map { email =>

        //Redirect(controllers.routes.FileUploadController.start(emailUID, false, true))
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
        Future.successful(BadRequest(composeEmail(emailUID, formWithErrors)))
      }
      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
  }

  private def base64Decode(result: String): String =
    new String(Base64.getDecoder.decode(result), Charsets.UTF_8)

}
