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

package services

import akka.stream.Materializer
import com.mongodb.client.result.InsertOneResult
import connectors.GatekeeperEmailConnector
import controllers.ComposeEmailForm
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mongodb.scala.bson.{BsonNumber, BsonValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{ACCEPTED, BAD_REQUEST, OK}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
class ComposeEmailServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]


  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val form = ComposeEmailForm("a@b.com", "subject of email", "email body")
    val invalidform = ComposeEmailForm("a@b", "", "")

    val gatekeeperEmailConnectorMock: GatekeeperEmailConnector = mock[GatekeeperEmailConnector]
    val underTest = new ComposeEmailService(gatekeeperEmailConnectorMock)

    when(gatekeeperEmailConnectorMock.sendEmail(form))
      .thenReturn(successful((ACCEPTED)))
    when(gatekeeperEmailConnectorMock.sendEmail(invalidform))
      .thenReturn(successful((BAD_REQUEST)))
  }

  "sendEmail" should {

    "return accepted on correct payload" in new Setup {
      val httpCode = await(underTest.sendEmail(form))
      httpCode shouldBe ACCEPTED
    }

    "return Bad Request on incorrect payload" in new Setup {
      val httpCode = await(underTest.sendEmail(invalidform))
      httpCode shouldBe BAD_REQUEST
    }
  }

}
