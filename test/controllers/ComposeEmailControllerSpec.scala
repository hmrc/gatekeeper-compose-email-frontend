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

import models._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import play.api.http.Status
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import utils.ComposeEmailControllerSpecHelpers._
import views.html.{ComposeEmail, EmailSentConfirmation}

class ComposeEmailControllerSpec extends ControllerBaseSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup extends ControllerSetupBase {
    val su = List(RegisteredUser("sawd", "efef", "eff", true))
    val emailUUID = UUID.randomUUID().toString
    lazy val mockGatekeeperEmailService = mock[ComposeEmailService]
    val csrfToken: (String, String) = "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
    implicit val materializer = app.materializer

    val notLoggedInRequest = FakeRequest("GET", "/email").withCSRFToken
    val loggedInRequest = FakeRequest("GET", "/email").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakeConfirmationGetRequest = FakeRequest("GET", "/sent-email").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakeConfirmationEmailPreviewRequest = FakeRequest("GET", "/emailpreview").withSession(csrfToken, authToken, userToken).withCSRFToken
    val fakePostFormRequest = FakeRequest("POST", "/email").withSession(csrfToken, authToken, userToken).withCSRFToken

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val composeEmailForm: ComposeEmailForm = ComposeEmailForm("dfasd", "asdfasf", true)
    val composeEmail: ComposeEmail = fakeApplication.injector.instanceOf[ComposeEmail]
    val emailSentConfirmation: EmailSentConfirmation = fakeApplication.injector.instanceOf[EmailSentConfirmation]
    val controller = buildController(mockGateKeeperService, mockAuthConnector)
  }

  "POST /email" should {
    "unmarshal the request body when it contains an array of User with one element" in new Setup {
      val composeEmailRecipients =
        """[{"email":"neil.frow@digital.hmrc.gov.uk","userId":"d8efe602-3ba4-434e-a547-07bba424797f","firstName":"Neil","lastName":"Frow","verified":true,"mfaEnabled":false}]"""
      val userSelectionData =
        """{"API":"Agent Authorisation","Topic":"Business and policy"}""".stripMargin
      val selectionQuery = """{"topic":"topic-dev", "privateapimatch": false, "apiVersionFilter": "apiVersionFilter", "allUsers": false}""".stripMargin

      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("email-recipients" -> composeEmailRecipients, "user-selection" -> userSelectionData, "user-selection-query" -> selectionQuery)
        .withCSRFToken
      val result = controller.initialiseEmail()(fakeRequest)
      status(result) shouldBe OK
    }

    "unmarshal the request body when it contains an array of User with two elements" in new Setup {
      val composeEmailRecipients =
        """[{"email":"neil.frow@digital.hmrc.gov.uk","userId":"d8efe602-3ba4-434e-a547-07bba424797f",
          |"firstName":"Neil","lastName":"Frow","verified":true,"mfaEnabled":false},
          |{"email":"neil.frow@digital.hmrc.gov.uk","userId":"d8efe602-3ba4-434e-a547-07bba424797f",
          |"firstName":"Neil","lastName":"Frow","verified":true,"mfaEnabled":false}]""".stripMargin
      val userSelectionData =
        """{"API":"Agent Authorisation","Topic":"Business and policy"}""".stripMargin
      val selectionQuery = """{"topic":"topic-dev", "privateapimatch": false, "apiVersionFilter": "apiVersionFilter", "allUsers": false}""".stripMargin
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("email-recipients" -> composeEmailRecipients, "user-selection" -> userSelectionData, "user-selection-query" -> selectionQuery )
        .withCSRFToken
      val result = controller.initialiseEmail()(fakeRequest)
      status(result) shouldBe OK
      contentAsString(result).contains("Compose email") shouldBe true
    }

    "handle a form which contains the selection query but its value is not valid JSON" in new Setup {
      val userSelectionData =
        """{"API":"Agent Authorisation","Topic":"Business and policy"}""".stripMargin
      val selectionQuery = """{topic:topic-dev, "apiVersionFilter": "apiVersionFilter"}""".stripMargin
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("user-selection" -> userSelectionData, "user-selection-query" -> selectionQuery)
        .withCSRFToken
      val result = controller.initialiseEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
      (contentAsJson(result) \ "message").as[String] should startWith("Request payload does not appear to be JSON")
    }

    "handle a form which contains selection query which contains valid JSON but which does not have mandatory attributes" in new Setup {
      val userSelectionData =
        """{"API":"Agent Authorisation","Topic":"Business and policy"}""".stripMargin
      val selectionQuery = """{"topic":"topic-dev", "apiVersionFilter": "apiVersionFilter"}""".stripMargin
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("user-selection" -> userSelectionData, "user-selection-query" -> selectionQuery)
        .withCSRFToken
      val result = controller.initialiseEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
      (contentAsJson(result) \ "message").as[String] should startWith("Request payload does not contain gatekeeper user selected query data")
    }

    "handle a request payload which doesn't contain the expected user selected options" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withFormUrlEncodedBody("dummy" -> "value")
        .withCSRFToken
      val result = controller.initialiseEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
      (contentAsJson(result) \ "message").as[String] should startWith("Request payload does not contain gatekeeper user selected options")
    }

    "handle a request payload which doesn't contain a form" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val fakeRequest = FakeRequest("POST", "/email")
        .withSession(csrfToken, authToken, userToken)
        .withCSRFToken
      val result = controller.initialiseEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_REQUEST_PAYLOAD"
      (contentAsJson(result) \ "message").as[String] shouldBe "Request payload was not a URL encoded form"
    }
  }

  "GET /sent-email" should {
    "return 200" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.sentEmailConfirmation("{}", 1)(fakeConfirmationGetRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.OK
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "return HTML" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.sentEmailConfirmation("{}", 1)(fakeConfirmationGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailService)
    }
  }

  "GET /emailpreview/emailUUID" should {
    "return 200" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.emailPreview(emailUUID, "{}")(loggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe OK
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "return HTML" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.emailPreview(emailUUID, "{}")(loggedInRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailService)
    }
  }


  "POST /upload" should {

    "reject a form submission with missing emailSubject" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val uploadRequest = FakeRequest().withBody(composeEmailForm).withCSRFToken

      val result = controller.upload(emailUUID, "{}")(uploadRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "reject a form submission with missing emailBody" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()

      val uploadRequest = FakeRequest().withBody(composeEmailForm).withCSRFToken

      val result = controller.upload(emailUUID, "{}")(uploadRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

  }

  "POST /delete" should {

    "reject a form submission with missing radio button yesNo selection" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val uploadRequest = FakeRequest().withCSRFToken

      val result = controller.delete(emailUUID, "{}")(uploadRequest)
      status(result) shouldBe BAD_REQUEST
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "accept a form submission with Yes selected" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()

      val uploadRequest = FakeRequest().withFormUrlEncodedBody("value" -> "true").withCSRFToken

      val result = controller.delete(emailUUID, "{}")(uploadRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "accept a form submission with No selected" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()

      val uploadRequest = FakeRequest().withFormUrlEncodedBody("value" -> "false").withCSRFToken

      val result = controller.delete(emailUUID, "{}")(uploadRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

  }

  "POST /deleteOption" should {

    "a form submission on click on deleteEmail button" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val uploadRequest = FakeRequest().withCSRFToken

      val result = controller.deleteOption(emailUUID, "{}")(uploadRequest)
      status(result) shouldBe OK
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

  }
}
