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
import connectors.GatekeeperEmailFileUploadConnector
import forms.UploadAnotherFileFormProvider
import models.GatekeeperRole
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import services.ComposeEmailService
import uk.gov.hmrc.govukfrontend.views.Aliases._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.GatekeeperAuthWrapper
import views.html.{EmailPreview, UploadAnotherFileView}

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
  fileUploadConnector: GatekeeperEmailFileUploadConnector,
  implicit val ec: ExecutionContext,
  implicit val hc: HeaderCarrier,
  implicit val appConfig: AppConfig,
) extends FrontendController(mcc) with GatekeeperAuthWrapper
    with I18nSupport {

  def onLoad(emailUUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    emailService.fetchEmail(emailUUID) flatMap
     { email =>
      if (!email.attachmentDetails.isDefined) {
        Future.successful(Redirect(controllers.routes.UploadFileController.onLoad(emailUUID)))
      } else {
        Future.successful(Ok(view(formProvider(), buildSummaryList(email.attachmentDetails))))
      }
    }
  }

  def onSubmit(emailUUID: String): Action[AnyContent] = requiresAtLeast(GatekeeperRole.USER) { implicit request =>
    formProvider().bindFromRequest().fold(
      formWithErrors => resultWithErrors(formWithErrors),
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

  private def resultWithErrors(
    formWithErrors: Form[Boolean]
  ): Future[Result] =
    request.userAnswers.get(FileUploadPage).fold(
      Future(Redirect(controllers.docUpload.routes.UploadFileController.onLoad().url))
    ) { files =>
      Future.successful(BadRequest(view(formWithErrors, buildSummaryList(files))))
    }

  private def getOptionalDocs(userAnswers: UserAnswers): Seq[OptionalDocument] = {
    val anyOptionalDocs = userAnswers.get(AnyOtherSupportingDocsPage).getOrElse(false)
    if (anyOptionalDocs) {
      userAnswers.get(OptionalSupportingDocsPage).getOrElse(Seq.empty)
    } else {
      Seq.empty
    }
  }

  private def buildSummaryList(files: Seq[String])(implicit messages: Messages) = {
    val fileUploads = files.map(file => fileUploadConnector.fetchFileuploadStatus(file))

    val summaryListRows = fileUploads.zipWithIndex.map { case (file, index) =>
      //val removeLink = controllers.routes.RemoveUploadedFileController.onLoad(Index(index)).url
      SummaryListRow(
        key = Key(content = Text(file.fileName), classes = s"govuk-!-width-one-third govuk-!-font-weight-regular".trim),
        value = Value(
          content = HtmlContent(
            s"""<a href=removeLink class="govuk-link"> <span aria-hidden="true">${messages("common.remove")}</span>
               |<span class="govuk-visually-hidden"> Remove ${file.fileName}</span>
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
