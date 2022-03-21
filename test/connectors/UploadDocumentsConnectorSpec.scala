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
import config.{AppConfig, EmailConnectorConfig}
import controllers.{ComposeEmailForm, EmailPreviewForm}
import models.file_upload.UploadDocumentsWrapper
import models.{OutgoingEmail, User}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.mvc.Results.Status
import play.api.test.Helpers.OK
import services.ComposeEmailService
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class UploadDocumentsConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterEach with BeforeAndAfterAll with GuiceOneAppPerSuite {


  val subject = "Email subject"
  val emailUUID = "email-uuid"
  val emailBody = "Body to be used in the email template"
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
  val mockComposeEmailConnector: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
  class ComposeEmailServiceStub extends ComposeEmailService(mockComposeEmailConnector) {
    override def fetchEmail(emailUUID: String)(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
      Future.successful(Json.parse(outgoingEmail).as[OutgoingEmail])
    }
  }
  val composEmailServieStub = new ComposeEmailServiceStub

  trait Setup {
    val httpClient = app.injector.instanceOf[HttpClient]


    implicit val hc = HeaderCarrier()
    implicit val appConfig = mock[AppConfig]
    val CREATED = 201
    val OK = 200

    class UploadDocumentsConnectorSuccess extends UploadDocumentsConnector(httpClient, composEmailServieStub) {
      override def actualPost(request: UploadDocumentsWrapper)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
        Future.successful(HttpResponse.apply(CREATED, "", Map[String, Seq[String]]("Location" -> Seq("/upload-documents"))))
      }
    }
    lazy val underTestSuccess = new UploadDocumentsConnectorSuccess

    class UploadDocumentsConnectorFailure extends UploadDocumentsConnector(httpClient, composEmailServieStub) {
      override def actualPost(request: UploadDocumentsWrapper)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
        Future.successful(HttpResponse.apply(OK, "", Map.empty[String, Seq[String]]))
      }
    }
    lazy val underTestFailure = new UploadDocumentsConnectorFailure

    val users = List(User("example@example.com", "first name", "last name", true),
      User("example2@example2.com", "first name2", "last name2", true))


  }

  "UploadDocumentsConnector" should {

    "send succesful initialise to UDF" in new Setup {
      val result = await(underTestSuccess.initializeNewFileUpload(emailUUID, true, true))
      result shouldBe Some("/upload-documents")
    }

    "send failed initialise to UDF" in new Setup {
      val result = await(underTestFailure.initializeNewFileUpload(emailUUID, true, true))
      result shouldBe None
    }
  }

}