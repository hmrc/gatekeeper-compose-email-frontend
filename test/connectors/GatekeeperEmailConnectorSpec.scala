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

import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.ComposeEmailForm
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, BAD_REQUEST}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

class GatekeeperEmailConnectorSpec extends AnyWordSpec with WireMockSupport with GuiceOneAppPerSuite {
  implicit val hc = HeaderCarrier()
  override implicit lazy val app: Application = appBuilder.build()
  val appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.gatekeeper-email.port" -> wireMockPort,
        "metrics.enabled" -> false,
        "auditing.enabled" -> false,
      )

  val emailAddress = "email@example.com"
  val emailSubject = "Email subject"
  val emailServicePath = "/gatekeeper-email"
  val emailBody = "Body to be used in the email template"
  val composeEmailForm: ComposeEmailForm = ComposeEmailForm(emailAddress, emailSubject, emailBody)

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]
    val underTest = app.injector.instanceOf[GatekeeperEmailConnector]
  }

  "emailConnector" should {
    "send gatekeeper email" in new Setup {
      stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(ACCEPTED).withBody("202")))

      val result: Int = await(underTest.sendEmail(composeEmailForm))

      result shouldBe ACCEPTED
    }

    "fail to send gatekeeper email" in new Setup {
      stubFor(post(urlEqualTo(emailServicePath)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody("Expected Wiremock error")))

      val exception: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(underTest.sendEmail(composeEmailForm))
      }

      exception.getMessage() shouldBe "POST of 'http://localhost:22221/gatekeeper-email' returned 400. Response body: 'Expected Wiremock error'"
    }
  }
}
