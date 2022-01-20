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

import controllers.ComposeEmailForm
import utils.UploadProxyController._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.{MultipartFormData, Result, Results}
import utils.ErrorAction
import org.apache.http.client.utils.URIBuilder

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Try

object MultipartFormExtractor {

  private val missingKey = Results.BadRequest(
    Json.parse("""{"message":"Could not find key field in request"}""")
  )
  private val missingUpscanUrl = Results.BadRequest(
    Json.parse("""{"message":"Could not find upscan_url field in request"}""")
  )
  private val badRedirectUrl = Results.BadRequest(
    Json.parse("""{"message":"Unable to build valid redirect URL for error action"}""")
  )

  def extractErrorAction(multipartFormData: MultipartFormData[TemporaryFile]): Either[Result, ErrorAction] = {
    val maybeErrorActionRedirect = extractErrorActionRedirect(multipartFormData)
    extractKey(multipartFormData).flatMap { key =>
      maybeErrorActionRedirect
        .map { errorActionRedirect =>
          validateErrorActionRedirectUrl(errorActionRedirect, key).map(_ =>
            ErrorAction(maybeErrorActionRedirect, key))
        }
        .getOrElse(Right(ErrorAction(None, key)))
    }
  }

  private def extractErrorActionRedirect(multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
    extractSingletonFormValue("error_action_redirect", multiPartFormData)

  def extractKey(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
    extractSingletonFormValue(KeyName, multiPartFormData).toRight(left = missingKey)

  def extractUpscanUrl(multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
    extractSingletonFormValue(UpscanUrl, multiPartFormData)

  def extractComposeEmailForm(multiPartFormData: MultipartFormData[TemporaryFile]): ComposeEmailForm = {
    val to = extractSingletonFormValue(EmailRecipient, multiPartFormData)
    val subject = extractSingletonFormValue(EmailSubject, multiPartFormData)
    val body = extractSingletonFormValue(EmailBody, multiPartFormData)
    ComposeEmailForm(to.get, subject.get, body.get)
  }
  private def extractSingletonFormValue(
                                         key: String,
                                         multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
    multiPartFormData.dataParts
      .get(key)
      .flatMap(_.headOption)

  private def validateErrorActionRedirectUrl(redirectUrl: String, key: String): Either[Result, URI] =
    Try {
      new URIBuilder(redirectUrl, UTF_8).addParameter("key", key).build()
    }.toOption.toRight(left = badRedirectUrl)
}