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
import models.upscan._
import models.upscan.UpscanErrors._
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Result
import play.shaded.ahc.io.netty.handler.codec.base64.Base64Decoder
import services.ComposeEmailService
import uk.gov.hmrc.http.HeaderCarrier
import utils.ApplicationLogger

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait FileUploadHandler[T] extends ApplicationLogger{

  val appConfig: AppConfig
  val fileUploadConnector: GatekeeperEmailFileUploadConnector
  val emailService: ComposeEmailService
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
  )(ec: ExecutionContext): Future[Result] = (key, error) match {
    case (Some(key), None) =>
        logger.info(s"**************No Errors in Upscan init response for key : $key")
        Future.successful(successRoute)
//      }
    case (_, Some(error)) =>
      logger.info(s"************Errors in Upscan init response for key : $key")
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
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    fileUploadConnector.fetchFileuploadStatus(key).flatMap {
      case UploadInfo(key, emailUUID, UploadedSuccessfully(_, _, _, _, _))  =>
        logger.info("************* handleUpscanFileProcessing Success Case")
        //save to email repository the file ref here.. as this file is successful..
        val attachAppended = emailService.fetchEmail(emailUUID).map( emailInfo =>
          if(emailInfo.attachmentDetails.isDefined) {
            emailInfo.copy(attachmentDetails = Some(emailInfo.attachmentDetails.get :+ key))
          } else emailInfo
        )
        attachAppended.flatMap(email => emailService.updateEmail(ComposeEmailForm(
          new String(Base64.getDecoder.decode(email.markdownEmailBody), Charsets.UTF_8),
          email.subject, true), emailUUID, email.recipients, email.attachmentDetails))

          Future.successful(uploadCompleteRoute)
      case UploadInfo(_, _, UploadedFailedWithErrors(_, _, _, _)) =>
        logger.info("************* handleUpscanFileProcessing Failure Case")
        Future.successful(uploadFailedRoute.flashing("uploadError" -> "Quarantined"))
      case UploadInfo(_, _, InProgress) =>
        logger.info("************* handleUpscanFileProcessing InProgress Case")
        Future.successful(uploadInProgressRoute)
      case _ =>
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
