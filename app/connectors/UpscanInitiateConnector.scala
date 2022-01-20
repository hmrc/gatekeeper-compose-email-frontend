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

package connectors

import javax.inject.Inject
import play.api.libs.json.{Json, OFormat, Reads, Writes}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import PreparedUpload._
import config.AppConfig
import services.{UpscanFileReference, UpscanInitiateResponse}

import scala.concurrent.{ExecutionContext, Future}

sealed trait UpscanInitiateRequest

// TODO expectedContentType is also an optional value
case class UpscanInitiateRequestV1(
  callbackUrl: String,
  successRedirect: Option[String] = None,
  minimumFileSize: Option[Int]    = None,
  maximumFileSize: Option[Int]    = Some(512))
    extends UpscanInitiateRequest

// TODO expectedContentType is also an optional value
case class UpscanInitiateRequestV2(
  callbackUrl: String,
  successRedirect: Option[String] = None,
  errorRedirect: Option[String]   = None,
  minimumFileSize: Option[Int]    = None,
  maximumFileSize: Option[Int]    = Some(512))
    extends UpscanInitiateRequest

case class UploadForm(href: String, fields: Map[String, String])

case class Reference(value: String) extends AnyVal

object Reference {
  implicit val referenceReader: Reads[Reference] = Reads.StringReads.map(Reference(_))
}

case class PreparedUpload(reference: Reference, uploadRequest: UploadForm)

object UpscanInitiateRequestV1 {
  implicit val format: OFormat[UpscanInitiateRequestV1] = Json.format[UpscanInitiateRequestV1]
}

object UpscanInitiateRequestV2 {
  implicit val format: OFormat[UpscanInitiateRequestV2] = Json.format[UpscanInitiateRequestV2]
}

object PreparedUpload {

  implicit val uploadFormFormat: Reads[UploadForm] = Json.reads[UploadForm]

  implicit val format: Reads[PreparedUpload] = Json.reads[PreparedUpload]
}

class UpscanInitiateConnector @Inject()(httpClient: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  private val headers = Map(
    HeaderNames.CONTENT_TYPE -> "application/json"
  )

  def initiateV2(redirectOnSuccess: Option[String], redirectOnError: Option[String])
                (implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] = {
    val request = UpscanInitiateRequestV2(
      callbackUrl = appConfig.callbackEndpointTarget,
      successRedirect = redirectOnSuccess,
      errorRedirect = redirectOnError
    )
    initiate(appConfig.initiateV2Url, request)
  }

  private def initiate(url: String, request: UpscanInitiateRequestV2)(
    implicit hc: HeaderCarrier,
    wts: Writes[UpscanInitiateRequestV2]): Future[UpscanInitiateResponse] = {
    for {
        response <- post(url, request)
        fileReference = UpscanFileReference(response.reference.value)
        postTarget    = response.uploadRequest.href
        formFields    = response.uploadRequest.fields
      } yield UpscanInitiateResponse(fileReference, postTarget, formFields)
  }

  def post(url: String, request: UpscanInitiateRequestV2)(
    implicit hc: HeaderCarrier,
    wts: Writes[UpscanInitiateRequestV2]): Future[PreparedUpload] = {
    httpClient.POST[UpscanInitiateRequestV2, PreparedUpload](url, request, headers.toSeq)
  }

}
