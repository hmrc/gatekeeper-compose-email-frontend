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

import play.api.libs.json.Json

case class NavLink(label: String, href: Option[String])

object NavLink {
  implicit val format = Json.format[NavLink]
}

case object StaticNavLinks {

  def apply(): Seq[NavLink] = {
    Seq(
      NavLink("Applications", Some("back to GK")),
      NavLink("Developers", Some("back to GK")),
      NavLink("Email", Some("back to GK")),
      NavLink("API Approvals", Some("back to GK"))
    )
  }
}

case object UserNavLinks {

  private def loggedInNavLinks(userFullName: String) = Seq(NavLink(userFullName, None))

  private val loggedOutNavLinks: Seq[NavLink] = Seq.empty

  def apply(userFullName: Option[String]): Seq[NavLink] = userFullName match {
    case Some(name) => loggedInNavLinks(name)
    case None => loggedOutNavLinks
  }
}
