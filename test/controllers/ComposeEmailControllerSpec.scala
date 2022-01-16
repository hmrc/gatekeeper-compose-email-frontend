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

import common.ControllerBaseSpec
import connectors.{GatekeeperEmailConnector, UpscanInitiateConnector}
import models.{InProgress, Reference, UploadInfo}
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, FileSizeMimeChecks}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class ComposeEmailControllerSpec extends ControllerBaseSpec with Matchers {

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
  private val controller = new ComposeEmailController(
    mcc,
    composeEmailTemplateView,
    emailPreviewTemplateView,
    fileChecksPreview,
    mockEmailConnector,
    mockUpscanInitiateConnector,
    emailSentTemplateView,
    mockWSClient)

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val upscanUploadUrl = "/gatekeeperemail/insertfileuploadstatus?key=fileReference"
    when(mockUpscanInitiateConnector.initiateV2(*, *)(*))
      .thenReturn(successful(UpscanInitiateResponse(UpscanFileReference("fileReference"), "upscanUrl", Map())))

    when(mockEmailConnector.inProgressUploadStatus(*)(*))
      .thenReturn(successful(UploadInfo(Reference("fileReference"), InProgress)))

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
}
