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

package models.upscan

import models.Enumerable

sealed trait FileStatusEnum

sealed trait FileStatusError extends FileStatusEnum

sealed trait FileStatusSuccess extends FileStatusEnum

object FileStatusEnum extends Enumerable.Implicits[FileStatusEnum] {

  case object READY extends FileStatusSuccess

  case object FAILED extends FileStatusError

  case object FAILED_QUARANTINE extends FileStatusError

  case object FAILED_REJECTED extends FileStatusError

  case object FAILED_UNKNOWN extends FileStatusError

  val values: Seq[FileStatusEnum] = Seq(
    READY,
    FAILED,
    FAILED_QUARANTINE,
    FAILED_REJECTED,
    FAILED_UNKNOWN
  )

  implicit val enumerable: Enumerable[FileStatusEnum] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
