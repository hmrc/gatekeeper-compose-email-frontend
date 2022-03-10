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

package controllers

import config.AppConfig
import connectors.{AuthConnector, UploadDocumentsConnector}
import models.{GatekeeperRole, OutgoingEmail}
import models.file_upload.UploadedFileMetadata
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.ComposeEmailService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper
import views.html.{ComposeEmail, EmailPreview, ForbiddenView}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadController @Inject()(
                                      mcc: MessagesControllerComponents,
                                      override val forbiddenView: ForbiddenView,
                                      override val authConnector: AuthConnector,
                                      uploadDocumentsConnector: UploadDocumentsConnector,
                                      emailService: ComposeEmailService,
                                      composeEmail: ComposeEmail,
                                      emailPreview: EmailPreview
                                    )(implicit executionContext: ExecutionContext, appConfig: AppConfig)
  extends FrontendController(mcc) with I18nSupport  with GatekeeperAuthWrapper with Logging{


  def start(emailUUID: String, searched: Boolean = false, multipleUpload: Boolean = true): Action[AnyContent] =
    requiresAtLeast(GatekeeperRole.USER) { implicit request =>
      uploadDocumentsConnector.initializeNewFileUpload(emailUUID, searched, multipleUpload)
        .map {
          case Some(url) => Redirect(s"${appConfig.fileUploadPublicUrl}$url")
          case None => BadRequest
        }
  }

  def continue(emailUUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER)  { implicit request =>
    Future.successful(NoContent)
  }
}
