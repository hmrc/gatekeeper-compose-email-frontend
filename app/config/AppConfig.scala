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

package config

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {
  def appName: String
  def assetsPrefix: String
  def title: String

  def gatekeeperSuccessUrl: String
  def strideLoginUrl: String

  def authBaseUrl: String
  def adminRole: String
  def superUserRole: String
  def userRole: String
  def welshLanguageSupportEnabled: Boolean
}

@Singleton
class AppConfigImpl @Inject()(config: Configuration)
  extends ServicesConfig(config) with AppConfig with EmailConnectorConfig {
  val welshLanguageSupportEnabled = config.getOptional[Boolean]("features.welsh-language-support").getOrElse(false)

  val appName = "HMRC API Gatekeeper"
  val assetsPrefix = getString("assets.url") + getString("assets.version")
  val title = "HMRC API Gatekeeper"
  val emailBaseUrl =  baseUrl("gatekeeper-email")
  val emailSubject =  getString("emailSubject")


  override def gatekeeperSuccessUrl: String = getString("api-gatekeeper-email-success-url")

  override def strideLoginUrl: String = s"${baseUrl("stride-auth-frontend")}/stride/sign-in"

  val authBaseUrl = baseUrl("auth")

  override def adminRole: String = getString("roles.admin")

  override def superUserRole: String = getString("roles.super-user")

  override def userRole: String =  getString("roles.user")
}

trait EmailConnectorConfig {
  val emailBaseUrl: String
  val emailSubject: String
}