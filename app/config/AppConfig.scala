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

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig


@Singleton
class AppConfig @Inject()(config: Configuration)
  extends ServicesConfig(config)
    with EmailConnectorConfig
{
  val welshLanguageSupportEnabled: Boolean = config.getOptional[Boolean]("features.welsh-language-support").getOrElse(false)

  def title = "HMRC API Gatekeeper"
  lazy val initiateUrl              = baseUrl("upscan-initiate") + "/upscan/initiate"
  lazy val initiateV2Url            = baseUrl("upscan-initiate") + "/upscan/v2/initiate"
  lazy val uploadRedirectTargetBase = getString("upload-redirect-target-base")
  lazy val callbackEndpointTarget   = getString("upscan.callback-endpoint")
  val emailBaseUrl =  baseUrl("gatekeeper-email")
  val emailSubject =  getString("emailSubject")
  val maxFileSize = getString("file-formats.max-file-size-mb")
  val approvedFileExtensions = getString("file-formats.approved-file-extensions")
  val approvedFileTypes = getString("file-formats.approved-file-types")
}

trait EmailConnectorConfig {
  val emailBaseUrl: String
  val emailSubject: String
}