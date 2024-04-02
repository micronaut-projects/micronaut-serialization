package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonIgnoreSpec
import spock.lang.PendingFeature

class DatabindJsonIgnoreSpec extends JsonIgnoreSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("jackson.deserialization.failOnUnknownProperties", "true")
        ))
    }

    @Override
    protected String unknownPropertyMessage(String propertyName, String className) {
        return """Unrecognized field "$propertyName" (class $className), not marked as ignorable"""
    }

    @PendingFeature(reason = "Jackson doesn't support ignored record values")
    void "json ignore on a record parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
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

    @PendingFeature(reason = "Jackson doesn't support ignored constructor values")
    void "json ignore on a constructor parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
class Test{
    @JsonIgnore
    private final Ignored foo;
    private final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") Ignored foo, @JsonProperty("bar") String bar) {
        this.foo = foo;
        this.bar = bar;
    }

    public example.Ignored getFoo() {
        return foo;
    }

    public String getBar() {
        return bar;
    }

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
