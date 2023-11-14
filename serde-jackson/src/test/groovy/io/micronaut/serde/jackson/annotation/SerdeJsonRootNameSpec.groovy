package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonRootNameSpec

class SerdeJsonRootNameSpec extends JsonRootNameSpec {

      void "test deserialize from null value"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonRootName(value = "sampleClass")
record SampleClass(String a, String b) {}
""")

        when:
        def deserNull = jsonMapper.readValue("""{"sampleClass":null}""", argumentOf(context, 'test.SampleClass'))

        then:
        deserNull == null

        cleanup:
        context.close()
    }

    void "test deserialize from {}"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonRootName(value = "sampleClass")
record SampleClass(String a, String b) {}
""")

        when:
        def deserNull2 = jsonMapper.readValue("""{}""", argumentOf(context, 'test.SampleClass'))

        then:
        deserNull2 == null

        cleanup:
        context.close()
    }
}
