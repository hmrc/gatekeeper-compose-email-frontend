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
import models.upscan.{FileStatusEnum, FileUpload, UpscanInitiateError}
import models.upscan.UpscanErrors.{Quarantined, Rejected, TooBig, TooSmall, UpscanError}
import play.api.mvc.Results.InternalServerError
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait FileUploadHandler[T] {

  val appConfig: AppConfig

  val syncErrorToUpscanErrorMapping: String => UpscanError = Map(
    "EntityTooSmall" -> TooSmall,
    "EntityTooLarge" -> TooBig
  ).withDefaultValue(Unknown)

  val asyncErrorToUpscanErrorMapping: FileStatusEnum => UpscanError = {
    case FileStatusEnum.FAILED_QUARANTINE => Quarantined
    case FileStatusEnum.FAILED_REJECTED   => Rejected
    case _                                => Unknown
  }

  def handleUpscanResponse(
    key: Option[String],
    error: Option[UpscanInitiateError],
    successRoute: Result,
    errorRoute: Result
  )(implicit request: DataRequest[AnyContent], ec: ExecutionContext): Future[Result] = (key, error) match {
    case (Some(key), None) =>
      fileUploadRepository.updateRecord(FileUpload(key, Some(request.credId))).map { _ =>
        Thread.sleep(appConfig.upScanPollingDelayMilliSeconds)
        successRoute
      }
    case (_, Some(error)) =>
      val uploadError = syncErrorToUpscanErrorMapping(error.code)
      Future.successful(errorRoute.flashing("uploadError" -> uploadError.toString))
    case _ =>
      throw new RuntimeException("No key returned for successful upload")
  }

  def handleUpscanFileProcessing(
    key: String,
    uploadCompleteRoute: Result,
    uploadInProgressRoute: Result,
    uploadFailedRoute: Result,
    updateFilesList: FileUpload => Seq[T],
    saveFilesList: Seq[T] => Try[UserAnswers]
  )(implicit ec: ExecutionContext): Future[Result] = {
    fileUploadRepository.getRecord(key).flatMap {
      case Some(upload @ FileUpload(_, _, _, Some(FileStatusEnum.READY), _, _, _)) =>
        val updatedListFiles = updateFilesList(upload)
        for {
          updatedAnswers <- Future.fromTry(saveFilesList(updatedListFiles))
          _              <- sessionRepository.set(updatedAnswers)
        } yield uploadCompleteRoute
      case Some(FileUpload(_, _, _, Some(errorStatus), _, _, _)) =>
        val uploadError = asyncErrorToUpscanErrorMapping(errorStatus)
        Future.successful(uploadFailedRoute.flashing("uploadError" -> uploadError.toString))
      case Some(FileUpload(_, _, _, None, _, _, _)) =>
        Future.successful(uploadInProgressRoute)
      case None =>
        Future.successful(InternalServerError)
    }
  }

  def extractFileDetails(doc: FileUpload, key: String): FileUploadInfo = {
    for {
      filename      <- doc.fileName
      downloadUrl   <- doc.downloadUrl
      uploadDetails <- doc.uploadDetails
    } yield {
      FileUploadInfo(
        reference = doc.reference,
        fileName = filename,
        downloadUrl = downloadUrl,
        uploadTimestamp = uploadDetails.uploadTimestamp,
        checksum = uploadDetails.checksum,
        fileMimeType = uploadDetails.fileMimeType
      )
    }
  }.getOrElse(throw new RuntimeException(s"Unable to retrieve file upload details with the ID $key"))

  def buildUpscanError(
    errorCode: Option[String],
    errorMessage: Option[String],
    errorResource: Option[String],
    errorRequestId: Option[String]
  ): Option[UpscanInitiateError] =
    errorCode match {
      case Some(error) =>
        Some(
          UpscanInitiateError(
            error,
            errorMessage.getOrElse("Not supplied"),
            errorResource.getOrElse("Not supplied"),
            errorRequestId.getOrElse("Not supplied")
          )
        )
      case _ => None
    }

}
