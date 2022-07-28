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
import connectors.{AuthConnector, GatekeeperEmailConnector}
import models.{GatekeeperRole, OutgoingEmail}
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.ComposeEmailService
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper
import views.html.{ComposeEmail, ForbiddenView}

import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailPreviewController @Inject()
(mcc: MessagesControllerComponents,
 composeEmail: ComposeEmail,
 emailService: ComposeEmailService,
 override val forbiddenView: ForbiddenView,
 override val authConnector: AuthConnector,
 emailConnector: GatekeeperEmailConnector)
(implicit val appConfig: AppConfig, val ec: ExecutionContext)
  extends FrontendController(mcc) with GatekeeperAuthWrapper with I18nSupport with Logging with WithDefaultFormBinding {

  def sendEmail(emailUUID: String, userSelection: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER){
    implicit request => {
      val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUUID)
      fetchEmail.map { email =>
        emailConnector.sendEmail(EmailPreviewForm(emailUUID, ComposeEmailForm(email.subject, email.htmlEmailBody, true)))
        //TODO need to fetch email count based on UUID
        Redirect(routes.ComposeEmailController.sentEmailConfirmation(userSelection, 1))
      }
    }
  }

  def editEmail(emailUUID: String, userSelection: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) {
    implicit request => {
      val userSelectionMap: Map[String, String] = Json.parse(userSelection).as[Map[String, String]]
      val fetchEmail: Future[OutgoingEmail] = emailService.fetchEmail(emailUUID)
      fetchEmail.map { email =>
        val txtEmailBody = base64Decode(email.markdownEmailBody)
        Ok(composeEmail(emailUUID, controllers.ComposeEmailForm.form.fill(ComposeEmailForm(email.subject, txtEmailBody, true)), userSelectionMap))
      }
    }
  }

  private def base64Decode(result: String): String =
    new String(Base64.getDecoder.decode(result), Charsets.UTF_8)
}