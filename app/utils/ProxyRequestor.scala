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

package utils

import akka.stream.scaladsl.Source
import akka.util.ByteString
import config.AppConfig
import models.ErrorResponse
import play.api.libs.Files.logger
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData
import utils.ErrorAction
import utils.UploadProxyController.ErrorResponseHandler.proxyErrorResponse

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProxyRequestor @Inject() (wsClient: WSClient)(implicit val ec: ExecutionContext) {

  def proxyRequest(errorAction: ErrorAction, body: Source[MultipartFormData.Part[Source[ByteString, _]], _],
                   upscanUrl: String): Future[Option[ErrorResponse]] = {
    for {
      response <- post(upscanUrl, body)

      _ = logger.debug(
        s"Upload response for Key=[${errorAction.key}] has status=[${response.status}], " +
          s"headers=[${response.headers}], body=[${response.body}]")
    } yield
      response match {
        case r if r.status >= 200 && r.status < 299 =>
          logger.info(s"response status: ${response.status}")
          None
        case r                                      =>
          logger.info(s"response status for non 200 to 400 is : ${response.status} and " +
            s"body: ${response.body} and headers: ${response.headers}")
          proxyErrorResponse(errorAction, r.status, r.body, r.headers)
      }
  }

  def post(upscanUrl: String, body: Source[MultipartFormData.Part[Source[ByteString, _]], _]) = {
    wsClient
      .url(upscanUrl)
      .withFollowRedirects(follow = false)
      .post(body)
  }
}