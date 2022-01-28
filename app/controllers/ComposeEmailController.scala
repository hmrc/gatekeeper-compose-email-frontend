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

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import scala.concurrent.{ExecutionContext, Future}
import config.AppConfig
import connectors.AuthConnector
import controllers.ComposeEmailForm.form
import models.GatekeeperRole
import play.api.libs.json.Json
import services.ComposeEmailService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import util.GatekeeperAuthWrapper
import utils.ErrorHelper
import views.html.{ComposeEmail, EmailSentConfirmation, ErrorTemplate, ForbiddenView}

@Singleton
class ComposeEmailController @Inject()(mcc: MessagesControllerComponents,
                                       composeEmail: ComposeEmail,
                                       emailService: ComposeEmailService,
                                       sentEmail: EmailSentConfirmation,
                                       override val forbiddenView: ForbiddenView,
                                       override val authConnector: AuthConnector,
                                       override val errorTemplate: ErrorTemplate)
                                      (implicit  val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with ErrorHelper with GatekeeperAuthWrapper with Logging {

  def email: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    println(s"""Session cookie has for value 'emailRecipients': ${request.session.get("emailRecipients")}""")
    Future.successful(Ok(composeEmail(form.fill(ComposeEmailForm("","","")))))
  }

  def sentEmailConfirmation: Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => Future.successful(Ok(sentEmail()))
  }

  def sendEmail(): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => {
      def handleValidForm(form: ComposeEmailForm) = {
        logger.info(s"Body is ${form.emailBody}, toAddress is ${form.emailRecipient}, subject is ${form.emailSubject}")
        emailService.sendEmail(form) map { _ match {
            case ACCEPTED  => Redirect(routes.ComposeEmailController.sentEmailConfirmation())
            case _ => technicalDifficulties
          }
        }
      }

      def handleInvalidForm(formWithErrors: Form[ComposeEmailForm]) = {
        logger.warn(s"Error in form: ${formWithErrors.errors}")
        Future.successful(BadRequest(composeEmail(formWithErrors)))
      }

      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
    }
  }

  def processRecipients: Action[AnyContent] =  Action.async { implicit request =>
    val emailRecipients: String = Json.stringify(Json.toJson(request.body.asFormUrlEncoded.map(p => p.get("email-recipients"))))
    Future.successful(Redirect("/api-gatekeeper/compose-email/email").addingToSession("emailRecipients" -> emailRecipients))
  }
}
