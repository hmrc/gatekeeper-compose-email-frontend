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

package models

import uk.gov.hmrc.http.SessionKeys

import java.util.UUID

object GatekeeperRole extends Enumeration {
  type GatekeeperRole = Value
  val USER, SUPERUSER, ADMIN = Value
}

object GatekeeperSessionKeys {
  val LoggedInUser = "LoggedInUser"
  val AuthToken = SessionKeys.authToken
}

case class User (email: String, userId: String, firstName: String, lastName: String,
                 verified: Boolean, organisation: String, mfaEnabled: Boolean)

import java.util.UUID

case class UserId(value: UUID) extends AnyVal {
  def asText = value.toString
}

object UserId {
  import play.api.libs.json.Json
  implicit val format = Json.valueFormat[UserId]
  def random = UserId(UUID.randomUUID())
}

case class RegisteredUser(
                           email: String,
                           userId: UserId,
                           firstName: String,
                           lastName: String,
                           verified: Boolean,
                           organisation: Option[String] = None,
                           mfaEnabled: Boolean = false) {
}

object RegisteredUser {
  import UserId._
  import play.api.libs.json._

  implicit val registeredUserFormat = Json.format[RegisteredUser]
}
