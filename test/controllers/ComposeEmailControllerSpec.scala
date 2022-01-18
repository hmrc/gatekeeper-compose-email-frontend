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

import akka.stream.scaladsl.Source
import common.ControllerBaseSpec
import connectors.{GatekeeperEmailConnector, UpscanInitiateConnector}
import models.{InProgress, Reference, UploadInfo, UploadedSuccessfully}
import org.mockito.ArgumentMatchers.anyObject
import org.mockito.MockitoSugar
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.{TemporaryFile, logger}
import play.api.mvc.MultipartFormData.DataPart
import play.api.mvc.{MultipartFormData, RequestHeader}
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.ws.WSClient
import services.{UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, FileSizeMimeChecks}
import test.utils.CreateTempFileFromResource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import test.utils.ImplicitsSpec
import util.ErrorAction
import util.Implicits.Base64StringOps
import util.UploadProxyController.TemporaryFilePart
import util.UploadProxyController.TemporaryFilePart.partitionTrys
class ComposeEmailControllerSpec extends ControllerBaseSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val materializer = app.materializer

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm"     -> false,
        "metrics.enabled" -> false
      )
      .build()

  private val fakeGetRequest = FakeRequest("GET", "/email").withCSRFToken
  private val fakeConfirmationGetRequest = FakeRequest("GET", "/sent-email").withCSRFToken
  private val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]
  private val mockEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
  private val mockWSClient: WSClient = mock[WSClient]
  private lazy val composeEmailTemplateView = app.injector.instanceOf[ComposeEmail]
  private lazy val emailPreviewTemplateView = app.injector.instanceOf[EmailPreview]
  private lazy val emailSentTemplateView = app.injector.instanceOf[EmailSentConfirmation]
  private lazy val fileChecksPreview: FileSizeMimeChecks = app.injector.instanceOf[FileSizeMimeChecks]
  private lazy val httpClient = mock[HttpClient]
  private val controller = new ComposeEmailController(
    mcc,
    composeEmailTemplateView,
    emailPreviewTemplateView,
    fileChecksPreview,
    mockEmailConnector,
    mockUpscanInitiateConnector,
    emailSentTemplateView,
    mockWSClient,
    httpClient)

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val upscanUploadUrl = "/gatekeeperemail/insertfileuploadstatus?key=fileReference"
    when(mockUpscanInitiateConnector.initiateV2(*, *)(*))
      .thenReturn(successful(UpscanInitiateResponse(UpscanFileReference("fileReference"), "upscanUrl", Map())))

    when(mockEmailConnector.inProgressUploadStatus(*)(*))
      .thenReturn(successful(UploadInfo(Reference("fileReference"), InProgress)))

    when(mockEmailConnector.fetchFileuploadStatus(*)(*))
      .thenReturn(successful(UploadInfo(Reference("fileReference"),
        UploadedSuccessfully("text-to-upload.txt", "txt", "http://localhost/text-to-upload.txt", None, "gatekeeper-email/text-to-upload.txt"))))


  }

  "GET /email" should {
//    "return 200" in new Setup {
//      val result = controller.email()(fakeGetRequest)
//      status(result) shouldBe Status.OK
//    }

//    "return HTML" in new Setup {
//      val result = controller.email(fakeGetRequest)
//      contentType(result) shouldBe Some("text/html")
//      charset(result)     shouldBe Some("utf-8")
//    }
  }

  "GET /sent-email" should {
    "return 200" in new Setup {
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      status(result) shouldBe 200
    }

    "return HTML" in new Setup {
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result)     shouldBe Some("utf-8")
    }
  }

  "POST /email" should {
    "send an email upon receiving a valid form submission" in new Setup {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailRecipient"->"fsadfas%40adfas.com", "emailSubject"->"dfasd", "emailBody"->"asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe SEE_OTHER
    }

    "reject a form submission with missing emailRecipient" in new Setup {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailSubject"->"dfasd", "emailBody"->"asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "reject a form submission with missing emailSubject" in new Setup {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailRecipient"->"fsadfas%40adfas.com", "emailBody"->"asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "reject a form submission with missing emailBody" in new Setup {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailRecipient"->"fsadfas%40adfas.com", "emailSubject"->"dfasd")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }
  }

  "POST /upload" should {
    "upload a successfully POSTed form and file" in {
      Given("a valid form containing a valid file")
      val fileToUpload = CreateTempFileFromResource("/text-to-upload.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "text-to-upload.pdf",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts = Map(
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
        ),
        files    = Seq(filePart),
        badParts = Nil
      )
      val (_, fileAdoptionSuccesses) = partitionTrys {
        formDataBody.files.map { filePart =>
          for {
            adoptedFilePart <- TemporaryFilePart.adoptFile(filePart)
            _ = logger.debug(
              s"Moved TemporaryFile for Key file-key from [${filePart.ref.path}] to [${adoptedFilePart.ref}]")
          } yield adoptedFilePart
        }
      }
      val uploadBody =
        Source(formDataBody.dataParts.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList
          ++ fileAdoptionSuccesses.map(TemporaryFilePart.toUploadSource))

      when(controller.proxyRequest(ErrorAction(None, "file-key"), uploadBody, "http://upscan-s3/"))
        .thenReturn(successful(None))

      val uploadRequest = FakeRequest().withBody(formDataBody)
//      val result = controller.upload()(uploadRequest)
      val result = controller.proxyRequest(ErrorAction(None, "file-key"), uploadBody, "http://upscan-s3/")
      (result) shouldBe None
    }



  }

}
