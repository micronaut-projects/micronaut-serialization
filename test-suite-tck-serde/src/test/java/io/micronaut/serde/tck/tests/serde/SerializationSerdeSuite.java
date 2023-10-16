package io.micronaut.serde.tck.tests.serde;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@ExcludeClassNamePatterns(
    "io.micronaut.serde.tck.tests.bytebuffer.ByteBufferNativeTest" // https://github.com/FasterXML/jackson-databind/blob/2.16/src/main/java/com/fasterxml/jackson/databind/ser/std/ByteBufferSerializer.java#L27-L35
)
@SelectPackages("io.micronaut.serde.tck.tests")
@SuiteDisplayName("Serialization TCK Serde")
public class SerializationSerdeSuite {
}
