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

package utils

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import models.UploadInfo._
import models._
import play.api.libs.Files.{TemporaryFile, logger}
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart

import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ErrorAction(redirectUrl: Option[String], key: String)

object UploadProxyController {


  val KeyName = "key"
  val UpscanUrl = "upscan-url"
  val EmailSubject = "emailSubject"
  val EmailBody = "emailBody"

  object TemporaryFilePart {
    private val AdoptedFileSuffix = ".out"

    def partitionTrys[A](trys: Seq[Try[A]]): (Seq[String], Seq[A]) = {
      val failureResults = trys.collect { case Failure(err) => s"${err.getClass.getSimpleName}: ${err.getMessage}" }
      val successResults = trys.collect { case Success(a)   => a }
      (failureResults, successResults)
    }

    def adoptFile(filePart: FilePart[TemporaryFile]): Try[FilePart[Path]] = {
      val inPath  = filePart.ref.path
      val outPath = inPath.resolveSibling(inPath.getFileName)
      Try(filePart.copy(ref = filePart.ref.atomicMoveWithFallback(outPath)))
    }

    def toUploadSource(filePart: FilePart[Path]): FilePart[Source[ByteString, Future[IOResult]]] =
      filePart.copy(ref = FileIO.fromPath(filePart.ref))

    def deleteFile(filePart: FilePart[Path]): Try[Boolean] =
      Try(Files.deleteIfExists(filePart.ref))
  }

  object ErrorResponseHandler {

    def proxyErrorResponse(
                            errorAction: ErrorAction,
                            statusCode: Int,
                            jsonResponseBody: String,
                            responseHeaders: Map[String, Seq[String]]): Option[ErrorResponse] = {
      logger.info(s"JSON Response bits $jsonResponseBody and status is $statusCode")
      Some(jsonErrorFields(jsonResponseBody))
    }

    private def jsonErrorFields(jsonBody: String): ErrorResponse = {
      val json = Json.parse(jsonBody)
      json.as[ErrorResponse]
    }
  }
}