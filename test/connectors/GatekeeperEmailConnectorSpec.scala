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
import play.api.http.Status.{ACCEPTED, NOT_FOUND, OK}
import services.ComposeEmailService
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, Upstream5xxResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
  val emailServicePath = "/gatekeeper-email"
  val emailBody = "Body to be used in the email template"

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]

    val fakeEmailConnectorConfig = new EmailConnectorConfig {
      val emailBaseUrl = wireMockUrl
      override val emailSubject: String = subject
    }

    implicit val hc = HeaderCarrier()

    class NewGatekeeperEmailConnectorSuccess extends GatekeeperEmailConnector(httpClient, fakeEmailConnectorConfig) {

      override def sendEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[Int] = {
        Future.successful(ACCEPTED)
      }
    }
    class NewGatekeeperEmailConnectorFailed extends GatekeeperEmailConnector(httpClient, fakeEmailConnectorConfig) {

      override def sendEmail(composeEmailForm: ComposeEmailForm)(implicit hc: HeaderCarrier): Future[Int] = {
        throw Upstream5xxResponse("Internal Server Error", 500, 100)
      }
    }
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm(emailAddress, subject, emailBody)
    lazy val underTestSuccess = new NewGatekeeperEmailConnectorSuccess
    lazy val underTestFailed = new NewGatekeeperEmailConnectorFailed

  }

  trait WorkingHttp {
    self: Setup =>
    stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(OK)))
  }

  trait FailingHttp {
    self: Setup =>
    stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(NOT_FOUND)))
  }

  "emailConnector" should {

    "send gatekeeper email" in new Setup  {
      val result = await(underTestSuccess.sendEmail(composeEmailForm))


      result shouldBe ACCEPTED
      }

      "fail to send gatekeeper email" in new Setup  {
        intercept[UpstreamErrorResponse] {
          await(underTestFailed.sendEmail(composeEmailForm))
        }
      }
    }
}
