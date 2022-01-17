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
import connectors.GatekeeperEmailConnector
import models.OutgoingEmail
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class EmailPreviewControllerSpec extends ControllerBaseSpec with Matchers {
  implicit val materializer = app.materializer

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm"     -> false,
        "metrics.enabled" -> false
      )
      .build()

  private val mockGatekeeperEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
  private val controller = new EmailPreviewController(
    mcc,
    mockGatekeeperEmailConnector
  )

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val outgoingEmail =
      s"""
         |  {
         |    "emailId": "emailId",
         |    "recepientTitle": "Team-Title",
         |    "recepients": [""],
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

  }

  "POST /email" should {

    "send an email upon receiving a valid form submission" in new Setup {
      val fakeRequest = FakeRequest("POST", "/send-email")
        .withFormUrlEncodedBody("emailId"->"emailId", "emailSubject"->"emailSubject")
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
    }

    "send an email upon receiving an invalid form submission" in new Setup {
      val fakeRequest = FakeRequest("POST", "/send-email")
        .withFormUrlEncodedBody("emailSubject"->"emailSubject")
      val result = controller.sendEmail()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }
}
