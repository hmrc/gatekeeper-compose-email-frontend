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

import play.api.data.FormError
import play.api.data.format.Formatter

import java.time.LocalDate
import scala.util.{Failure, Success, Try}

private[mappings] class LocalDateFormatter(
  invalidKey: String,
  allRequiredKey: String,
  twoRequiredKey: String,
  requiredKey: String,
  dayMonthLengthKey: String,
  yearLengthKey: String,
  validatePastKey: Option[String],
  validateAfterKey: Option[String],
  args: Seq[String] = Seq.empty
) extends Formatter[LocalDate]
    with Formatters
    with Constraints {

  private val fieldKeys: List[String] = List("day", "month", "year")

  private val yearLength        = 4
  private val dayMonthLengthMax = 2

  private def toDate(key: String, day: Int, month: Int, year: Int): Either[Seq[FormError], LocalDate] =
    Try(LocalDate.of(year, month, day)) match {
      case Success(date) =>
        Right(date)
      case Failure(_) =>
        Left(Seq(FormError(s"$key.day", invalidKey, fieldKeys ++ args)))
    }

  private def validateDate(key: String, date: LocalDate): Either[Seq[FormError], LocalDate] = {
    if (validatePastKey.isDefined) {
      if (date.isAfter(LocalDate.now)) {
        Left(List(FormError(s"$key.day", validatePastKey.get, fieldKeys)))
      } else if (LocalDate.of(1900, 1, 2).isAfter(date)) {
        Left(List(FormError(s"$key.day", validateAfterKey.get, fieldKeys)))
      } else {
        Right(date)
      }
    } else {
      Right(date)
    }
  }

  override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], LocalDate] = {
    val sanitizedFields = data.mapValues(filter).filter(_._2.nonEmpty)
    val missingKeys     = fieldKeys.toSet -- sanitizedFields.keys.map(_.stripPrefix(s"$key."))

    def int(field: String, validate: Int => Boolean, errorKey: String) = {
      val formatter = intFormatter(
        requiredKey = invalidKey,
        wholeNumberKey = invalidKey,
        nonNumericKey = invalidKey,
        args :+ field
      )
      formatter
        .bind(s"$key.$field", sanitizedFields)
        .filterOrElse(validate, Seq(FormError(s"$key.$field", errorKey, Seq(field))))
    }

    val day   = int("day", d => d > 0 && d.toString.length <= dayMonthLengthMax, dayMonthLengthKey)
    val month = int("month", m => m > 0 && m.toString.length <= dayMonthLengthMax, dayMonthLengthKey)
    val year  = int("year", y => y > 0 && y.toString.length == yearLength, yearLengthKey)

    missingKeys.size match {
      case 0 =>
        (day, month, year) match {
          case (Right(d), Right(m), Right(y)) =>
            toDate(key, d, m, y).flatMap(validateDate(key, _))
          case (d, m, y) =>
            val errors = List(d, m, y).flatMap(_.left.toSeq.flatten)
            Left(List(errors.head.copy(args = errors.flatMap(_.args))))
        }
      case 1 =>
        Left(List(FormError(s"$key.${missingKeys.head}", requiredKey, missingKeys.toSeq ++ args)))
      case 2 =>
        Left(List(FormError(s"$key.${missingKeys.head}", twoRequiredKey, missingKeys.toSeq ++ args)))
      case _ =>
        Left(List(FormError(s"$key.day", allRequiredKey, fieldKeys ++ args)))
    }
  }

  override def unbind(key: String, value: LocalDate): Map[String, String] =
    Map(
      s"$key.day"   -> value.getDayOfMonth.toString,
      s"$key.month" -> value.getMonthValue.toString,
      s"$key.year"  -> value.getYear.toString
    )
}
