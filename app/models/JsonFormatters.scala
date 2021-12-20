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

package models

import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsValue, Json, OFormat, Reads, Writes}

object JsonFormatters {
  implicit val httpSuccessResponseFormat: OFormat[EmailSuccessHttpResponse] = Json.format[EmailSuccessHttpResponse]
  implicit val httpFailedResponseFormat: OFormat[EmailFailedHttpResponse] = Json.format[EmailFailedHttpResponse]
  implicit val readHttpStatus: Reads[EmailHttpResponse] = new Reads[EmailHttpResponse] {
    override def reads(json: JsValue): JsResult[EmailHttpResponse] = {
      val jsObject = json.asInstanceOf[JsObject]
      jsObject.value.get("_type") match {
        case Some(JsString("EmailSuccessHttpResponse")) => Json.fromJson[EmailSuccessHttpResponse](jsObject)(httpSuccessResponseFormat)
        case Some(JsString("EmailFailedHttpResponse")) => Json.fromJson[EmailFailedHttpResponse](jsObject)(httpFailedResponseFormat)
        case Some(value) => JsError(s"Unexpected value of _type: $value")
        case None => JsError("Missing _type field")
      }
    }
  }
  val writeHttpStatus: Writes[EmailHttpResponse] = new Writes[EmailHttpResponse] {
    override def writes(p: EmailHttpResponse): JsObject = {
      p match {
        case s : EmailSuccessHttpResponse => httpSuccessResponseFormat.writes(s) ++ Json.obj("_type" -> "EmailSuccessHttpResponse")
        case f : EmailFailedHttpResponse =>  httpFailedResponseFormat.writes(f) ++ Json.obj("_type" -> "EmailFailedHttpResponse")
      }
    }
  }
  //case f : UploadedFailedWithErrors => uploadedFailed.writes(f) ++ Json.obj("_type" -> "uploadedFailed")
  implicit val httpResponseFormat: Format[EmailHttpResponse] = Format(readHttpStatus,writeHttpStatus)
}
