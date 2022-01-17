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
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, postRequestedFor, stubFor, urlEqualTo, verify => wireMockVerify}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import common.AsyncHmrcSpec
import config.EmailConnectorConfig
import controllers.{ComposeEmailForm, EmailPreviewForm}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
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

  val gatekeeperLink = "http://some.url"
  val emailAddress = "email@example.com"
  val subject = "Email subject"
  val emailId = "email-uuid"
  val emailServicePath = s"/gatekeeper-email/send-email/$emailId"
  val emailBody = "Body to be used in the email template"

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]

    val fakeEmailConnectorConfig = new EmailConnectorConfig {
      val emailBaseUrl = wireMockUrl
      override val emailSubject: String = subject
    }

    implicit val hc = HeaderCarrier()

    lazy val underTest = new GatekeeperEmailConnector(httpClient, fakeEmailConnectorConfig)
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm(emailAddress, subject, emailBody)
    val emailPreviewForm: EmailPreviewForm = EmailPreviewForm(emailId, subject)
  }

  trait WorkingHttp {
      self: Setup =>

    val outgoingEmail =
      s"""
         |  {
         |    "emailId": "$emailId",
         |    "recepientTitle": "Team-Title",
         |    "recepients": [""],
         |    "attachmentLink": "",
         |    "markdownEmailBody": "",
         |    "htmlEmailBody": "",
         |    "subject": "",
         |    "composedBy": "auto-emailer",
         |    "approvedBy": "auto-emailer"
         |  }
      """.stripMargin
    stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(outgoingEmail)
      .withStatus(200)))
  }

  trait FailingHttp {
      self: Setup =>
    stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(404)))
  }

  "emailConnector" should {

    "send gatekeeper email" in new Setup with WorkingHttp {
      await(underTest.sendEmail(emailPreviewForm))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailServicePath))
      )
    }

    "fail to send gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.sendEmail(emailPreviewForm))
      }
    }
  }
}
