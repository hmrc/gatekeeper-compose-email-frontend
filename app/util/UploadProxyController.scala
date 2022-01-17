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

package util

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import models.UploadInfo._
import models._
import org.apache.http.client.utils.URIBuilder
import play.api.libs.Files.{TemporaryFile, logger}
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Result, Results}
import play.api.mvc.Results.Redirect

import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

case class ErrorAction(redirectUrl: Option[String], key: String)

object UploadProxyController {


  val KeyName = "key"
  val UpscanUrl = "upscan-url"
  val EmailRecipient = "emailRecipient"
  val EmailSubject = "emailSubject"
  val EmailBody = "emailBody"

  object TemporaryFilePart {
    private val AdoptedFileSuffix = ".out"

    def partitionTrys[A](trys: Seq[Try[A]]): (Seq[String], Seq[A]) = {
      val failureResults = trys.collect { case Failure(err) => s"${err.getClass.getSimpleName}: ${err.getMessage}" }
      val successResults = trys.collect { case Success(a)   => a }
      (failureResults, successResults)
    }

    /*
     * Play creates a TemporaryFile as the MultipartFormData is parsed.
     * The file is "owned" by Play, is scoped to the request, and is subject to being deleted by a finalizer thread.
     * We "adopt" the file by moving it.
     * This gives us control of its lifetime at the expense of taking responsibility for cleaning it up.  If our
     * cleanup fails, another cleanup will be attempted on shutDown by virtue of the fact that we have not moved
     * the file outside of the playtempXXX folder.
     *
     * See: https://www.playframework.com/documentation/2.7.x/ScalaFileUpload#Uploading-files-in-a-form-using-multipart/form-data
     * See: play.api.libs.Files$DefaultTemporaryFileCreator's FinalizableReferenceQueue & stopHook
     */
    def adoptFile(filePart: FilePart[TemporaryFile]): Try[FilePart[Path]] = {
      val inPath  = filePart.ref.path
      val outPath = inPath.resolveSibling(inPath.getFileName + AdoptedFileSuffix)
      Try(filePart.copy(ref = filePart.ref.atomicMoveWithFallback(outPath)))
    }

    def toUploadSource(filePart: FilePart[Path]): FilePart[Source[ByteString, Future[IOResult]]] =
      filePart.copy(ref = FileIO.fromPath(filePart.ref))

    def deleteFile(filePart: FilePart[Path]): Try[Boolean] =
      Try(Files.deleteIfExists(filePart.ref))
  }



  object ErrorResponseHandler {

    private val MessageField = "Message"

    //    def errorResponse(errorAction: ErrorAction, message: String): Result =
    //      asErrorResult(errorAction, Status.INTERNAL_SERVER_ERROR, Map(fieldName(MessageField) -> message))
    //
    //    def okResponse(errorAction: ErrorAction, message: String): Result =
    //      asErrorResult(errorAction, Status.OK, Map(fieldName(MessageField) -> message))

    def proxyErrorResponse(
                            errorAction: ErrorAction,
                            statusCode: Int,
                            jsonResponseBody: String,
                            responseHeaders: Map[String, Seq[String]]): Option[ErrorResponse] = {
      logger.info(s"JSON Response bits $jsonResponseBody")
      Some(jsonErrorFields(jsonResponseBody))
      //asErrorResult(errorAction, statusCode, jsonErrorFields(jsonResponseBody), responseHeaders)
    }

    //    private def asErrorResult(
    //                               errorAction: ErrorAction,
    //                               statusCode: Int,
    //                               errorFields: ErrorResponse,
    //                               responseHeaders: Map[String, Seq[String]] = Map.empty): Result = {
    //      val resultFields     = errorFields + (KeyName -> errorAction.key)
    //      val exposableHeaders = responseHeaders.filter { case (name, _) => isExposableResponseHeader(name) }
    //      logger.info(s"About to redirect to ${errorAction.redirectUrl}")
    //      errorAction.redirectUrl
    //        .fold(ifEmpty = jsonResult(statusCode, resultFields)) { redirectUrl =>
    //          redirectResult(redirectUrl, queryParams = responseHeaders)
    //        }
    //        .withHeaders(asTuples(exposableHeaders): _*)
    //    }

    /*
     * This is a dummy placeholder to draw attention to the fact that filtering of error response headers is
     * required in the real implementation.  We currently retain only CORS-related headers and custom Amazon
     * headers.  The implementation in this stub differs.  This stub has a CORS filter (the real implementation
     * does not), and our UploadController does not add any fake Amazon headers - so we actually have nothing
     * to do here ...
     */
    private def isExposableResponseHeader(name: String): Boolean = false

    private def jsonResult(statusCode: Int, fields: Map[String, String]): Result =
      Results.Status(statusCode)(Json.toJsObject(fields))

    private def redirectResult(url: String, queryParams: Map[String, Seq[String]]): Result = {
      logger.info(s"****>>>> redirectResult with $url")
      val urlBuilder = queryParams.foldLeft(new URIBuilder(url)) { (urlBuilder, param) =>
        urlBuilder.addParameter(param._1, param._2.head)
      }
      logger.info(s">>>>>*****URL for error redirection is ${urlBuilder.build().toASCIIString} and query params are ${queryParams}")
      Redirect(urlBuilder.build().toASCIIString)
    }

    private def jsonErrorFields(jsonBody: String): ErrorResponse = {
      val json = Json.parse(jsonBody)
      json.as[ErrorResponse]
    }


    private def makeOptionalField(elemType: String, xml: Elem): Option[(String, String)] =
      (xml \ elemType).headOption.map(node => fieldName(elemType) -> node.text)

    private def fieldName(elemType: String): String = s"error$elemType"
  }

  def asTuples(values: Map[String, Seq[String]]): Seq[(String, String)] =
    values.toList.flatMap { case (h, v) => v.map((h, _)) }
}

