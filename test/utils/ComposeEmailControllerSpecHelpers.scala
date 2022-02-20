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

package utils

import akka.stream.scaladsl.Source
import akka.util.ByteString
import common.ControllerBaseSpec
import config.EmailConnectorConfig
import connectors.{AuthConnector, GatekeeperEmailConnector}
import controllers.{ComposeEmailController, ComposeEmailForm, ControllerSetupBase}
import mocks.TestRoles.userRole
import mocks.connector.AuthConnectorMock
import models.file_upload.UploadedFile
import models.{OutgoingEmail, User}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mockito.MockitoSugar.mock
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseStatus}
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, OK}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import services.ComposeEmailService
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, ErrorTemplate, ForbiddenView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import scala.xml.Properties.userName

object ComposeEmailControllerSpecHelpers  extends ControllerBaseSpec with Matchers with GivenWhenThen
  with MockitoSugar {
  implicit val materializer = app.materializer
  lazy val forbiddenView = app.injector.instanceOf[ForbiddenView]
  val errorTemplate: ErrorTemplate = fakeApplication.injector.instanceOf[ErrorTemplate]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm"     -> false,
        "metrics.enabled" -> false
      )
      .build()

  val fakeGetRequest = FakeRequest("GET", "/email").withCSRFToken
  val fakeConfirmationGetRequest = FakeRequest("GET", "/sent-email").withCSRFToken
  val mockEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
  val mockWSClient: WSClient = mock[WSClient]
  lazy val composeEmailTemplateView = app.injector.instanceOf[ComposeEmail]
  lazy val emailPreviewTemplateView = app.injector.instanceOf[EmailPreview]
  lazy val emailSentTemplateView = app.injector.instanceOf[EmailSentConfirmation]
  lazy val httpClient = mock[HttpClient]
  val su = List(User("sawd", "efef", "eff", true))

  class ComposeEmailServiceTest extends ComposeEmailService(mock[GatekeeperEmailConnector]){
    override def saveEmail(composeEmailForm: ComposeEmailForm, emailUID: String,  userInfo: List[User])(implicit hc: HeaderCarrier): Future[OutgoingEmail] =
      Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
        "Hello", su, None,  "*test email body*", "", "", "", None))
    override def fetchEmail(emailUID: String)(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
      Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
        "Hello", su, None,  "*test email body*", "", "", "", None))
    }
    override def updateEmail(composeEmailForm: ComposeEmailForm, emailUID: String, users: List[User],
                             attachmentDetails: Option[Seq[UploadedFile]])(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
      Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
        "Hello", su, None,  "*test email body*", "", "", "", None))
    }
  }
  val mockGateKeeperService = new ComposeEmailServiceTest

  def buildController(mockGateKeeperService: ComposeEmailService, mockAuthConnector: AuthConnector): ComposeEmailController = {
    new ComposeEmailController(
      mcc,
      composeEmailTemplateView,
      emailPreviewTemplateView,
      mockGateKeeperService,
      emailSentTemplateView,
      forbiddenView, mockAuthConnector)
  }

}