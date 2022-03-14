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

package forms.mappings

import models.Enumerable
import play.api.data.FieldMapping
import play.api.data.Forms.of

import java.time.LocalDate

trait Mappings extends Formatters with Constraints {

  protected def text(errorKey: String = "error.required", args: Seq[Any] = Seq.empty): FieldMapping[String] =
    of(stringFormatter(errorKey, args))

  protected def int(
    requiredKey: String = "error.required",
    wholeNumberKey: String = "error.wholeNumber",
    nonNumericKey: String = "error.nonNumeric"
  ): FieldMapping[Int] =
    of(intFormatter(requiredKey, wholeNumberKey, nonNumericKey))

  protected def numeric(
    isCurrency: Boolean = false,
    numDecimalPlaces: Int = 2,
    requiredKey: String = "error.required",
    invalidDecimalPlacesKey: String = "error.invalidNumeric",
    nonNumericKey: String = "error.nonNumeric"
  ): FieldMapping[BigDecimal] =
    if (isCurrency) {
      of(numericFormatter(isCurrency = true, numDecimalPlaces, requiredKey, invalidDecimalPlacesKey, nonNumericKey))
    } else {
      of(numericFormatter(isCurrency = false, numDecimalPlaces, requiredKey, invalidDecimalPlacesKey, nonNumericKey))
    }

  protected def boolean(
    requiredKey: String = "error.required",
    invalidKey: String = "error.boolean",
    args: Seq[Any] = Seq.empty
  ): FieldMapping[Boolean] =
    of(booleanFormatter(requiredKey, invalidKey, args))

  // scalastyle:off
  protected def localDate(
    invalidKey: String,
    allRequiredKey: String,
    twoRequiredKey: String,
    requiredKey: String,
    dayMonthLengthKey: String = "error.date.length",
    yearLengthKey: String = "error.year.length",
    validatePastKey: Option[String] = None,
    validateAfterKey: Option[String] = None,
    args: Seq[String] = Seq.empty
  ): FieldMapping[LocalDate] =
    of(
      new LocalDateFormatter(
        invalidKey,
        allRequiredKey,
        twoRequiredKey,
        requiredKey,
        dayMonthLengthKey,
        yearLengthKey,
        validatePastKey,
        validateAfterKey,
        args
      )
    )
  // scalastyle:on

  protected def enumerable[A](requiredKey: String = "error.required", invalidKey: String = "error.invalid")(implicit
    ev: Enumerable[A]
  ): FieldMapping[A] =
    of(enumerableFormatter[A](requiredKey, invalidKey))

  protected def foreignCurrency(
    requiredKey: String = "error.required",
    invalidNumeric: String = "error.invalid"
  ): FieldMapping[String] =
    of(foreignCurrencyFormatter(requiredKey, invalidNumeric))

}
