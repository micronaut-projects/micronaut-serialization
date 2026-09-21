package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonIgnoreSpec

class DatabindJsonIgnoreSpec extends JsonIgnoreSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("jackson.deserialization-features.fail-on-unknown-properties", "true")
        ))
    }

    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return """Unrecognized property "$propertyName" (class $className), not marked as ignorable"""
    }

    @Override
    protected String unknownFieldMessage(String propertyName, String className) {
        return """Unrecognized field "$propertyName" (class $className), not marked as ignorable"""
    }

    void "json ignore on a record parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
record Test(@JsonIgnore @JsonProperty("foo") Ignored foo, @JsonProperty("bar") String bar) {
}
class Ignored {
}
''')

            def des = jsonMapper.readValue('{"foo": {}, "bar": "2"}', typeUnderTest)

        expect:
            des.foo == null
            des.bar == "2"

        cleanup:
            context.close()
    }

}
