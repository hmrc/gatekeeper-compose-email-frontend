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

import connectors.{GatekeeperEmailConnector, UpscanInitiateConnector}
import models.{InProgress, OutgoingEmail, Reference, UploadInfo}
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import play.filters.csrf.CSRF.TokenProvider
import services.{UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.http.HeaderCarrier
import utils.ComposeEmailControllerSpecHelpers.{mcc, mockGateKeeperService, mockedProxyRequestor}
import views.html.{ComposeEmail}
import play.api.test.CSRFTokenHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class EmailPreviewControllerSpec extends ControllerBaseSpec with Matchers {
  implicit val materializer = app.materializer

  trait Setup extends ControllerSetupBase {
    val csrfToken: (String, String) = "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
    private val mockGatekeeperEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
    private val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]
    private lazy val composeEmailTemplateView = app.injector.instanceOf[ComposeEmail]

    val controller = new EmailPreviewController(
      mcc,
      composeEmailTemplateView,
      mockUpscanInitiateConnector,
      mockGatekeeperEmailConnector
    )
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val outgoingEmail =
      s"""
         |  {
         |    "emailUID": "emailId",
         |    "recipientTitle": "Team-Title",
         |    "recipients": [{"email": "", "firstName": "", "lastName": "", "verified": true}],
         |    "attachmentLink": "",
         |    "markdownEmailBody": "",
         |    "htmlEmailBody": "",
         |    "subject": "emailSubject",
         |    "composedBy": "auto-emailer",
         |    "approvedBy": "auto-emailer"
         |  }
      """.stripMargin

    when(mockGatekeeperEmailConnector.sendEmail(*)(*))
      .thenReturn(successful(Json.parse(outgoingEmail).as[OutgoingEmail]))

    when(mockUpscanInitiateConnector.initiateV2(*, *)(*))
      .thenReturn(successful(UpscanInitiateResponse(UpscanFileReference("reference"), "", Map())))

    when(mockGatekeeperEmailConnector.inProgressUploadStatus(*)(*))
      .thenReturn(successful(UploadInfo(Reference("123423"), InProgress)))


  def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm"     -> false,
        "metrics.enabled" -> false
      )
      .build()
  }

  "POST /email" should {

    "send an email upon receiving a valid form submission" in new Setup {
      val fakeRequest = FakeRequest("POST", "/send-email")
        .withFormUrlEncodedBody("emailUID"->"emailId", "composeEmailForm.emailSubject"->"emailSubject",
          "composeEmailForm.emailBody"->"emailBody")
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
    }

    "send an email upon receiving an invalid form submission" in new Setup {
      val fakeRequest = FakeRequest("POST", "/send-email")
        .withFormUrlEncodedBody("emailUID"->"emailId")
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "POST /edit-email" should {

    "edit email submits valid form to compose email" in new Setup {
      val fakeRequest = FakeRequest("POST", "/edit-email")
        .withFormUrlEncodedBody("emailUID"->"emailId", "composeEmailForm.emailSubject"->"emailSubject",
          "composeEmailForm.emailBody"->"emailBody")
        .withSession(csrfToken, authToken, userToken).withCSRFToken

      val result = controller.editEmail()(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "edit email submits invalid form to compose email" in new Setup {
      val fakeRequest = FakeRequest("POST", "/edit-email")
        .withFormUrlEncodedBody("emailUID"->"emailId")
        .withSession(csrfToken, authToken, userToken).withCSRFToken

      val result = controller.editEmail()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }
}