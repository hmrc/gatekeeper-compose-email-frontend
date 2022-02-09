package models.upscan

import play.api.libs.json.Format
import play.api.libs.json.Json
import models.upscan.UploadDocumentType
import UploadDocumentsSessionConfig._

final case class UploadDocumentsSessionConfig(
                                               //nonce: Nonce, // unique secret shared by the host and upload microservices
                                               continueUrl: String, // url to continue after uploading the files
                                               continueWhenFullUrl: String, // url to continue after all possible files has been uploaded
                                               backlinkUrl: String, // backlink url
                                               callbackUrl: String, // url where to post uploaded files
                                               minimumNumberOfFiles: Int, // minimum number of files to upload
                                               maximumNumberOfFiles: Int, // maximum number of files to upload
                                               initialNumberOfEmptyRows: Int, // number of empty 'choose file' rows to display
                                               maximumFileSizeBytes: Long, // maximum size of a single file upload
                                               allowedContentTypes: String, // a list of allowed file types (i.e. MIME types),
                                               allowedFileExtensions: String, // file picker filter hint, see: https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/accept
                                               cargo: UploadDocumentType, // type of the document to assign to the newly added files
                                               newFileDescription: String, // description of the new file added
                                               content: Content
                                             )

object UploadDocumentsSessionConfig {

  final case class Content(
                            serviceName: String,
                            title: String,
                            descriptionHtml: String,
                            serviceUrl: String,
                            accessibilityStatementUrl: String,
                            phaseBanner: String,
                            phaseBannerUrl: String,
                            signOutUrl: String,
                            timedOutUrl: String,
                            keepAliveUrl: String,
                            timeoutSeconds: Int,
                            countdownSeconds: Int,
                            showLanguageSelection: Boolean,
                            pageTitleClasses: String,
                            allowedFilesTypesHint: String
                          )

  object Content {
    implicit val format: Format[Content] = Json.format[Content]
  }

  implicit val format: Format[UploadDocumentsSessionConfig] =
    Json.format[UploadDocumentsSessionConfig]
}
