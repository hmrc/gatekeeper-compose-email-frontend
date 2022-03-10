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

package models.upscan

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import java.time.{Instant, LocalDateTime}
import scala.util.Try

case class FileUpload(
  reference: String,
  credId: Option[String] = None,
  downloadUrl: Option[String] = None,
  fileStatus: Option[FileStatusEnum] = None,
  uploadDetails: Option[UploadDetails] = None,
  failureDetails: Option[FailureDetails] = None,
  lastUpdatedDate: Option[Instant] = None
) {
  val fileName = uploadDetails.map(_.fileName)
}

object FileUpload {

  val callbackReads: Reads[FileUpload] = (
    (JsPath \ "reference").read[String] and
      (JsPath \ "credId").readNullable[String] and
      (JsPath \ "downloadUrl").readNullable[String] and
      (JsPath \ "fileStatus").readNullable[FileStatusEnum] and
      (JsPath \ "uploadDetails").readNullable[UploadDetails] and
      (JsPath \ "failureDetails").readNullable[FailureDetails] and
      (JsPath \ "lastUpdatedDate").readNullable[Instant]
  )(FileUpload.apply _)

  implicit val format: OFormat[FileUpload] = Json.format[FileUpload]
}

case class UploadDetails(uploadTimestamp: LocalDateTime, checksum: String, fileName: String, fileMimeType: String)

object UploadDetails {

  implicit val formats: Format[UploadDetails] = Format(
    ((JsPath \ "uploadTimestamp").read[LocalDateTime] and
      (JsPath \ "checksum").read[String] and
      (JsPath \ "fileName").read[String].map(decodeMimeEncodedWord) and
      (JsPath \ "fileMimeType").read[String])(UploadDetails.apply _),
    ((JsPath \ "uploadTimestamp").write[LocalDateTime] and
      (JsPath \ "checksum").write[String] and
      (JsPath \ "fileName").write[String] and
      (JsPath \ "fileMimeType").write[String])(unlift(UploadDetails.unapply _))
  )

  def decodeMimeEncodedWord(word: String): String =
    Try(MimeUtility.decodeText(word)).getOrElse(word)
}

case class FailureDetails(failureReason: String, message: String)

object FailureDetails {

  implicit val format: OFormat[FailureDetails] = Json.format[FailureDetails]
}
