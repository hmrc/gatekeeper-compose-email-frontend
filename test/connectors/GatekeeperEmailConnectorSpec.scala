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

  val gatekeeperLink = "http://some.url"
  val emailAddress = "email@example.com"
  val subject = "Email subject"
  val emailUID = "email-uuid"
  val keyRef = "file-key"
  val emailSendServicePath = s"/gatekeeper-email/send-email/$emailUID"
  val emailSaveServicePath = s"/gatekeeper-email/save-email?emailUID=$emailUID&key=$keyRef"
  val emailUpdateServicePath = s"/gatekeeper-email/update-email?emailUID=$emailUID&key=$keyRef"
  val fetchEmailUrl = s"/gatekeeper-email/fetch-email/$emailUID"
  val inProgressUploadStatusUrl = s"/gatekeeperemail/insertfileuploadstatus?key=$keyRef"
  val fetchProgressUploadStatusUrl = s"/gatekeeperemail/fetchfileuploadstatus?key=$keyRef"
  val emailBody = "Body to be used in the email template"

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]

    val fakeEmailConnectorConfig = new EmailConnectorConfig {
      val emailBaseUrl = wireMockUrl
      override val emailSubject: String = subject
    }

    implicit val hc = HeaderCarrier()

    lazy val underTest = new GatekeeperEmailConnector(httpClient, fakeEmailConnectorConfig)
    val composeEmailForm: ComposeEmailForm = ComposeEmailForm(subject, emailBody)
    val emailPreviewForm: EmailPreviewForm = EmailPreviewForm(emailUID, composeEmailForm)
    val users = List(User("example@example.com", "first name", "last name", true),
      User("example2@example2.com", "first name2", "last name2", true))
  }

  trait WorkingHttp {
    self: Setup =>

    val outgoingEmail =
      s"""
         |  {
         |    "emailUID": "$emailUID",
         |    "recipientTitle": "Team-Title",
         |    "recipients": [{"email": "", "firstName": "", "lastName": "", "verified": true}],
         |    "attachmentLink": "",
         |    "markdownEmailBody": "",
         |    "htmlEmailBody": "",
         |    "subject": "",
         |    "composedBy": "auto-emailer",
         |    "approvedBy": "auto-emailer"
         |  }
      """.stripMargin
    val uploadInfo =
      s"""
         |{"reference":{"value":"file-key"},
         |"status":{"_type":"InProgress"}
         |}
         |
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
    stubFor(get(urlEqualTo(fetchProgressUploadStatusUrl)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(uploadInfo)
      .withStatus(OK)))
    stubFor(post(urlEqualTo(inProgressUploadStatusUrl)).willReturn(aResponse()
      .withHeader("Content-type", "application/json")
      .withBody(uploadInfo)
      .withStatus(OK)))
  }

  trait FailingHttp {
    self: Setup =>
    stubFor(post(urlEqualTo(emailSendServicePath)).willReturn(aResponse().withStatus(NOT_FOUND)))
    stubFor(post(urlEqualTo(emailSaveServicePath)).willReturn(aResponse().withStatus(NOT_FOUND)))
    stubFor(post(urlEqualTo(inProgressUploadStatusUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
    stubFor(get(urlEqualTo(fetchProgressUploadStatusUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
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
      await(underTest.saveEmail(composeEmailForm, emailUID, users, keyRef))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailSaveServicePath))
      )
    }

    "fail to save gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.saveEmail(composeEmailForm, emailUID, users, keyRef))
      }
    }

    "update gatekeeper email" in new Setup with WorkingHttp {
      await(underTest.updateEmail(composeEmailForm, emailUID, users, keyRef))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(emailUpdateServicePath))
      )
    }

    "fail to update gatekeeper email" in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.updateEmail(composeEmailForm, emailUID, users, keyRef))
      }
    }

    "fetch email info" in new Setup with WorkingHttp {
      await(underTest.fetchEmail(emailUID))

      wireMockVerify(1, getRequestedFor(
        urlEqualTo(fetchEmailUrl))
      )
    }

    "fail to fetch  email info " in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.fetchEmail(emailUID))
      }
    }


    "fetch file upload status info" in new Setup with WorkingHttp {
      await(underTest.fetchFileuploadStatus("file-key"))

      wireMockVerify(1, getRequestedFor(
        urlEqualTo(fetchProgressUploadStatusUrl))
      )
    }

    "fail to fetch  file upload status " in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.fetchFileuploadStatus("file-key"))
      }
    }

    "save file upload status info" in new Setup with WorkingHttp {
      await(underTest.inProgressUploadStatus("file-key"))

      wireMockVerify(1, postRequestedFor(
        urlEqualTo(inProgressUploadStatusUrl))
      )
    }

    "fail to save  file upload status " in new Setup with FailingHttp {
      intercept[UpstreamErrorResponse] {
        await(underTest.inProgressUploadStatus("file-key"))
      }
    }
  }
}