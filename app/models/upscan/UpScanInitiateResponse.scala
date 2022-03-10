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

import play.api.libs.json._

case class UpScanInitiateResponse(reference: Reference, uploadFormTemplate: UploadFormTemplate)

case class Reference(value: String)

case class UploadFormTemplate(href: String, fields: Map[String, String])

object UpScanInitiateResponse {

  implicit val referenceFormat: Format[Reference] = new Format[Reference] {

    override def writes(reference: Reference): JsValue = JsString(reference.value)

    override def reads(json: JsValue): JsResult[Reference] = json.validate[String] match {
      case JsSuccess(reference, _) => JsSuccess(Reference(reference))
      case error: JsError          => error
    }
  }
  implicit val jsonFormatUploadForm: Format[UploadFormTemplate] = Json.format[UploadFormTemplate]

  implicit val jsonReadsForUpScanInitiateResponse: Reads[UpScanInitiateResponse] = new Reads[UpScanInitiateResponse] {
    override def reads(json: JsValue): JsResult[UpScanInitiateResponse] = {
      for {
        reference     <- (json \ "reference").validate[Reference](referenceFormat)
        fieldsAndHref <- (json \ "uploadRequest").validate[UploadFormTemplate](jsonFormatUploadForm)
      } yield UpScanInitiateResponse(reference, fieldsAndHref)
    }
  }
}
