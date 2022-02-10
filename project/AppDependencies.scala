import play.core.PlayVersion
import sbt._

object AppDependencies {

  lazy val slf4jVersion = "1.7.23"
  lazy val logbackVersion = "1.1.10"
  lazy val jsoupVersion = "1.13.1"
  lazy val scalaCheckVersion = "1.14.0"

  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  lazy val dependencies = Seq(
    "uk.gov.hmrc"       %%  "bootstrap-frontend-play-28"    % "5.18.0",
    "uk.gov.hmrc"       %%  "time"                          % "3.11.0",
    "uk.gov.hmrc"       %%  "govuk-template"                % "5.72.0-play-28",
    "uk.gov.hmrc"       %%  "play-ui"                       % "9.7.0-play-28",
    "uk.gov.hmrc"       %%  "play-conditional-form-mapping" % "1.2.0-play-26",
    "uk.gov.hmrc"       %%  "json-encryption"               % "4.8.0-play-26",
    "uk.gov.hmrc"       %%  "play-json-union-formatter"     % "1.11.0",
    "uk.gov.hmrc"       %%  "emailaddress"                  % "3.5.0",
    "uk.gov.hmrc"       %%  "play-frontend-hmrc"            % "1.31.0-play-28",
    "uk.gov.hmrc.mongo" %%  "hmrc-mongo-play-28"            % "0.55.0",
    "uk.gov.hmrc"       %%  "http-metrics"                  % "2.3.0-play-28",
    "commons-net"       %   "commons-net"                   % "3.6",
    "org.slf4j"         %   "slf4j-api"                     % slf4jVersion,
    "org.slf4j"         %   "jcl-over-slf4j"                % slf4jVersion,
    "org.slf4j"         %   "log4j-over-slf4j"              % slf4jVersion,
    "org.slf4j"         %   "jul-to-slf4j"                  % slf4jVersion,
    "ch.qos.logback"    %   "logback-classic"               % logbackVersion,
    "ch.qos.logback"    %   "logback-core"                  % logbackVersion,
    "com.typesafe.play" %%  "play-json"                     % "2.8.1",
    "com.typesafe.play" %%  "play-json-joda"                % "2.8.1",
    "org.typelevel"     %%  "cats-core"                     % "2.3.1",
    "org.apache.httpcomponents" % "httpclient"                 % "4.5.13"
  )

  lazy val testScopes = Seq(Test.name, IntegrationTest.name, "acceptance").mkString(",")

  lazy val testDependencies: Seq[ModuleID] = Seq(
    "org.scalatestplus.play"  %%  "scalatestplus-play"        % "3.1.3",
    "org.pegdown"             %   "pegdown"                   % "1.6.0",
    "org.jsoup"               %   "jsoup"                     % jsoupVersion,
    "com.typesafe.play"       %%  "play-test"                 % PlayVersion.current,
    "com.github.tomakehurst"  %   "wiremock"                  % "1.58",
    "org.seleniumhq.selenium" %   "selenium-java"             % "2.53.1",
    "org.seleniumhq.selenium" %   "selenium-htmlunit-driver"  % "2.52.0",
    "org.mockito"             %%  "mockito-scala-scalatest"   % "1.7.1",
    "org.scalacheck"          %%  "scalacheck"                % scalaCheckVersion,
    "uk.gov.hmrc"             %%  "bootstrap-test-play-28"    % "5.16.0",
    "uk.gov.hmrc.mongo"       %%  "hmrc-mongo-test-play-28"   % "0.55.0",
    "com.vladsch.flexmark"    %   "flexmark-all"              % "0.36.8"
  ).map (_ % testScopes)
}