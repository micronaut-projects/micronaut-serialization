package io.micronaut.serde.tck.tests.serde;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@ExcludeClassNamePatterns(
    "io.micronaut.serde.tck.tests.bytebuffer.ByteBufferNativeTest"
)
@SelectPackages("io.micronaut.serde.tck.tests")
@SuiteDisplayName("Serialization TCK Serde")
public class SerializationSerdeSuite {
}
