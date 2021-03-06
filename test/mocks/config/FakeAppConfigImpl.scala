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

///*
// * Copyright 2022 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package mocks.config
//
//import com.google.inject.Singleton
//import config.{AppConfig}
//import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
//
//import javax.inject.Inject
//import mocks.TestRoles
//import play.api.Configuration
//
//@Singleton
//class FakeAppConfigImpl @Inject()(config: Configuration, serviceConfig: ServiceConfig)
//  extends AppConfig(config) {
//
//  override val title = "Unit Test Title"
//
//  override val strideLoginUrl = "https://loginUri"
//  override val gatekeeperSuccessUrl = "http://mock-gatekeeper-frontend/api-gatekeeper/applications"
//
//  override val userRole = TestRoles.userRole
//  override val adminRole = TestRoles.adminRole
//  override val superUserRole = TestRoles.superUserRole
//}
