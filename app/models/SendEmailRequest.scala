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

package models

import controllers.ComposeEmailForm
import play.api.libs.json.Json

case class EmailData(emailRecipient: String, emailSubject: String, emailBody: String)

case class SendEmailRequest(to: List[String],
                            templateId: String,
                            emailData: EmailData,
                            force: Boolean = false,
                            auditData: Map[String, String] = Map.empty,
                            eventUrl: Option[String] = None)

object SendEmailRequest {
  implicit val emailDataFmt = Json.format[EmailData]
  implicit val sendEmailRequestFmt = Json.format[SendEmailRequest]

  def createEmailRequest(composeEmailForm: ComposeEmailForm) = {

    SendEmailRequest(
      to = List(composeEmailForm.emailRecipient),
      templateId = "gatekeeper",
      EmailData(composeEmailForm.emailRecipient, composeEmailForm.emailSubject, composeEmailForm.emailBody)
    )
  }

}