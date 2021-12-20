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

package controllers

import models.GatekeeperSessionKeys
import mocks.connector.AuthConnectorMock
import org.mockito.ArgumentMatchersSugar
import org.mockito.scalatest.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

class ControllerSetupBase extends MockitoSugar
with ArgumentMatchersSugar
with AuthConnectorMock {

  val userToken = GatekeeperSessionKeys.LoggedInUser -> userName
  val authToken = GatekeeperSessionKeys.AuthToken -> "some-bearer-token"
  val aLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(authToken, userToken)


}
