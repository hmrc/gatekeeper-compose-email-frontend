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
import models.file_upload.{Nonce, UploadDocumentsWrapper, UploadedFile}
import play.api.http.Status.CREATED
import play.api.libs.json.{Format, Json}
import services.ComposeEmailService
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UploadDocumentsConnector @Inject()(httpClient: HttpClient,
                                         emailService: ComposeEmailService
                                        )(implicit executionContext: ExecutionContext, appConfig: AppConfig) {



  def initializeNewFileUpload(emailUID: String, searched: Boolean, multipleUpload: Boolean)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val nonce = Nonce.random
//    val payload = UploadDocumentsWrapper.createPayload(nonce, emailUID, searched, multipleUpload)
    for {
      emailInfo <- emailService.fetchEmail(emailUID)
      payload = UploadDocumentsWrapper.createPayload(nonce, emailUID, searched, multipleUpload, emailInfo.attachmentDetails)
      result <- sendRequest(payload)
    } yield result
  }

  private def sendRequest(request: UploadDocumentsWrapper)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    actualPost(request).map { response =>
      response.status match {
        case CREATED =>
          response.header("Location")
        case _ =>
          None
      }
    }.recover { case _ => None }
  }

  def actualPost(request: UploadDocumentsWrapper)(implicit hc: HeaderCarrier) = {
    httpClient.POST[UploadDocumentsWrapper, HttpResponse](appConfig.fileUploadInitializationUrl, request)
  }


}

object UploadDocumentsConnector {

  type Response = Option[String]

  final case class Request(
                            config: UploadDocumentsWrapper,
                            existingFiles: Seq[UploadedFile]
                          )

  implicit val requestFormat: Format[Request] = Json.format[Request]
}
