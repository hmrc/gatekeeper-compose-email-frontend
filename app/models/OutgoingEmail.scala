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

import models.file_upload.{UploadCargo, UploadedFile}
import play.api.libs.json.{Json, OFormat}

case class OutgoingEmail(emailUUID: String, recipientTitle: String, recipients: List[User], attachmentDetails: Option[Seq[UploadedFile]],
                         markdownEmailBody: String, htmlEmailBody: String, subject: String,
                         status: String, composedBy: String, approvedBy: Option[String], userSelectionQuery: Option[DevelopersEmailQuery])

object OutgoingEmail {
  implicit val userFmt: OFormat[User] = Json.format[User]
  implicit val format: OFormat[UploadCargo] = Json.format[UploadCargo]
  implicit val attachmentDetailsFormat: OFormat[UploadedFile] = Json.format[UploadedFile]
  implicit val emailFmt: OFormat[OutgoingEmail] = Json.format[OutgoingEmail]
}
