package models.upscan

import play.api.libs.json.Format
import play.api.libs.json.Json

import java.time.ZonedDateTime
import models.upscan.UploadDocumentType

/** DTO between upload-documents-frontend and this microservice.
 * Do NOT rename fields!
 */
final case class UploadedFile(
                               upscanReference: String,
                               downloadUrl: String,
                               uploadTimestamp: ZonedDateTime,
                               checksum: String,
                               fileName: String,
                               fileMimeType: String,
                               fileSize: Option[Long],
                               cargo: Option[UploadDocumentType] = None,
                               description: Option[String] = None,
                               previewUrl: Option[String] = None
                             ) {
  def documentType: Option[UploadDocumentType] = cargo
}

object UploadedFile {
  implicit val formats: Format[UploadedFile] = Json.format[UploadedFile]
}
