package io.micronaut.serde.tck.tests.jacksondatabind;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.serde.tck.tests")
@ExcludeClassNamePatterns({
    // Jackson Databind 3.2 no longer resolves host names, see DatabindInetAddressSpec
    "io.micronaut.serde.tck.tests.InetAddressTest"
})
@SuiteDisplayName("Serialization TCK Jackson Databind")
public class SerializationJacksonDatabindSuite {
}
