/*
 * Copyright 2021 HM Revenue & Customs
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
import connectors.GatekeeperEmailConnector
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.mockito.matchers.MacroBasedMatchers
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.Play.materializer
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import play.api.test.CSRFTokenHelper._
import play.test.Helpers.fakeApplication
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.{ComposeEmail, EmailSentConfirmation, ErrorTemplate}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import org.mockito.Mockito._

import scala.concurrent.Future


class ComposeEmailControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    override def fakeApplication(): Application =
      new GuiceApplicationBuilder()
        .configure(
          "metrics.jvm"     -> false,
          "metrics.enabled" -> false
        )
        .build()
    class NewEmailService extends ComposeEmailService(mock[GatekeeperEmailConnector]) {

    override def sendEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[Int] = {
      println("------------>>>>>>>>>> in faked sendEmail")
      Future.successful(202)
    }

    }
    val fakeGetRequest = FakeRequest("GET", "/email").withCSRFToken
    val fakeConfirmationGetRequest = FakeRequest("GET", "/sent-email").withCSRFToken
    val mockEmailService: ComposeEmailService = new NewEmailService
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm("fsadfas%40adfas.com", "dfasd", "asdfasf")
//    when(mockEmailService.sendEmail(*))
//      .thenReturn(successful(OK))
   val errorTemplate: ErrorTemplate = fakeApplication.injector.instanceOf[ErrorTemplate]
   val composeEmail: ComposeEmail = fakeApplication.injector.instanceOf[ComposeEmail]
   val emailSentConfirmation: EmailSentConfirmation = fakeApplication.injector.instanceOf[EmailSentConfirmation]
   val controller = new ComposeEmailController(Helpers.stubMessagesControllerComponents(),
       composeEmail,
       mockEmailService,
       emailSentConfirmation,
       errorTemplate)






  "GET /email" should {
    "return 200" in  {
      val result = controller.email(fakeGetRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in {
      val result = controller.email(fakeGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result)     shouldBe Some("utf-8")
    }
  }

  "GET /sent-email" should {
    "return 200" in {
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in {
      val result = controller.sentEmailConfirmation(fakeConfirmationGetRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result)     shouldBe Some("utf-8")
    }
  }

  "POST /email" should {
    "send an email upon receiving a valid form submission" in {

      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailRecipient"->"fsadfas%40adfas.com", "emailSubject"->"dfasd", "emailBody"->"asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe SEE_OTHER
    }

    "reject a form submission with missing emailRecipient" in {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailSubject"->"dfasd", "emailBody"->"asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "reject a form submission with missing emailSubject" in {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailRecipient"->"fsadfas%40adfas.com", "emailBody"->"asdfasf")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "reject a form submission with missing emailBody" in {
      val fakeRequest = FakeRequest("POST", "/email")
        .withFormUrlEncodedBody("emailRecipient"->"fsadfas%40adfas.com", "emailSubject"->"dfasd")
        .withCSRFToken
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe BAD_REQUEST
    }
  }
}
