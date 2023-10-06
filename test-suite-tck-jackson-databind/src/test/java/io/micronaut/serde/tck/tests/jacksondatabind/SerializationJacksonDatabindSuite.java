package io.micronaut.serde.tck.tests.jacksondatabind;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.serde.tck.tests")
@SuiteDisplayName("Serialization TCK Jackson Databind")
public class SerializationJacksonDatabindSuite {
}
