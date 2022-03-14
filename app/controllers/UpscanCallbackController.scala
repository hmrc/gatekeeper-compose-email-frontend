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

import models.upscan._
import play.api.i18n.I18nSupport
import play.api.libs.json.JsValue
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanCallbackController @Inject()(
  mcc: MessagesControllerComponents,
  //fileUploadRepository: FileUploadRepository,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc)
    with I18nSupport {

  def callbackHandler(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FileUpload] { fileUploadResponse =>
      //fileUploadRepository.updateRecord(deriveFileStatus(fileUploadResponse)).map { isOk =>
        //if (isOk) NoContent else InternalServerError
      println(s"***********---> $fileUploadResponse")
      Future.successful(Ok)
      //}
    }
  }

  private[controllers] def deriveFileStatus(fileUpload: FileUpload): FileUpload = {
    fileUpload.failureDetails match {
      case Some(details) if details.failureReason == "QUARANTINE" =>
        fileUpload.copy(fileStatus = Some(FileStatusEnum.FAILED_QUARANTINE))
      case Some(details) if details.failureReason == "REJECTED" =>
        fileUpload.copy(fileStatus = Some(FileStatusEnum.FAILED_REJECTED))
      case Some(details) =>
        fileUpload.copy(fileStatus = Some(FileStatusEnum.FAILED_UNKNOWN))
      case None => fileUpload
    }
  }

}
