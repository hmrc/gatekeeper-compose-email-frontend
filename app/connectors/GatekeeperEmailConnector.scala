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

package connectors

import config.EmailConnectorConfig
import controllers.ComposeEmailForm
import models.{EmailFailedHttpResponse, EmailHttpResponse, EmailSuccessHttpResponse, SendEmailRequest}
import models.SendEmailRequest.createEmailRequest
import play.api.libs.json.{JsError, JsObject, JsResult, JsString, JsValue, Json, OFormat, Reads, Writes}
import play.api.mvc._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API
import utils.ApplicationLogger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GatekeeperEmailConnector @Inject()(http: HttpClient, config: EmailConnectorConfig)(implicit ec: ExecutionContext)
  extends CommonResponseHandlers
  with ApplicationLogger {
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
  implicit val httpResponseFormat: OFormat[EmailHttpResponse] = Json.format[EmailHttpResponse]

  val api = API("gatekeeper-email")
  lazy val serviceUrl = config.emailBaseUrl

  def sendEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[Int] = {
    post(createEmailRequest(composeEmailForm))
  }

  private def post(request: SendEmailRequest)(implicit hc: HeaderCarrier) = {
    http.POST[SendEmailRequest, Either[UpstreamErrorResponse, Int]](s"$serviceUrl/gatekeeper-email", request)
    .map(resp => resp match {
      case Right(_) => resp.right.get
      case Left(err) => throw err
    })
  }

}
