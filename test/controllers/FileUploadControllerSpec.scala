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

import config.AppConfig
import connectors.{AuthConnector, UploadDocumentsConnector}
import models._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import services.ComposeEmailService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.ComposeEmailControllerSpecHelpers.{app, fakeApplication, _}
import views.html.{ComposeEmail, EmailPreview, EmailSentConfirmation, ErrorTemplate, ForbiddenView}

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

class FileUploadControllerSpec extends ControllerBaseSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup extends ControllerSetupBase {
    val su = List(User("sawd", "efef", "eff", true))
    val emailUID = UUID.randomUUID().toString
    lazy val mockGatekeeperEmailService = mock[ComposeEmailService]
    val csrfToken: (String, String) = "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
    implicit val materializer = app.materializer
    lazy val forbiddenView = app.injector.instanceOf[ForbiddenView]
    override val mockAuthConnector = mock[AuthConnector]
    val errorTemplate: ErrorTemplate = fakeApplication.injector.instanceOf[ErrorTemplate]
    lazy val emailPreviewTemplateView = app.injector.instanceOf[EmailPreview]
    val uploadDocumentsConnector = mock[UploadDocumentsConnector]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val loggedInRequest = FakeRequest("POST", "/start-file-upload/:emailUID").withSession(csrfToken, authToken, userToken).withCSRFToken
    val notLoggedInRequest = FakeRequest("POST", "/start-file-upload/:emailUID").withCSRFToken

    val composeEmailForm: ComposeEmailForm = ComposeEmailForm("dfasd", "asdfasf", true)
    val composeEmail: ComposeEmail = fakeApplication.injector.instanceOf[ComposeEmail]
    val emailSentConfirmation: EmailSentConfirmation = fakeApplication.injector.instanceOf[EmailSentConfirmation]

    val controller = new FileUploadController(mcc: MessagesControllerComponents,
                                              forbiddenView,
                                              mockAuthConnector,
                                              uploadDocumentsConnector,
                                              mockGatekeeperEmailService,
                                              composeEmail: ComposeEmail,
                                              emailPreviewTemplateView)

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

    when(mockGatekeeperEmailService.fetchEmail(*)(*))
      .thenReturn(successful(Json.parse(outgoingEmail).as[OutgoingEmail]))

    when(uploadDocumentsConnector.actualPost(*)(*))
      .thenReturn(successful(HttpResponse.apply(OK, "", Map[String, Seq[String]]("Location" -> Seq("/upload-documents")))))

    when(uploadDocumentsConnector.initializeNewFileUpload(*, *, *)(*))
      .thenReturn(successful(Some("/upload-documents")))
  }


  "POST /start-file-upload/:emailUID" should {
    "return 303" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      val result = controller.start(emailUID)(loggedInRequest)
      status(result) shouldBe Status.SEE_OTHER
      verifyAuthConnectorCalledForUser
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "redirect to login page for a user that is not authenticated" in new Setup {
      givenFailedLogin()
      val result = controller.start(emailUID)(notLoggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.SEE_OTHER
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "deny user with incorrect privileges" in new Setup {
      givenTheGKUserHasInsufficientEnrolments()
      val result = controller.start(emailUID)(notLoggedInRequest)
      verifyAuthConnectorCalledForUser
      status(result) shouldBe Status.FORBIDDEN
      verifyZeroInteractions(mockGatekeeperEmailService)
    }

    "return 400 when initialise fails" in new Setup {
      givenTheGKUserIsAuthorisedAndIsANormalUser()
      when(uploadDocumentsConnector.initializeNewFileUpload(*, *, *)(*))
        .thenReturn(successful(None))
      val result = controller.start(emailUID)(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
      verifyZeroInteractions(mockGatekeeperEmailService)
    }
  }

}