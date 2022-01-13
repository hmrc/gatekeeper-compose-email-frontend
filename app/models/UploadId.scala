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

import play.api.libs.json.{Format, JsError, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, OFormat, Reads, Writes}
import play.api.mvc.QueryStringBindable

import java.util.UUID

case class ErrorResponse(errorMessage: String, key: String, errorCode: String, errorRequestId: String, errorResource: String)
case class UploadId(value: String) extends AnyVal

sealed trait UploadStatus
case object InProgress extends UploadStatus
case object Failed extends UploadStatus
case class UploadedSuccessfully(name: String, mimeType: String, downloadUrl: String, size: Option[Long], objectStoreUrl: String) extends UploadStatus
case class UploadedFailedWithErrors(errorCode: String, errorMessage: String, errorRequestId: String, key: String) extends UploadStatus

//case class UploadId(value : UUID) extends AnyVal

case class Reference(value: String) extends AnyVal


case class UploadInfo(reference : Reference, status : UploadStatus)
object UploadInfo {
  val status = "status"

  implicit val errorResponse: OFormat[ErrorResponse] = Json.format[ErrorResponse]
  implicit val referenceFormat: OFormat[Reference] =  Json.format[Reference]
  implicit val inProgressFormat: OFormat[InProgress.type] = Json.format[InProgress.type]
  implicit val failedFormat: OFormat[Failed.type] = Json.format[Failed.type]
  implicit val uploadedSuccessfullyFormat: OFormat[UploadedSuccessfully] = Json.format[UploadedSuccessfully]
  implicit val uploadedFailedWithErrorsFormat: OFormat[UploadedFailedWithErrors] = Json.format[UploadedFailedWithErrors]
  implicit val read: Reads[UploadStatus] = new Reads[UploadStatus] {
    override def reads(json: JsValue): JsResult[UploadStatus] = {
      val jsObject = json.asInstanceOf[JsObject]
      jsObject.value.get("_type") match {
        case Some(JsString("InProgress")) => JsSuccess(InProgress)
        case Some(JsString("Failed")) => JsSuccess(Failed)
        case Some(JsString("UploadedSuccessfully")) => Json.fromJson[UploadedSuccessfully](jsObject)(uploadedSuccessfullyFormat)
        case Some(JsString("uploadedFailed")) => Json.fromJson[UploadedFailedWithErrors](jsObject)(uploadedFailedWithErrorsFormat)
        case Some(value) => JsError(s"Unexpected value of _type: $value")
        case None => JsError("Missing _type field")
      }
    }
  }


  val write: Writes[UploadStatus] = new Writes[UploadStatus] {
    override def writes(p: UploadStatus): JsObject = {
      p match {
        case InProgress => JsObject(Map("_type" -> JsString("InProgress")))
        case Failed => JsObject(Map("_type" -> JsString("Failed")))
        case s : UploadedSuccessfully => uploadedSuccessfullyFormat.writes(s) ++ Json.obj("_type" -> "UploadedSuccessfully")
        case f : UploadedFailedWithErrors => uploadedFailedWithErrorsFormat.writes(f) ++ Json.obj("_type" -> "uploadedFailed")
      }
    }
  }
  implicit val uploadStatusFormat: Format[UploadStatus] = Format(read,write)
  implicit val uploadInfoFormat: OFormat[UploadInfo] =  Json.format[UploadInfo]

}

object UploadId {
  def generate = UploadId(UUID.randomUUID().toString)

  implicit def queryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[UploadId] =
    stringBinder.transform(UploadId(_),_.value)
}