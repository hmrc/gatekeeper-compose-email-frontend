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

import connectors.GatekeeperEmailConnector
import controllers.{ComposeEmailForm, EmailPreviewForm}
import models.{OutgoingEmail, User}
import org.mockito.ArgumentMatchersSugar
import org.mockito.scalatest.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import play.mvc.Http.Status
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ComposeEmailServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val mockEmailConnector = mock[GatekeeperEmailConnector]
    val underTest = new ComposeEmailService(mockEmailConnector)
    val su = List(User("sawd", "efef", "eff", true))
    val emailUUID = "emailUUID"
  }

  "saveEmail" should {
    "handle saving an email successfully" in new Setup {
      when(mockEmailConnector.saveEmail(*, *, *)(*)).thenReturn(Future.successful(OutgoingEmail("", "", su, None, "", "", "", "", None)))
      val result = await(underTest.saveEmail(new ComposeEmailForm("", "", true), "", su))
      result shouldBe OutgoingEmail("", "", su, None, "", "", "", "", None)
    }
  }

  "fetchEmail" should {
    "handle fetching an email successfully" in new Setup {
      when(mockEmailConnector.fetchEmail(*)(*)).thenReturn(Future.successful(OutgoingEmail("", "", su, None, "", "", "", "", None)))
      val result = await(underTest.fetchEmail(emailUUID = emailUUID))
      result shouldBe OutgoingEmail("", "", su, None, "", "", "", "", None)
    }
  }

  "updateEmail" should {
    "handle updating an email successfully" in new Setup {
      when(mockEmailConnector.updateEmail(*, *, *, *)(*)).thenReturn(Future.successful(OutgoingEmail("", "", su, None, "", "", "", "", None)))
      val result = await(underTest.updateEmail(new ComposeEmailForm("", "", true), "", su))
      result shouldBe OutgoingEmail("", "", su, None, "", "", "", "", None)
    }
  }
}
