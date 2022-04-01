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

import java.util.UUID

import connectors.GatekeeperEmailConnector
import models.OutgoingEmail
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import play.filters.csrf.CSRF.TokenProvider
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import utils.ComposeEmailControllerSpecHelpers.mcc
import views.html.ComposeEmail

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class EmailPreviewControllerSpec extends ControllerBaseSpec with Matchers {
  implicit val materializer = app.materializer

  trait Setup extends ControllerSetupBase {
    val emailUUID = UUID.randomUUID().toString

    val csrfToken: (String, String) = "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
    private val mockGatekeeperEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
    private val mockComposeEmailService: ComposeEmailService = mock[ComposeEmailService]
    private lazy val composeEmailTemplateView = app.injector.instanceOf[ComposeEmail]

    val controller = new EmailPreviewController(
      mcc,
      composeEmailTemplateView,
      mockComposeEmailService,
      mockGatekeeperEmailConnector
    )
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val outgoingEmail =
      s"""
         |  {
         |    "emailUUID": "emailId",
         |    "recipientTitle": "Team-Title",
         |    "recipients": [{"email": "", "firstName": "", "lastName": "", "verified": true}],
         |    "attachmentLink": "",
         |    "markdownEmailBody": "",
         |    "htmlEmailBody": "",
         |    "subject": "emailSubject",
         |    "status": "IN_PROGRESS",
         |    "composedBy": "auto-emailer",
         |    "approvedBy": "auto-emailer"
         |  }
      """.stripMargin

    when(mockGatekeeperEmailConnector.sendEmail(*)(*))
      .thenReturn(successful(Json.parse(outgoingEmail).as[OutgoingEmail]))

    when(mockGatekeeperEmailConnector.fetchEmail(*)(*))
      .thenReturn(successful(Json.parse(outgoingEmail).as[OutgoingEmail]))

    when(mockComposeEmailService.fetchEmail(*)(*))
      .thenReturn(successful(Json.parse(outgoingEmail).as[OutgoingEmail]))

    when(mockGatekeeperEmailConnector.updateEmail(*, *, *, *)(*))
      .thenReturn(successful(Json.parse(outgoingEmail).as[OutgoingEmail]))

  def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm"     -> false,
        "metrics.enabled" -> false
      )
      .build()
  }

  "POST /send-email" should {

    "send an email upon receiving a valid form submission" in new Setup {
      val fakeRequest = FakeRequest("POST", s"/send-email/$emailUUID")
        .withSession(csrfToken, authToken, userToken).withCSRFToken
      val result = controller.sendEmail(emailUUID)(fakeRequest)
      status(result) shouldBe SEE_OTHER
    }
  }

  "POST /edit-email" should {

    "edit email submits valid form to compose email" in new Setup {
      val fakeRequest = FakeRequest("POST", "/edit-email")
        .withFormUrlEncodedBody("emailUUID"->"emailId", "composeEmailForm.emailSubject"->"emailSubject",
          "composeEmailForm.emailBody"->"emailBody")
        .withSession(csrfToken, authToken, userToken).withCSRFToken

      val result = controller.editEmail(emailUUID, "{}")(fakeRequest)
      status(result) shouldBe OK
    }
  }
}