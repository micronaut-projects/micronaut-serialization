package io.micronaut.serde.tck.jackson.databind

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonIgnoreSpec
import spock.lang.PendingFeature

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

    @PendingFeature(reason = "Jackson doesn't support ignored constructor values")
    void "json ignore on a constructor parameter"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
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

    void "json ignore on an overridden getter doesn't ignore the explicitly named creator parameter with the same name"() {
        given: 'unlike Micronaut Serialization, Jackson only drops the ignored accessor of an explicitly included property'
            def context = buildContext('example.Sub', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
abstract class Base {
    private String x;

    @JsonProperty("x")
    public String getX() {
        return x;
    }

    @JsonProperty("x")
    public final void setX(String x) {
        this.x = x;
    }
}

@Serdeable
final class Sub extends Base {
    private final String creatorValue;

    @JsonCreator
    Sub(@JsonProperty("x") String x) {
        this.creatorValue = x;
    }

    public String creatorValue() {
        return creatorValue;
    }

    @Override
    @JsonIgnore
    public String getX() {
        return super.getX();
    }
}
''')

        when:
            def des = jsonMapper.readValue('{"x":"value"}', typeUnderTest)

        then:
            des.creatorValue() == "value"

        cleanup:
            context.close()
    }

}
