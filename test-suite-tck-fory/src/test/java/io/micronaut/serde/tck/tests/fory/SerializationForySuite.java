package io.micronaut.serde.tck.tests.fory;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.serde.tck.tests")
@SuiteDisplayName("Serialization TCK Fory JSON")
public class SerializationForySuite {
}
