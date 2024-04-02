/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.http.server.tck.netty;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@ExcludeClassNamePatterns({
        "io.micronaut.http.server.tck.tests.hateoas.JsonErrorTest",
        "io.micronaut.http.server.tck.tests.hateoas.VndErrorTest",
        "io.micronaut.http.server.tck.tests.hateoas.JsonErrorSerdeTest",
    "io.micronaut.http.server.tck.tests.constraintshandler.ControllerConstraintHandlerTest",
    "io.micronaut.http.server.tck.tests.forms.FormsInputNumberOptionalTest",
    "io.micronaut.http.server.tck.tests.forms.FormsSubmissionsWithListsTest",
    "io.micronaut.http.server.tck.tests.staticresources.StaticResourceTest" // Graal fails to see /assets from the TCK as a resource
})
@SelectPackages({
        "io.micronaut.http.server.tck.tests",
        "io.micronaut.serde.http.server.tck.netty",
})
@SuiteDisplayName("HTTP Server TCK for Netty")
public class NettyHttpServerTestSuite {
}
