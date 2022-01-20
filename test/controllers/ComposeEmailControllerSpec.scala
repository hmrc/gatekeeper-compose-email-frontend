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
import akka.util.ByteString
import common.ControllerBaseSpec
import config.EmailConnectorConfig
import connectors.GatekeeperEmailConnector
import models.{InProgress, OutgoingEmail, Reference, UploadInfo, UploadedFailedWithErrors, UploadedSuccessfully}
import org.mockito.MockitoSugar
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import play.api.http.Status
import play.api.libs.Files.{TemporaryFile, logger}
import play.api.libs.ws.ahc.AhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseStatus}
import play.api.mvc.MultipartFormData.DataPart
import play.api.mvc.MultipartFormData
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import uk.gov.hmrc.http.HeaderCarrier
import test.utils.CreateTempFileFromResource

import scala.concurrent.ExecutionContext.Implicits.global
import util.ProxyRequestor
import util.Implicits.Base64StringOps
import util.UploadProxyController.TemporaryFilePart
import util.UploadProxyController.TemporaryFilePart.partitionTrys
import test.utils.ComposeEmailControllerSpecHelpers._

import scala.concurrent.Future
class ComposeEmailControllerSpec extends ControllerBaseSpec with Matchers with GivenWhenThen with MockitoSugar {



  trait Setup {
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
  }

  "GET /email" should {
    "return 200" in new Setup {
      val result = controller.email()(fakeGetRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in new Setup {
      val result = controller.email(fakeGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result)     shouldBe Some("utf-8")
    }
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
    "preview a successfully POSTed form and file" in new Setup {
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


      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }

    "preview a successfully POSTed form and show error for file with virus" in new Setup {
      Given("a valid form containing a valid file with virus")
      class GatekeeperEmailConnectorVirusTest extends GatekeeperEmailConnector(httpClient, mock[EmailConnectorConfig]){
        override def saveEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] =
          Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
            "Hello", List(""), None,  "*test email body*", "", "", "", None))

        override def fetchFileuploadStatus(key: String)(implicit hc: HeaderCarrier): Future[UploadInfo] =
          Future.successful(UploadInfo(Reference("fileReference"),
            UploadedFailedWithErrors("QUARANTINE", "file got virus", "243rwrf", "file-key")))
      }
      val mockGateKeeperConnector = new GatekeeperEmailConnectorVirusTest
      val fileToUpload = CreateTempFileFromResource("/eicar.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "eicar.txt",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      val controller = buildController(mockGateKeeperConnector, mockedProxyRequestor)
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
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
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }

    "preview a successfully POSTed form and show error for file with file size above allowed limit" in new Setup {
      Given("a valid form containing a valid file with virus")
      class GatekeeperEmailConnectorInvalidFile extends GatekeeperEmailConnector(httpClient, mock[EmailConnectorConfig]){
        override def saveEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[OutgoingEmail] =
          Future.successful(OutgoingEmail("srinivasalu.munagala@digital.hmrc.gov.uk",
            "Hello", List(""), None,  "*test email body*", "", "", "", None))

        override def fetchFileuploadStatus(key: String)(implicit hc: HeaderCarrier): Future[UploadInfo] =
          Future.successful(UploadInfo(Reference("fileReference"),
            InProgress))
      }
      val mockGateKeeperConnector = new GatekeeperEmailConnectorInvalidFile
      val fileToUpload = CreateTempFileFromResource("/screenshot.png")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "screenshot.png",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )

      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
        files    = Seq(filePart),
        badParts = Nil
      )

      val mockedProxyRequestorWrongSize = new ProxyRequestorTestWrongSize

      val controller = buildController(mockGateKeeperConnector, mockedProxyRequestorWrongSize)
      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }

    "preview a successfully POSTed form with out a file" in new Setup {
      Given("a valid form containing a valid file")

      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts,
        files    = Seq(),
        badParts = Nil
      )

      val uploadRequest = FakeRequest().withBody(formDataBody).withCSRFToken
      val result = controller.upload()(uploadRequest)
      status(result) shouldBe 200
    }
  }

}
