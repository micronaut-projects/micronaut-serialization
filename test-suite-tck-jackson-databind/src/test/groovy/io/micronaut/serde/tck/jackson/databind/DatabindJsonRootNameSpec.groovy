package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonRootNameSpec
import spock.lang.PendingFeature

class DatabindJsonRootNameSpec extends JsonRootNameSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of(
                        "jackson.serialization.wrapRootValue", "true",
                        "jackson.deserialization.unwrapRootValue", "true"
                )
        ))
    }

    // Jackson Databind doesn't support serializing from null/empty

    @PendingFeature
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

    @PendingFeature
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
