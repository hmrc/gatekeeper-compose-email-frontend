import play.core.PlayVersion
import sbt._

object AppDependencies {

  lazy val bootstrapPlayVersion = "5.23.0"
  lazy val jsoupVersion = "1.13.1"
  lazy val scalaCheckVersion = "1.14.0"

  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  lazy val dependencies = Seq(
    "uk.gov.hmrc"       %%  "bootstrap-frontend-play-28"    % bootstrapPlayVersion,
    "uk.gov.hmrc"       %%  "govuk-template"                % "5.75.0-play-28",
    "uk.gov.hmrc"       %%  "play-ui"                       % "9.8.0-play-28",
    "uk.gov.hmrc"       %%  "play-frontend-hmrc"            % "3.14.0-play-28"
  )

  lazy val testScopes = Seq(Test.name, IntegrationTest.name, "acceptance").mkString(",")

  lazy val testDependencies: Seq[ModuleID] = Seq(
    "org.scalatestplus.play"  %%  "scalatestplus-play"        % "3.1.3",
    "org.pegdown"             %   "pegdown"                   % "1.6.0",
    "org.jsoup"               %   "jsoup"                     % jsoupVersion,
    "com.github.tomakehurst"  %   "wiremock"                  % "1.58",
    "org.seleniumhq.selenium" %   "selenium-java"             % "2.53.1",
    "org.seleniumhq.selenium" %   "selenium-htmlunit-driver"  % "2.52.0",
    "org.mockito"             %%  "mockito-scala-scalatest"   % "1.7.1",
    "org.scalacheck"          %%  "scalacheck"                % scalaCheckVersion,
    "uk.gov.hmrc"             %%  "bootstrap-test-play-28"    % bootstrapPlayVersion,
    "com.vladsch.flexmark"    %   "flexmark-all"              % "0.36.8"
  ).map (_ % testScopes)
}