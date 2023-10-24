package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonCompileSpec

class JsonRootNameSpec extends JsonCompileSpec {

    void "test basic JsonRootName"() {
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
        def instance = newInstance(context, 'test.SampleClass', "xyz", "abc")
        def json = writeJson(jsonMapper, instance)

        then:
        json == """{"sampleClass":{"a":"xyz","b":"abc"}}"""

        when:
        def deser = jsonMapper.readValue(json, argumentOf(context, 'test.SampleClass'))

        then:
        deser.a == "xyz"
        deser.b == "abc"

        when:
        def deserNull = jsonMapper.readValue("""{"sampleClass":null}""", argumentOf(context, 'test.SampleClass'))

        then:
        deserNull == null

        when:
        def deserNull2 = jsonMapper.readValue("""{}""", argumentOf(context, 'test.SampleClass'))

        then:
        deserNull2 == null

        cleanup:
        context.close()
    }
}
