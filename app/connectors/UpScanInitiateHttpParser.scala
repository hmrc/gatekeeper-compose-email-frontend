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

import models.{BadRequest, ErrorResponse, InvalidJson, UnexpectedFailure}
import models.upscan.UpScanInitiateResponse
import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.JsSuccess
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object UpScanInitiateHttpParser extends Logging {
  type UpscanInitiateResponse = Either[ErrorResponse, UpScanInitiateResponse]

  implicit object UpScanInitiateResponseReads extends HttpReads[UpscanInitiateResponse] {

    def read(method: String, url: String, response: HttpResponse): UpscanInitiateResponse = {
      response.status match {
        case OK =>
          response.json.validate[UpScanInitiateResponse](
            UpScanInitiateResponse.jsonReadsForUpScanInitiateResponse
          ) match {
            case JsSuccess(model, _) => Right(model)
            case _                   => Left(InvalidJson)
          }
        case BAD_REQUEST =>
          logger.warn(s"Bad request returned with reason: ${response.body}")
          Left(BadRequest)
        case status =>
          logger.warn(s"Unexpected response, status $status returned")
          Left(UnexpectedFailure(status, s"Unexpected response, status $status returned"))
      }
    }
  }

}
