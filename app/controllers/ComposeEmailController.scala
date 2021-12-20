/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.ComposeEmailForm.form
import connectors.GatekeeperEmailConnector
import models.{EmailFailedHttpResponse, EmailSuccessHttpResponse}
import models.JsonFormatters._
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.ComposeEmailService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{ComposeEmail, EmailSentConfirmation, ErrorTemplate}
import utils.ErrorHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ComposeEmailController @Inject()(mcc: MessagesControllerComponents,
                                       composeEmail: ComposeEmail,
                                       emailService: ComposeEmailService,
                                       override val errorTemplate: ErrorTemplate,
                                       sentEmail: EmailSentConfirmation
                                      )(implicit val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with I18nSupport with ErrorHelper with Logging {

  def email: Action[AnyContent] = Action.async { implicit request =>
        Future.successful(Ok(composeEmail(form.fill(ComposeEmailForm("","","")))))

  }

  def sentEmailConfirmation: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(sentEmail()))
  }

  def sendEmail(): Action[AnyContent] = Action.async {
    implicit request => {
      def handleValidForm(form: ComposeEmailForm) = {
        logger.info(s"Body is ${form.emailBody}, toAddress is ${form.emailRecipient}, subject is ${form.emailSubject}")
        emailService.sendEmail(form) map { response =>
          response match {
            case EmailSuccessHttpResponse(code, body) => sentEmailConfirmation()
            case EmailFailedHttpResponse(code, body) => technicalDifficulties
          }
        }
        Future.successful(Redirect(routes.ComposeEmailController.sentEmailConfirmation()))
      }

      def handleInvalidForm(formWithErrors: Form[ComposeEmailForm]) = {
        logger.warn(s"Error in form: ${formWithErrors.errors}")
        Future.successful(BadRequest(composeEmail(formWithErrors)))
      }
      ComposeEmailForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
    }
  }
}
