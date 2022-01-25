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

package controllers

import models._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import play.api.Play.materializer
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import utils.ComposeEmailControllerSpecHelpers._
import utils.CreateTempFileFromResource
import utils.Implicits.Base64StringOps
import views.html.{ComposeEmail, EmailSentConfirmation}

import scala.concurrent.Future

class ComposeEmailControllerSpec extends ControllerBaseSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup extends ControllerSetupBase {
    lazy val mockGatekeeperEmailService = mock[ComposeEmailService]
    val csrfToken: (String, String) = "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken

    val notLoggedInRequest = FakeRequest("GET", "/email").withCSRFToken
    val loggedInRequest = FakeRequest("GET", "/email").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakeConfirmationGetRequest = FakeRequest("GET", "/sent-email").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakePostFormRequest = FakeRequest("POST", "/email").withSession(csrfToken, authToken, userToken).withCSRFToken

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val upscanUploadUrl = "/gatekeeperemail/insertfileuploadstatus?key=fileReference"
    val dataParts = Map(
      "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
      "x-amz-credential"        -> Seq("some-credentials"),
      "x-amz-date"              -> Seq("20180517T113023Z"),
      "policy"                  -> Seq("{\"policy\":null}".base64encode()),
      "x-amz-signature"         -> Seq("some-signature"),
      "acl"                     -> Seq("private"),
      "key"                     -> Seq("file-key"),
      "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback"),
      "emailSubject"            -> Seq("Test Email Subject"),
      "emailBody"               -> Seq("*test email body*"),
      "emailRecipient"          -> Seq("srinivasalu.munagala@digital.hmrc.gov.uk"),
      "upscan-url"              -> Seq("http://upscan-s3/")
    )
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm("fsadfas%40adfas.com", "dfasd", "asdfasf")
    val composeEmail: ComposeEmail = fakeApplication.injector.instanceOf[ComposeEmail]
    val emailSentConfirmation: EmailSentConfirmation = fakeApplication.injector.instanceOf[EmailSentConfirmation]
    val controller = buildController(mockGateKeeperService, mockedProxyRequestor, mockAuthConnector)
  }

  "GET /email" should {
    "return 200" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.email()(loggedInRequest)
      status(result) shouldBe Status.OK
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "redirect to login page for a user that is not authenticated" in new Setup {
      givenFailedLogin()
      val result = controller.email()(notLoggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.SEE_OTHER
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "deny user with incorrect privileges" in new Setup {
      givenTheGKUserHasInsufficientEnrolments()
      val result = controller.email()(notLoggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.FORBIDDEN
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "return HTML" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.email()(loggedInRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailService)
    }
  }

  "GET /sent-email" should {
    "return 200" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.OK
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "return HTML" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailService)
    }
  }


  "POST /upload" should {
    "reject a form submission with missing emailRecipient" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts.filter(x => x._1 != "emailRecipient") + ("emailRecipient" -> Seq("")),
        files    = Seq(),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken

      val result = controller.upload()(uploadRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "reject a form submission with missing emailSubject" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts.filter(x => x._1 != "emailSubject") + ("emailSubject" -> Seq("")),
        files    = Seq(),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken

      val result = controller.upload()(uploadRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "reject a form submission with missing emailBody" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts.filter(x => x._1 != "emailBody") + ("emailBody" -> Seq("")),
        files    = Seq(),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken

      val result = controller.upload()(uploadRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "preview a successfully POSTed form and file" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      Given("a valid form containing a valid file")
      val fileToUpload = CreateTempFileFromResource("/text-to-upload.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "/text-to-upload.pdf",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
        files    = Seq(filePart),
        badParts = Nil
      )
      verifyZeroInteractions(mockGatekeeperEmailService)
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }

    "preview a successfully POSTed form and show error for file with virus" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      Given("a valid form containing a valid file with virus")
      class ComposeEmailServiceTestVirusTest extends ComposeEmailServiceTest{
        override def saveEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] =
          Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
            "Hello", List(""), None,  "*test email body*", "", "", "", None))

        override def fetchFileuploadStatus(key: String)(implicit hc: HeaderCarrier): Future[UploadInfo] =
          Future.successful(UploadInfo(Reference("fileReference"),
            UploadedFailedWithErrors("QUARANTINE", "file got virus", "243rwrf", "file-key")))
      }
      val mockGateKeeperService = new ComposeEmailServiceTestVirusTest
      val fileToUpload = CreateTempFileFromResource("/eicar.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "eicar.txt",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      override val controller = buildController(mockGateKeeperService, mockedProxyRequestor, mockAuthConnector)
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
        files    = Seq(filePart),
        badParts = Nil
      )
      verifyZeroInteractions(mockGatekeeperEmailService)
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }

    "preview a successfully POSTed form and show error for file with file size above allowed limit" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      Given("a valid form containing a valid file with virus")
      class ComposeEmailServiceTestInvalidFile extends ComposeEmailServiceTest{
        override def saveEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] =
          Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
            "Hello", List(""), None,  "*test email body*", "", "", "", None))

        override def fetchFileuploadStatus(key: String)(implicit hc: HeaderCarrier): Future[UploadInfo] =
          Future.successful(UploadInfo(Reference("fileReference"),
            InProgress))
      }
      val mockGateKeeperService = new ComposeEmailServiceTestInvalidFile
      val fileToUpload = CreateTempFileFromResource("/screenshot.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "screenshot.txt",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      verifyZeroInteractions(mockGatekeeperEmailService)
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
        files    = Seq(filePart),
        badParts = Nil
      )

      val mockedProxyRequestorWrongSize = new ProxyRequestorTestWrongSize

      override val controller = buildController(mockGateKeeperService, mockedProxyRequestorWrongSize, mockAuthConnector)
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }

    "preview a successfully POSTed form with out a file" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      Given("a valid form containing a valid file")

      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
        files    = Seq(),
        badParts = Nil
      )
      verifyZeroInteractions(mockGatekeeperEmailService)
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }
  }
}
