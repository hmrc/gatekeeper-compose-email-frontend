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
import connectors.{AuthConnector, GatekeeperEmailConnector, PreparedUpload, UploadForm, UpscanInitiateConnector, UpscanInitiateRequestV2}
import controllers.{ComposeEmailController, ComposeEmailForm, ControllerSetupBase}
import mocks.TestRoles.userRole
import mocks.connector.AuthConnectorMock
import models.{GatekeeperRole, InProgress, OutgoingEmail, Reference, UploadInfo, UploadedSuccessfully}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mockito.MockitoSugar.mock
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseStatus}
import play.api.mvc.MultipartFormData
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, OK}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import services.ComposeEmailService
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Name, Retrieval, ~}
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import utils.ProxyRequestor
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, ErrorTemplate, FileSizeMimeChecks, ForbiddenView}

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
  val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]
  val mockEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
  val mockWSClient: WSClient = mock[WSClient]
  lazy val composeEmailTemplateView = app.injector.instanceOf[ComposeEmail]
  lazy val emailPreviewTemplateView = app.injector.instanceOf[EmailPreview]
  lazy val emailSentTemplateView = app.injector.instanceOf[EmailSentConfirmation]
  lazy val fileChecksPreview: FileSizeMimeChecks = app.injector.instanceOf[FileSizeMimeChecks]
  lazy val httpClient = mock[HttpClient]

  class ComposeEmailServiceTest extends ComposeEmailService(mock[GatekeeperEmailConnector]){
    override def saveEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] =
      Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
        "Hello", List(""), None,  "*test email body*", "", "", "", None))

    override def fetchFileuploadStatus(key: String)(implicit hc: HeaderCarrier): Future[UploadInfo] =
      Future.successful(UploadInfo(Reference("fileReference"),
        UploadedSuccessfully("text-to-upload.txt", "txt", "http://localhost/text-to-upload.txt", None, "gatekeeper-email/text-to-upload.txt")))

    override def inProgressUploadStatus(keyReference: String)(implicit hc: HeaderCarrier): Future[UploadInfo] =
      Future.successful(UploadInfo(Reference(keyReference), InProgress))
  }
  val mockGateKeeperService = new ComposeEmailServiceTest

  class ProxyRequestorTest extends ProxyRequestor(mockWSClient) {

    override def post(upscanUrl: String, body: Source[MultipartFormData.Part[Source[ByteString, _]], _]) = {
      import play.shaded.ahc.org.asynchttpclient.Response

      val respBuilder = new Response.ResponseBuilder()
      respBuilder.accumulate(new CacheableHttpResponseStatus(Uri.create("http://localhost/gatekeeper-email/email"), OK, "status text", "protocols!"))
      respBuilder.accumulate(new DefaultHttpHeaders())
      respBuilder.accumulate(new CacheableHttpResponseBodyPart("my body".getBytes(), true))
      val resp = new AhcWSResponse(respBuilder.build())
      Future.successful(resp)
    }
  }
  val mockedProxyRequestor = new ProxyRequestorTest

  class UpscanInitiateConnectorTest extends UpscanInitiateConnector(httpClient, appConfig) {
    override def post(url: String, request: UpscanInitiateRequestV2)(implicit hc: uk.gov.hmrc.http.HeaderCarrier,
                                                                     wts: play.api.libs.json.Writes[connectors.UpscanInitiateRequestV2]) = {
      Future.successful(PreparedUpload(connectors.Reference("url"), UploadForm("", Map()) ))
    }
  }
  val mockUpscanInitiateConnectorTest = new UpscanInitiateConnectorTest

//  class AuthMockConnectorChild extends AuthConnector(httpClient, appConfig) {
//    val predicate = Enrolment(appConfig.userRole)
//    val retrieval = Retrievals.name and Retrievals.authorisedEnrolments
//    override def authorise(predicate: Predicate, retrieval: Retrieval[Option[Name] ~ Enrolments])(implicit hc: HeaderCarrier, ec: ExecutionContext)  = {
//      successful(new ~(Some(Name(Some(userName), None)), Enrolments(Set(Enrolment(userRole)))))
//    }
//  }


  def buildController(mockGateKeeperService: ComposeEmailService,mockedProxyRequestor: ProxyRequestor, mockAuthConnector: AuthConnector): ComposeEmailController = {
    new ComposeEmailController(
      mcc,
      composeEmailTemplateView,
      emailPreviewTemplateView,
      fileChecksPreview,
      mockGateKeeperService,
      emailSentTemplateView,
      mockUpscanInitiateConnectorTest,
      mockWSClient,
      httpClient,
      mockedProxyRequestor,
      forbiddenView, mockAuthConnector)
  }
  //val controller = buildController(mockGateKeeperService, mockedProxyRequestor, mockAuthConnector)

  class ProxyRequestorTestWrongSize extends ProxyRequestor(mockWSClient) {

    override def post(upscanUrl: String, body: Source[MultipartFormData.Part[Source[ByteString, _]], _]) = {
      import play.shaded.ahc.org.asynchttpclient.Response

      val respBuilder = new Response.ResponseBuilder()
      respBuilder.accumulate(new CacheableHttpResponseStatus(Uri.create("http://localhost/gatekeeper-email/email"),
        BAD_REQUEST, "status text", "protocols!"))
      respBuilder.accumulate(new DefaultHttpHeaders())
      respBuilder.accumulate(new CacheableHttpResponseBodyPart(("{\"errorMessage\":\"Your proposed upload exceeds the maximum allowed size\"," +
        "\"key\":\"3d376736-7853-4b53-88c3-91c3ef8e3ff5\",\"errorCode\":\"EntityTooLarge\",\"errorRequestId\":\"SomeRequestId\"," +
        "\"errorResource\":\"NoFileReference\"}").getBytes(), true))
      val resp = new AhcWSResponse(respBuilder.build())
      Future.successful(resp)
    }

  }
}