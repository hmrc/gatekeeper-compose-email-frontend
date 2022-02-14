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

import com.google.common.base.Charsets
import config.AppConfig
import connectors.{GatekeeperEmailConnector, UpscanInitiateConnector}
import models.OutgoingEmail
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.ComposeEmailService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.ComposeEmail

import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailPreviewController @Inject()
(mcc: MessagesControllerComponents,
 composeEmail: ComposeEmail,
 emailService: ComposeEmailService,
 emailConnector: GatekeeperEmailConnector)
(implicit val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with I18nSupport with Logging {

  def sendEmail(emailUID: String): Action[AnyContent] = Action.async {
    implicit request => {
      val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUID)
      fetchEmail.map { email =>
        emailConnector.sendEmail(EmailPreviewForm(emailUID, ComposeEmailForm(email.subject, email.htmlEmailBody)))
        Redirect(routes.ComposeEmailController.sentEmailConfirmation())
      }
    }
  }

  def editEmail(emailUID: String): Action[AnyContent] = Action.async {
    implicit request => {
      val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUID)
      fetchEmail.map { email =>
        Ok(composeEmail(emailUID, controllers.ComposeEmailForm.form.fill(ComposeEmailForm(email.subject, base64Decode(email.markdownEmailBody)))))
      }
    }
  }

  private def base64Decode(result: String): String =
    new String(Base64.getDecoder.decode(result), Charsets.UTF_8)
}