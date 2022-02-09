package models

import models.upscan.UploadedFile
import models.upscan.UploadDocumentType
import play.api.libs.json.Json
import play.api.libs.json.Format

final case class UploadDocumentsCallback(
                                          uploadedFiles: Seq[UploadedFile],
                                          cargo: UploadDocumentType
                                        ) {
  def documentType: UploadDocumentType = cargo
}

object UploadDocumentsCallback {
  implicit val format: Format[UploadDocumentsCallback] = Json.format[UploadDocumentsCallback]
}
