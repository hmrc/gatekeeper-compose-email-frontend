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
import play.api.data.FormError
import play.api.data.format.Formatter

import scala.util.control.Exception.nonFatalCatch

trait Formatters {

  private[mappings] def stringFormatter(errorKey: String, args: Seq[Any] = Seq.empty): Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] =
      data.get(key) match {
        case None => Left(Seq(FormError(key, errorKey, args)))
        case Some(s) =>
          val sanitisedInput = s.replace("\u0000", "").trim
          if (sanitisedInput.isEmpty) {
            Left(Seq(FormError(key, errorKey, args)))
          } else {
            Right(sanitisedInput)
          }
      }

    override def unbind(key: String, value: String): Map[String, String] =
      Map(key -> value.trim)
  }

  private[mappings] def booleanFormatter(requiredKey: String, invalidKey: String, args: Seq[Any] = Seq.empty): Formatter[Boolean] =
    new Formatter[Boolean] {

      private val baseFormatter = stringFormatter(requiredKey, args)

      override def bind(key: String, data: Map[String, String]) =
        baseFormatter
          .bind(key, data)
          .right.flatMap {
            case "true"  => Right(true)
            case "false" => Right(false)
            case _       => Left(Seq(FormError(key, invalidKey, args)))
          }

      def unbind(key: String, value: Boolean) = Map(key -> value.toString)
    }

  private[mappings] def intFormatter(
    requiredKey: String,
    wholeNumberKey: String,
    nonNumericKey: String,
    args: Seq[String] = Seq.empty
  ): Formatter[Int] =
    new Formatter[Int] {

      val decimalRegexp = """^-?(\d*\.\d*)$"""

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]) =
        baseFormatter
          .bind(key, data)
          .right.map(_.replace(",", "").trim)
          .right.flatMap {
            case s if s.matches(decimalRegexp) =>
              Left(Seq(FormError(key, wholeNumberKey, args)))
            case s =>
              nonFatalCatch
                .either(s.toInt)
                .left.map(_ => Seq(FormError(key, nonNumericKey, args)))
          }

      override def unbind(key: String, value: Int) =
        baseFormatter.unbind(key, value.toString)
    }

  private[mappings] def numericFormatter(
    isCurrency: Boolean = false,
    numDecimalPlaces: Int = 2,
    requiredKey: String,
    invalidDecimalPlacesKey: String,
    nonNumericKey: String,
    args: Seq[String] = Seq.empty
  ): Formatter[BigDecimal] =
    new Formatter[BigDecimal] {

      val correctDecimalPlaces = """(^-?\d*$)|(^-?\d*\.\d{1,""" + numDecimalPlaces + """}$)"""
      val validNumeric         = """(^-?\d*$)|(^-?\d*\.\d*$)"""

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]) = {

        val numericFormatter = stringFormatter(requiredKey).bind(key, data)
          .right.map(_.replace(",", ""))
          .right.map(_.replaceAll("\\s", ""))

        val currencyFormatter = numericFormatter
          .right.map(_.replace("£", ""))

        val validation: PartialFunction[String, Either[Seq[FormError], BigDecimal]] = {
          case s if !s.matches(validNumeric) =>
            Left(Seq(FormError(key, nonNumericKey, args)))
          case s if !s.matches(correctDecimalPlaces) =>
            Left(Seq(FormError(key, invalidDecimalPlacesKey, args)))
          case s =>
            nonFatalCatch
              .either(BigDecimal(s))
              .left.map(_ => Seq(FormError(key, nonNumericKey, args)))
        }

        if (isCurrency) {
          currencyFormatter
            .right.flatMap(validation)
        } else {
          numericFormatter
            .right.flatMap(validation)
        }
      }

      override def unbind(key: String, value: BigDecimal) =
        baseFormatter.unbind(key, value.toString)
    }

  private[mappings] def enumerableFormatter[A](requiredKey: String, invalidKey: String)(implicit
    ev: Enumerable[A]
  ): Formatter[A] =
    new Formatter[A] {

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
        baseFormatter.bind(key, data).right.flatMap { str =>
          ev.withName(str).map(Right.apply).getOrElse(Left(Seq(FormError(key, invalidKey))))
        }

      override def unbind(key: String, value: A): Map[String, String] =
        baseFormatter.unbind(key, value.toString)
    }

  private[mappings] def foreignCurrencyFormatter(requiredKey: String, invalidKey: String): Formatter[String] =
    new Formatter[String] {

      private val baseFormatter = stringFormatter(requiredKey)
      val valid2dpCurrency      = """(^-?\d*$)|(^-?\d*\.\d{1,2}$)"""
      val countryCurrencyRegex  = """^[a-zA-Z]{3}$"""

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] =
        baseFormatter.bind(key, data).right.flatMap { str =>
          val input = str.replaceAll("\\s", "").toUpperCase
          if (input.take(3).matches(countryCurrencyRegex) && input.drop(3).matches(valid2dpCurrency)) {
            Right(input)
          } else {
            Left(Seq(FormError(key, invalidKey)))
          }
        }

      override def unbind(key: String, value: String): Map[String, String] =
        baseFormatter.unbind(key, value)
    }

}
