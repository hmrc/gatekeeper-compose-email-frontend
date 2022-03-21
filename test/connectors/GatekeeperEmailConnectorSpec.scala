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

package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, getRequestedFor, post, postRequestedFor, stubFor, urlEqualTo, verify => wireMockVerify}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import common.AsyncHmrcSpec
import config.EmailConnectorConfig
import controllers.{ComposeEmailForm, EmailPreviewForm}
import models.User
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global

class GatekeeperEmailConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterEach with BeforeAndAfterAll with GuiceOneAppPerSuite {

  val stubPort = sys.env.getOrElse("WIREMOCK", "22222").toInt
  val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll() {
    super.beforeAll()
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterEach() {
    wireMockServer.resetMappings()
    super.afterEach()
  }

  override def afterAll() {
    wireMockServer.stop()
    super.afterAll()
  }

  val subject = "Email subject"
  val emailUUID = "email-uuid"
  val emailSendServicePath = s"/gatekeeper-email/send-email/$emailUUID"
  val emailSaveServicePath = s"/gatekeeper-email/save-email?emailUUID=$emailUUID"
  val emailUpdateServicePath = s"/gatekeeper-email/update-email?emailUUID=$emailUUID"
  val fetchEmailUrl = s"/gatekeeper-email/fetch-email/$emailUUID"
  val emailBody = "Body to be used in the email template"

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]

    val fakeEmailConnectorConfig = new EmailConnectorConfig {
      val emailBaseUrl = wireMockUrl
      override val emailSubject: String = subject
    }

    implicit val hc = HeaderCarrier()

    lazy val underTest = new GatekeeperEmailConnector(httpClient, fakeEmailConnectorConfig)
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm(subject, emailBody, true)
    val emailPreviewForm: EmailPreviewForm = EmailPreviewForm(emailUUID, composeEmailForm)
    val users = List(User("example@example.com", "first name", "last name", true),
      User("example2@example2.com", "first name2", "last name2", true))
  }

  trait WorkingHttp {
    self: Setup =>

    val outgoingEmail =
      s"""
         |  {
         |    "emailUUID": "$emailUUID",
         |    "recipientTitle": "Team-Title",
         |    "recipients": [{"email": "", "firstName": "", "lastName": "", "verified": true}],
         |    "attachmentLink": "",
         |    "markdownEmailBody": "",
         |    "htmlEmailBody": "",
         |    "subject": "",
         |    "status": "",
         |    "composedBy": "auto-emailer",
         |    "approvedBy": "auto-emailer"
         |  }
      """.stripMargin

    stubFor(post(urlEqualTo(emailSendServicePath)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(outgoingEmail)
      .withStatus(OK)))
    stubFor(post(urlEqualTo(emailSaveServicePath)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(outgoingEmail)
      .withStatus(OK)))
    stubFor(post(urlEqualTo(emailUpdateServicePath)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(outgoingEmail)
      .withStatus(OK)))
    stubFor(get(urlEqualTo(fetchEmailUrl)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(outgoingEmail)
      .withStatus(OK)))
  }

  trait FailingHttp {
    self: Setup =>
    stubFor(post(urlEqualTo(emailSendServicePath)).willReturn(aResponse().withStatus(NOT_FOUND)))
    stubFor(post(urlEqualTo(emailSaveServicePath)).willReturn(aResponse().withStatus(NOT_FOUND)))
  }

  "emailConnector" should {

    "send gatekeeper email" in new Setup with WorkingHttp {
      await(underTest.sendEmail(emailPreviewForm))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailSendServicePath))
      )
    }

    "fail to send gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.sendEmail(emailPreviewForm))
      }
    }

    "save gatekeeper email" in new Setup with WorkingHttp {
      await(underTest.saveEmail(composeEmailForm, emailUUID, users))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailSaveServicePath))
      )
    }

    "fail to save gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.saveEmail(composeEmailForm, emailUUID, users))
      }
    }

    "update gatekeeper email" in new Setup with WorkingHttp {
      await(underTest.updateEmail(composeEmailForm, emailUUID, users))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailUpdateServicePath))
      )
    }

    "fail to update gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.updateEmail(composeEmailForm, emailUUID, users))
      }
    }

    "fetch email info" in new Setup with WorkingHttp {
      await(underTest.fetchEmail(emailUUID))

      wireMockVerify(1, getRequestedFor(
        urlEqualTo(fetchEmailUrl))
      )
    }

    "fail to fetch  email info " in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.fetchEmail(emailUUID))
      }
    }
  }
}