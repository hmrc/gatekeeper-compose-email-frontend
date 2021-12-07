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

package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{verify => wireMockVerify, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import common.AsyncHmrcSpec
import config.EmailConnectorConfig
import controllers.ComposeEmailForm
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
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
  val emailId = "email@example.com"
  val subject = "Email subject"
  val emailServicePath = "/gatekeeper-email"
  val emailBody = "Body to be used in the email template"

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]
    
    val fakeEmailConnectorConfig = new EmailConnectorConfig {
      val emailBaseUrl = wireMockUrl
      override val emailSubject: String = subject
    }

    implicit val hc = HeaderCarrier()

    lazy val underTest = new GatekeeperEmailConnector(httpClient, fakeEmailConnectorConfig)
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm(emailId, subject, emailBody)

  }

  trait WorkingHttp {
      self: Setup =>
    stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(OK)))
  }

  trait FailingHttp {
      self: Setup =>
    stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(404)))
  }
  
  "emailConnector" should {

    "send gatekeeper email" in new Setup with WorkingHttp {
      await(underTest.sendEmail(composeEmailForm))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailServicePath))
        .withRequestBody(equalToJson(
          s"""
              |{
              |  "to": ["$emailId"],
              |  "templateId": "gatekeeper",
              |  "emailData": {
              |    "emailRecipient": "$emailId",
              |    "emailSubject": "$subject",
              |    "emailBody": "$emailBody",
              |  },
              |  "force": false,
              |  "auditData": {}
              |}""".stripMargin))
      )
    }

    "fail to send gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.sendEmail(composeEmailForm))
      }
    }
  }
}
