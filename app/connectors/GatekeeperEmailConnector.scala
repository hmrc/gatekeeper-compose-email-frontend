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
import controllers.{ComposeEmailForm, EmailPreviewForm}
import models.EmailRequest.{createEmailRequest, updateEmailRequest}
import models.{EmailRequest, OutgoingEmail, User}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpErrorFunctions, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API
import utils.ApplicationLogger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GatekeeperEmailConnector @Inject()(http: HttpClient, config: EmailConnectorConfig)(implicit ec: ExecutionContext)
  extends HttpErrorFunctions
    with ApplicationLogger {

  val api = API("gatekeeper-email")
  lazy val serviceUrl = config.emailBaseUrl

  def saveEmail(composeEmailForm: ComposeEmailForm, emailUUID: String, userInfo: List[User])(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    postSaveEmail(createEmailRequest(composeEmailForm, userInfo), emailUUID)
  }

  def updateEmail(composeEmailForm: ComposeEmailForm, emailUUID: String, users: List[User], attachmentDetails: Option[Seq[String]] = None)
                 (implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    postUpdateEmail(updateEmailRequest(composeEmailForm, users, attachmentDetails), emailUUID)
  }

  def fetchEmail(emailUUID: String)(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    val url = s"$serviceUrl/gatekeeper-email/fetch-email/$emailUUID"
    http.GET[OutgoingEmail](url)
  }

  def sendEmail(emailPreviewForm: EmailPreviewForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    http.POSTEmpty[OutgoingEmail](s"$serviceUrl/gatekeeper-email/send-email/${emailPreviewForm.emailUUID}")
  }

  private def postSaveEmail(request: EmailRequest, emailUUID: String)(implicit hc: HeaderCarrier) = {
    http.POST[EmailRequest, Either[UpstreamErrorResponse, OutgoingEmail]](s"$serviceUrl/gatekeeper-email/save-email?emailUUID=$emailUUID", request)
      .map {
        case resp@Right(_) => resp.right.get
        case Left(err) => throw err
      }
  }

  private def postUpdateEmail(request: EmailRequest, emailUUID: String)(implicit hc: HeaderCarrier) = {
    http.POST[EmailRequest, Either[UpstreamErrorResponse, OutgoingEmail]](s"$serviceUrl/gatekeeper-email/update-email?emailUUID=$emailUUID", request)
      .map {
        case resp@Right(_) => resp.right.get
        case Left(err) => throw err
      }
  }

}