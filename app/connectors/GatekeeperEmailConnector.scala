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

import config.EmailConnectorConfig
import controllers.ComposeEmailForm
import models.{OutgoingEmail, SendEmailRequest}
import models.SendEmailRequest.createEmailRequest
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API
import util.ApplicationLogger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GatekeeperEmailConnector @Inject()(http: HttpClient, config: EmailConnectorConfig)(implicit ec: ExecutionContext)
  extends CommonResponseHandlers
  with ApplicationLogger {

  val api = API("gatekeeper-email")
  lazy val serviceUrl = config.emailBaseUrl

  def sendEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    post(createEmailRequest(composeEmailForm))
  }

  private def post(request: SendEmailRequest)(implicit hc: HeaderCarrier) = {
    http.POST[SendEmailRequest, Either[UpstreamErrorResponse, OutgoingEmail]](s"$serviceUrl/gatekeeper-email", request)
      .map {
        case resp@Right(_) => resp.right.get
        case Left(err) => throw err
      }
  }
}
