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

import play.api.data.Form
import play.api.data.Forms.{mapping, text}

case class ComposeEmailForm(emailRecipient: String, emailSubject: String, emailBody: String) {}

object ComposeEmailForm {

  val form: Form[ComposeEmailForm] = Form(
    mapping(
      "emailRecipient" -> text.verifying("email.recipient.required", _.nonEmpty),
      "emailSubject" -> text.verifying("email.subject.required", _.nonEmpty),
      "emailBody" -> text.verifying("email.body.required", _.nonEmpty)
    )(ComposeEmailForm.apply)(ComposeEmailForm.unapply)
  )
}


case class EmailPreviewForm(emailId: String, composeEmailForm: ComposeEmailForm) {}

object EmailPreviewForm {

  val form: Form[EmailPreviewForm] = Form(
    mapping(
      "emailId" -> text.verifying("email.id.required", _.nonEmpty),
      "composeEmailForm" -> mapping(
        "emailRecipient" -> text.verifying("email.recipient.required", _.nonEmpty),
        "emailSubject" -> text.verifying("email.subject.required", _.nonEmpty),
        "emailBody" -> text.verifying("email.body.required", _.nonEmpty)
      )(ComposeEmailForm.apply)(ComposeEmailForm.unapply)
    )(EmailPreviewForm.apply)(EmailPreviewForm.unapply)
  )
}