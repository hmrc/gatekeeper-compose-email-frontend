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
import connectors.GatekeeperEmailConnector
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailPreviewController @Inject()
(mcc: MessagesControllerComponents,
 emailConnector: GatekeeperEmailConnector)
(implicit val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with I18nSupport with Logging {

  def sendEmail(): Action[AnyContent] = Action.async {
    implicit request => {
      def handleValidForm(form: EmailPreviewForm) = {
        logger.info(s"EmailPreviewForm: $form")
        logger.info(s"Persisted emailId is ${form.emailId}, subject is ${form.emailSubject}")
        emailConnector.sendEmail(form)
        Future.successful(Redirect(routes.ComposeEmailController.sentEmailConfirmation()))
      }

      def handleInvalidForm(formWithErrors: Form[EmailPreviewForm]) = {
        logger.warn(s"Error in form: ${formWithErrors.errors}")
        Future.successful(BadRequest("Error with EmailPreview form"))
      }
      EmailPreviewForm.form.bindFromRequest.fold(handleInvalidForm(_), handleValidForm(_))
    }
  }
}