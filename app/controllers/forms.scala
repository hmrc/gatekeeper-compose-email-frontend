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

import models.User
import play.api.data.Form
import play.api.data.Forms.{boolean, default, mapping, text}

case class ComposeEmailForm(emailSubject: String, emailBody: String, attachFiles: Boolean = false) {}

object ComposeEmailForm {

  val form: Form[ComposeEmailForm] = Form(
    mapping(
      "emailSubject" -> text.verifying("email.subject.required", _.nonEmpty),
      "emailBody" -> text.verifying("email.body.required", _.nonEmpty),
      "attachFiles" -> default(boolean, false)
    )(ComposeEmailForm.apply)(ComposeEmailForm.unapply)
  )
}


case class EmailPreviewForm(emailUID: String, composeEmailForm: ComposeEmailForm) {}

object EmailPreviewForm {

  val form: Form[EmailPreviewForm] = Form(
    mapping(
      "emailUID" -> text.verifying("email.uid.required", _.nonEmpty),
      "composeEmailForm" -> mapping(
        "emailSubject" -> text.verifying("email.subject.required", _.nonEmpty),
        "emailBody" -> text.verifying("email.body.required", _.nonEmpty),
        "attachFiles" -> default(boolean, false)
      )(ComposeEmailForm.apply)(ComposeEmailForm.unapply)
    )(EmailPreviewForm.apply)(EmailPreviewForm.unapply)
  )
}