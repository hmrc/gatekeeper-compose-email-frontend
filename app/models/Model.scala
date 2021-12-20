/*
 * Copyright 2021 HM Revenue & Customs
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

package models

import org.joda.time.DateTime
import play.api.libs.json.JodaReads._
import play.api.libs.json.JodaWrites._
import play.api.libs.json._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.http.SessionKeys

object GatekeeperRole extends Enumeration {
  type GatekeeperRole = Value
  val USER,SUPERUSER,ADMIN = Value
}

case class BearerToken(authToken: String, expiry: DateTime) {
  override val toString = authToken
}

object BearerToken {
  implicit val format = Json.format[BearerToken]
}

object GatekeeperSessionKeys {
  val LoggedInUser = "LoggedInUser"
  val AuthToken = SessionKeys.authToken
}

case class UpdateGrantLengthRequest(grantLengthInDays: Int)

object UpdateGrantLengthRequest {
  implicit val format = Json.format[UpdateGrantLengthRequest]
}

object PreconditionFailedException extends Throwable

class FetchApplicationsFailed(cause: Throwable) extends Throwable(cause)

object FetchApplicationSubscriptionsFailed extends Throwable

class InconsistentDataState(message: String) extends RuntimeException(message)

object TeamMemberAlreadyExists extends Throwable

object TeamMemberLastAdmin extends Throwable

object ApplicationNotFound extends Throwable

case class ApproveUpliftRequest(gatekeeperUserId: String)

object ApproveUpliftRequest {
  implicit val format = Json.format[ApproveUpliftRequest]
}

sealed trait ApproveUpliftSuccessful

case object ApproveUpliftSuccessful extends ApproveUpliftSuccessful


case class RejectUpliftRequest(gatekeeperUserId: String, reason: String)

object RejectUpliftRequest {
  implicit val format = Json.format[RejectUpliftRequest]
}

sealed trait RejectUpliftSuccessful

case object RejectUpliftSuccessful extends RejectUpliftSuccessful

case class ResendVerificationRequest(gatekeeperUserId: String)

object ResendVerificationRequest {
  implicit val format = Json.format[ResendVerificationRequest]
}

sealed trait ResendVerificationSuccessful

case object ResendVerificationSuccessful extends ResendVerificationSuccessful

object UpliftAction extends Enumeration {
  type UpliftAction = Value
  val APPROVE, REJECT = Value

  def from(action: String): Option[Value] = UpliftAction.values.find(e => e.toString == action.toUpperCase)

  implicit val format = Json.formatEnum(UpliftAction)
}

case class SubmissionDetails(submitterName: String, submitterEmail: String, submittedOn: DateTime)

case class ApprovalDetails(submittedOn: DateTime, approvedBy: String, approvedOn: DateTime)

object SubmissionDetails {
  implicit val format = Json.format[SubmissionDetails]
}

sealed trait UpdateOverridesResult

case object UpdateOverridesSuccessResult extends UpdateOverridesResult

case class UpdateScopesRequest(scopes: Set[String])

object UpdateScopesRequest {
  implicit val format = Json.format[UpdateScopesRequest]
}

sealed trait UpdateScopesResult
case object UpdateScopesSuccessResult extends UpdateScopesResult
case object UpdateScopesInvalidScopesResult extends UpdateScopesResult

case class UpdateIpAllowlistRequest(required: Boolean, allowlist: Set[String])

object UpdateIpAllowlistRequest {
  implicit val format = Json.format[UpdateIpAllowlistRequest]
}

sealed trait UpdateIpAllowlistResult
case object UpdateIpAllowlistSuccessResult extends UpdateIpAllowlistResult

sealed trait ApplicationUpdateResult
case object ApplicationUpdateSuccessResult extends ApplicationUpdateResult
case object ApplicationUpdateFailureResult extends ApplicationUpdateResult


sealed trait ApplicationDeleteResult
case object ApplicationDeleteSuccessResult extends ApplicationDeleteResult
case object ApplicationDeleteFailureResult extends ApplicationDeleteResult

sealed trait ApplicationBlockResult
case object ApplicationBlockSuccessResult extends ApplicationBlockResult
case object ApplicationBlockFailureResult extends ApplicationBlockResult

sealed trait ApplicationUnblockResult
case object ApplicationUnblockSuccessResult extends ApplicationUnblockResult
case object ApplicationUnblockFailureResult extends ApplicationUnblockResult

sealed trait DeveloperDeleteResult
case object DeveloperDeleteSuccessResult extends DeveloperDeleteResult
case object DeveloperDeleteFailureResult extends DeveloperDeleteResult

sealed trait CreatePrivOrROPCAppResult

case object CreatePrivOrROPCAppFailureResult extends CreatePrivOrROPCAppResult



case class ApiScope(key: String, name: String, description: String, confidenceLevel: Option[ConfidenceLevel] = None)
object ApiScope {
  implicit val formats = Json.format[ApiScope]
}

final case class DeleteApplicationForm(applicationNameConfirmation: String, collaboratorEmail: Option[String])
object DeleteApplicationForm {
  implicit val format = Json.format[DeleteApplicationForm]
}

final case class DeleteApplicationRequest(gatekeeperUserId: String, requestedByEmailAddress: String)
object DeleteApplicationRequest {
  implicit val format = Json.format[DeleteApplicationRequest]
}

final case class BlockApplicationForm(applicationNameConfirmation: String)
object BlockApplicationForm {
  implicit val format = Json.format[BlockApplicationForm]
}

final case class UnblockApplicationForm(applicationNameConfirmation: String)
object UnblockApplicationForm {
  implicit val format = Json.format[UnblockApplicationForm]
}

final case class BlockApplicationRequest(gatekeeperUserId: String)
object BlockApplicationRequest {
  implicit val format = Json.format[BlockApplicationRequest]
}

final case class UnblockApplicationRequest(gatekeeperUserId: String)
object UnblockApplicationRequest {
  implicit val format = Json.format[UnblockApplicationRequest]
}

case class DeleteCollaboratorRequest(
  email: String,
  adminsToEmail: Set[String],
  notifyCollaborator: Boolean
)

object DeleteCollaboratorRequest {
  implicit val writesDeleteCollaboratorRequest = Json.writes[DeleteCollaboratorRequest]
}

final case class DeleteDeveloperRequest(gatekeeperUserId: String, emailAddress: String)
object DeleteDeveloperRequest {
  implicit val format = Json.format[DeleteDeveloperRequest]
}

sealed trait FieldsDeleteResult
case object FieldsDeleteSuccessResult extends FieldsDeleteResult
case object FieldsDeleteFailureResult extends FieldsDeleteResult

final case class AddTeamMemberResponse(registeredUser: Boolean)

object AddTeamMemberResponse {
  implicit val format = Json.format[AddTeamMemberResponse]
}

case class ApproveServiceRequest(serviceName: String)

object ApproveServiceRequest {
  implicit val format = Json.format[ApproveServiceRequest]
}

class UpdateApiDefinitionsFailed extends Throwable
