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

import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import uk.gov.hmrc.govukfrontend.views.Aliases._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UploadAnotherFileController @Inject() (
  mcc: MessagesControllerComponents,
  formProvider: UploadAnotherFileFormProvider,
  view: UploadAnotherFileView,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc)
    with I18nSupport {

  def onLoad: Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    request.userAnswers.get(FileUploadPage).fold(
      Future(Redirect(controllers.docUpload.routes.SupportingDocController.onLoad().url))
    ) { files =>
      if (files.isEmpty) {
        Future.successful(Redirect(controllers.docUpload.routes.UploadFileController.onLoad()))
      } else {
        Future.successful(Ok(view(formProvider(), buildSummaryList(files), getOptionalDocs(request.userAnswers))))
      }
    }
  }

  def onSubmit: Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    formProvider().bindFromRequest().fold(
      formWithErrors => resultWithErrors(formWithErrors),
      addAnotherFile => {
        if (addAnotherFile) {
          Future.successful(Redirect(controllers.docUpload.routes.UploadFileController.onLoad()))
        } else {
          if (request.checkMode) {
            Future.successful(Redirect(controllers.cya.routes.CheckYourAnswersController.onLoad()))
          } else {
            Future.successful(Redirect(controllers.contactDetails.routes.DeclarantContactDetailsController.onLoad()))
          }
        }
      }
    )
  }

  private def resultWithErrors(
    formWithErrors: Form[Boolean]
  )(implicit request: DataRequest[AnyContent]): Future[Result] =
    request.userAnswers.get(FileUploadPage).fold(
      Future(Redirect(controllers.docUpload.routes.UploadFileController.onLoad().url))
    ) { files =>
      Future.successful(BadRequest(view(formWithErrors, buildSummaryList(files), getOptionalDocs(request.userAnswers))))
    }

  private def getOptionalDocs(userAnswers: UserAnswers): Seq[OptionalDocument] = {
    val anyOptionalDocs = userAnswers.get(AnyOtherSupportingDocsPage).getOrElse(false)
    if (anyOptionalDocs) {
      userAnswers.get(OptionalSupportingDocsPage).getOrElse(Seq.empty)
    } else {
      Seq.empty
    }
  }

  private def buildSummaryList(files: Seq[FileUploadInfo])(implicit messages: Messages) = {
    val summaryListRows = files.zipWithIndex.map { case (file, index) =>
      val removeLink = controllers.docUpload.routes.RemoveUploadedFileController.onLoad(Index(index)).url
      SummaryListRow(
        key = Key(content = Text(file.fileName), classes = s"govuk-!-width-one-third govuk-!-font-weight-regular".trim),
        value = Value(
          content = HtmlContent(
            s"""<a href=$removeLink class="govuk-link"> <span aria-hidden="true">${messages("common.remove")}</span>
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

}
