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

import config.AppConfig
import connectors.UpScanInitiateHttpParser.{UpScanInitiateResponseReads, UpscanInitiateResponse}
import models.upscan.UpScanInitiateRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpScanConnector @Inject()(httpClient: HttpClient, appConfig: AppConfig) {

  lazy val urlForPostInitiate: String = s"${appConfig.upScanInitiateBaseUrl}/upscan/v2/initiate"

  def postToInitiate(
    body: UpScanInitiateRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UpscanInitiateResponse] =
    httpClient.POST(urlForPostInitiate, body)(UpScanInitiateRequest.jsonWrites, UpScanInitiateResponseReads, hc, ec)
}
