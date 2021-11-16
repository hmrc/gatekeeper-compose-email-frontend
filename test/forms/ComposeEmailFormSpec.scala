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

package forms

import controllers.ComposeEmailForm
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ConposeEmailFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  "ComposeEmailForm" should {
    "successfully validate a complete form" in {
      val correctForm = Map("emailRecipient" -> "john.doe@blah.com", "emailSubject" -> "something", "emailBody" -> "blah\nblah")
      val boundForm = ComposeEmailForm.form.bind(correctForm)
      boundForm.errors.length shouldBe 0
    }
    "reject a form with no recipient address" in {
      val correctForm = Map("emailSubject" -> "something", "emailBody" -> "blah\nblah")
      val boundForm = ComposeEmailForm.form.bind(correctForm)
      boundForm.errors.length shouldBe 1
    }
    "reject a form with no subject" in {
      val correctForm = Map("emailRecipient" -> "john.doe@blah.com", "emailBody" -> "blah\nblah")
      val boundForm = ComposeEmailForm.form.bind(correctForm)
      boundForm.errors.length shouldBe 1
    }
  }
}
