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

import com.google.common.base.Charsets
import config.AppConfig
import connectors.{AuthConnector, GatekeeperEmailFileUploadConnector}
import forms.UploadAnotherFileFormProvider
import models.GatekeeperRole
import models.upscan.{UploadInfo, UploadedSuccessfully}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.EventSource.Event.writeable
import play.api.mvc._
import services.ComposeEmailService
import uk.gov.hmrc.govukfrontend.views.Aliases._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper
import views.html.{EmailPreview, ForbiddenView, UploadAnotherFileView}

import java.util.Base64
import javax.inject.Inject
import scala.collection.script.Index
import scala.concurrent.{ExecutionContext, Future}

class UploadAnotherFileController @Inject() (
  mcc: MessagesControllerComponents,
  formProvider: UploadAnotherFileFormProvider,
  emailService: ComposeEmailService,
  view: UploadAnotherFileView,
  emailPreview: EmailPreview,
  override val forbiddenView: ForbiddenView,
  override val authConnector: AuthConnector,
  fileUploadConnector: GatekeeperEmailFileUploadConnector,
  implicit val ec: ExecutionContext,
  implicit val appConfig: AppConfig,
) extends FrontendController(mcc) with GatekeeperAuthWrapper
    with I18nSupport {

  def onLoad(emailUUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    emailService.fetchEmail(emailUUID) flatMap
     { email =>
      if (!email.attachmentDetails.isDefined) {
        Future.successful(Redirect(controllers.routes.UploadFileController.onLoad(emailUUID)))
      } else {
        val fileUploads = Future.sequence(email.attachmentDetails.get.map(
          file => fileUploadConnector.fetchFileuploadStatus(file)
          ))
        val result = fileUploads.map(buildSummaryList(_))
        result.map( r => Ok(view(formProvider(), r, emailUUID)))
      }
    }
  }
//TODO fix user selection..
  def onSubmit(emailUUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    formProvider().bindFromRequest().fold(
      formWithErrors => Future.successful(Redirect(controllers.routes.UploadFileController.onLoad(emailUUID))),
      addAnotherFile => {
        if (addAnotherFile) {
          Future.successful(Redirect(controllers.routes.UploadFileController.onLoad(emailUUID)))
        } else {
          emailService.fetchEmail(emailUUID) map (email =>
          Ok(emailPreview(base64Decode(email.htmlEmailBody),
            controllers.EmailPreviewForm.form.fill(EmailPreviewForm(email.emailUUID, ComposeEmailForm(email.subject, email.markdownEmailBody, true))),
            Map("user selection" -> ""))) )
        }
      }
    )
  }

//  private def resultWithErrors(
//    formWithErrors: Form[Boolean], emailUUID: String
//  ): Future[Result] = {
//      Future.successful(BadRequest(view(formWithErrors, buildSummaryList())))
//    }

  private def buildSummaryList(files: Seq[UploadInfo])(implicit messages: Messages) = {

    val summaryListRows = files.zipWithIndex.map { case (file, index) =>
      //val removeLink = controllers.routes.RemoveUploadedFileController.onLoad(Index(index)).url
      file.status.asInstanceOf[UploadedSuccessfully].name
      SummaryListRow(
        key = Key(content = Text(file.status.asInstanceOf[UploadedSuccessfully].name), classes = s"govuk-!-width-one-third govuk-!-font-weight-regular".trim),
        value = Value(
          content = HtmlContent(
            s"""<a href=removeLink class="govuk-link"> <span aria-hidden="true">${messages("common.remove")}</span>
               |<span class="govuk-visually-hidden"> Remove ${file.status.asInstanceOf[UploadedSuccessfully].name}</span>
               |</a>""".stripMargin
          ),
          classes = "govuk-summary-list__actions"
        ),
        actions = None
      )
    }
    SummaryList(
      classes = "govuk-!-margin-bottom-9",
      rows = summaryListRows
    )
  }

  private def base64Decode(result: String): String =
    new String(Base64.getDecoder.decode(result), Charsets.UTF_8)

}
