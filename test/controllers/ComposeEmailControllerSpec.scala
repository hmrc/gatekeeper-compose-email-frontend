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

import connectors.GatekeeperEmailConnector
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import play.api.Play.materializer
import play.api.http.Status
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.{ComposeEmail, EmailSentConfirmation, ErrorTemplate, ForbiddenView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ComposeEmailControllerSpec extends ControllerBaseSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup extends ControllerSetupBase {
    lazy val mockGatekeeperEmailConnector = mock[GatekeeperEmailConnector]
    private lazy val forbiddenView = app.injector.instanceOf[ForbiddenView]
    private lazy val emailView = app.injector.instanceOf[ComposeEmail]
    private lazy val emailSentConfirmationView = app.injector.instanceOf[EmailSentConfirmation]
    val csrfToken: (String, String) = "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken

    val notLoggedInRequest = FakeRequest("GET", "/email").withCSRFToken
    val loggedInRequest = FakeRequest("GET", "/email").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakeConfirmationGetRequest = FakeRequest("GET", "/sent-email").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakePostFormRequest = FakeRequest("POST", "/email").withSession(csrfToken, authToken, userToken).withCSRFToken

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockEmailService: ComposeEmailService = mock[ComposeEmailService]
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm("fsadfas%40adfas.com", "dfasd", "asdfasf")
    val errorTemplate: ErrorTemplate = fakeApplication.injector.instanceOf[ErrorTemplate]
    val composeEmail: ComposeEmail = fakeApplication.injector.instanceOf[ComposeEmail]
    val emailSentConfirmation: EmailSentConfirmation = fakeApplication.injector.instanceOf[EmailSentConfirmation]
    val controller = new ComposeEmailController(stubMessagesControllerComponents(),
      composeEmail,
      mockEmailService,
      emailSentConfirmation,
      forbiddenView, mockAuthConnector, errorTemplate)
  }

  "GET /email" should {
    "return 200" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.email()(loggedInRequest)
      status(result) shouldBe Status.OK
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailConnector)
    }

    "redirect to login page for a user that is not authenticated" in new Setup {
      givenFailedLogin()
      val result = controller.email()(notLoggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.SEE_OTHER
      verifyZeroInteractions(mockGatekeeperEmailConnector)
    }

    "deny user with incorrect privileges" in new Setup {
      givenTheGKUserHasInsufficientEnrolments()
      val result = controller.email()(notLoggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.FORBIDDEN
      verifyZeroInteractions(mockGatekeeperEmailConnector)
    }

    "return HTML" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.email()(loggedInRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailConnector)
    }
  }

  "GET /sent-email" should {
    "return 200" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.OK
      verifyZeroInteractions(mockGatekeeperEmailConnector)
    }

    "return HTML" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailConnector)
    }
  }

  "POST /email" should {
    "send an email upon receiving a valid form submission" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      when(mockEmailService.sendEmail(*)(*)).thenReturn(Future.successful(ACCEPTED))
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("emailRecipient" -> "fsadfas%40adfas.com", "emailSubject" -> "dfasd", "emailBody" -> "asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe SEE_OTHER
      verify(mockEmailService).sendEmail(*)(*)
      verifyAuthConnectorCalledForUser
    }

    "handle receiving a failure response from the email service" in new Setup {
        givenTheGKUserIsAuthorisedAndIsANormalUser()
        when(mockEmailService.sendEmail(*)(*)).thenReturn(Future.successful(BAD_REQUEST))
        val fakeRequest = FakeRequest("POST", "/email")
          .withSession(csrfToken, authToken, userToken)
          .withFormUrlEncodedBody("emailRecipient" -> "fsadfas%40adfas.com", "emailSubject" -> "dfasd", "emailBody" -> "asdfasf")
          .withCSRFToken
        val result = controller.sendEmail()(fakeRequest)
        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result).contains("<p class=\"govuk-body\">Sorry, we are experiencing technical difficulties</p>") shouldBe true
        verify(mockEmailService).sendEmail(*)(*)
        verifyAuthConnectorCalledForUser
      }

      "reject a form submission with missing emailRecipient" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("emailSubject" -> "dfasd", "emailBody" -> "asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockEmailService)
    }

    "reject a form submission with missing emailSubject" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("emailRecipient" -> "fsadfas%40adfas.com", "emailBody" -> "asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockEmailService)
    }

    "reject a form submission with missing emailBody" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("emailRecipient" -> "fsadfas%40adfas.com", "emailSubject" -> "dfasd")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockEmailService)
    }
  }
}
