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

package services

import connectors.GatekeeperEmailConnector
import controllers.{ComposeEmailForm, EmailPreviewForm}
import models.{OutgoingEmail, UploadInfo, User}
import models.EmailRequest.createEmailRequest
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ComposeEmailService @Inject()(emailConnector: GatekeeperEmailConnector)
                         (implicit val ec: ExecutionContext){

  def saveEmail(composeEmailForm: ComposeEmailForm, emailUID: String,  userInfo: List[User])(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    emailConnector.saveEmail(composeEmailForm, emailUID, userInfo)
  }

  def fetchEmail(emailUID: String)(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    emailConnector.fetchEmail(emailUID)
  }

  def updateEmail(composeEmailForm: ComposeEmailForm, emailUID: String, users: List[User])(implicit hc: HeaderCarrier): Future[OutgoingEmail] = {
    emailConnector.updateEmail(composeEmailForm, emailUID, users)
  }

  def inProgressUploadStatus(keyReference: String)(implicit hc: HeaderCarrier): Future[UploadInfo] = {
    emailConnector.inProgressUploadStatus(keyReference)
  }

  def fetchFileuploadStatus(key: String)(implicit hc: HeaderCarrier) = {
    emailConnector.fetchFileuploadStatus(key)
  }
}
