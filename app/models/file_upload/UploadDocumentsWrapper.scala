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

package models.file_upload

import config.AppConfig
import connectors.UploadDocumentsConnector
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.Request

case class UploadDocumentsWrapper(config: UploadDocumentsConfig, existingFiles: Seq[UploadedFile])
import connectors.UploadDocumentsConnector.Request
object UploadDocumentsWrapper {

  def createPayload(nonce: Nonce,
                    emailUID: String,
                    searched: Boolean,
                    multipleUpload: Boolean,
                    attachmentDetails: Option[Seq[UploadedFile]]
                   )(implicit appConfig: AppConfig): UploadDocumentsWrapper = {
    val continueUrl = controllers.routes.ComposeEmailController.emailPreview(emailUID)
    val backLinkUrl = controllers.routes.ComposeEmailController.upload(emailUID)
    val callBack = controllers.routes.FileUploadController.updateFiles()

    val attachments =
      if(attachmentDetails.isDefined) {
        attachmentDetails.get
      } else Seq.empty

    UploadDocumentsWrapper(
      config = UploadDocumentsConfig(
        nonce = nonce,
        initialNumberOfEmptyRows = Some(1),
        continueUrl = s"${appConfig.fileUploadCallbackUrlPrefix}$continueUrl",
        backlinkUrl = s"${appConfig.fileUploadCallbackUrlPrefix}$backLinkUrl",
        callbackUrl = s"${appConfig.fileUploadCallbackUrlPrefix}$callBack",
        cargo = UploadCargo(emailUID),
//        content = Some(UploadDocumentsContent(
//          serviceName = Some(appConfig.fileUploadServiceName),
//          serviceUrl = Some(appConfig.homepage),
//          accessibilityStatementUrl = Some(appConfig.fileUploadAccessibilityUrl),
//          phaseBanner = Some(appConfig.fileUploadPhase),
//          phaseBannerUrl = Some(appConfig.fileUploadPhaseUrl),
//          userResearchBannerUrl = Some(appConfig.helpMakeGovUkBetterUrl),
//          contactFrontendServiceId = Some(appConfig.contactFrontendServiceId)
//        )),
        content = None,
//        features = Some(UploadDocumentsFeatures(Some(multipleUpload)))
        features = None
      ),
        existingFiles = attachments
    )
  }

  implicit val format: OFormat[UploadDocumentsWrapper] = Json.format[UploadDocumentsWrapper]
}