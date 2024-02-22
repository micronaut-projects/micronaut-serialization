package io.micronaut.serde.jackson.annotation

import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.serde.jackson.JsonPropertySpec
import spock.lang.PendingFeature

class SerdeJsonPropertySpec extends JsonPropertySpec {

    void "test @JsonProperty.Access.READ_ONLY (get only) - constructor"() {
        // Jackson cannot deserialize READ_ONLY as null
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {

    private String value;
    private String ignored;

    @JsonCreator
    public Test(@JsonProperty("value") String value, @JsonProperty(value = "ignored", access = JsonProperty.Access.READ_ONLY) String ignored) {
        this.value = value;
        this.ignored = ignored;
    }

    public String getValue() {
        return this.value;
    }

    public String getIgnored() {
        return this.ignored;
    }

}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test","ignored":"xyz"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == null

        cleanup:
            context.close()
    }

    void "test @JsonProperty.Access.READ_ONLY (get only) - record"() {
        // Jackson cannot deserialize READ_ONLY as null
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String ignored
) {}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test","ignored":"xyz"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == null

        cleanup:
            context.close()
    }

    void "test optional by default primitive field in constructor XXX"() {

        given:
            def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

@Serdeable
class Test {
    private final $type value;

    @com.fasterxml.jackson.annotation.JsonCreator
    Test(@JsonProperty("value") $type value) {
        this.value = value;
    }

    public $type getValue() {
        return value;
    }
}
""")

        when:
            def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then:
            bean.value == value

        cleanup:
            ctx.close()

        where:
            type      | value
            "byte"    | (byte) 0
            "short"   | (short) 0
            "int"     | 0
            "long"    | 0L
            "float"   | 0F
            "double"  | 0D

            "@Nullable Byte"    | null
            "@Nullable Short"   | null
            "@Nullable Integer" | null
            "@Nullable Long"    | null
            "@Nullable Float"   | null
            "@Nullable Double"  | null
    }

    void "implicit creator with parameter names"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;
    public final String bar;

    public Test(String foo, String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42", "bar": "56"}', typeUnderTest)

        expect:
        deserialized.foo == "42"
        deserialized.bar == "56"

        cleanup:
        context.close()
    }

    void "JsonCreator with single parameter of same name"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)

        expect:
        deserialized.foo == "42"

        cleanup:
        context.close()
    }

    @PendingFeature(reason = 'single-parameter json creator. Dont think we should support this, can be done with delegating mode for JsonCreator')
    void "JsonCreator with single parameter of different name"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator
    public Test(String bar) {
        this.foo = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('"42"', typeUnderTest)

        expect:
        deserialized.foo == "42"

        cleanup:
        context.close()
    }

    void "test required primitive field"() {
        given:
        def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(required = true)
    private int value;

    @JsonCreator
    Test(@JsonProperty("value") int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
""")

        when:
        def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then: "Jackson will deserialize a default value"
        def e = thrown(Exception)
        e.message.contains("Unable to deserialize type [test.Test]. Required constructor parameter [int value] at index [0] is not present or is null in the supplied data")

        cleanup:
        ctx.close()
    }

    void "test @JsonProperty on field"() {
        // Jackson is using 'defaultValue' only for documentation
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(value = "other", defaultValue = "default")
    private String value;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }
}
""", [value: 'test'])
        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"other":"test","ignored":false}'

        when:
        def bean = jsonMapper.readValue(result, argumentOf(context, 'test.Test'))
        then:
        bean.ignored == false
        bean.value == 'test'

        when:
        bean = jsonMapper.readValue("{}", argumentOf(context, 'test.Test'))
        then:
        bean.ignored == false
        bean.value == 'default'

        cleanup:
        context.close()

    }

    void "test @JsonProperty records"() {
        // Jackson is using 'defaultValue' only for documentation
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty(value = "other", defaultValue = "default")
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, defaultValue = "false") // Get only
    boolean ignored
) {}
""")
        when:
        def bean = newInstance(context, 'test.Test', "test", false)
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"other":"test","ignored":false}'

        when:
        bean = jsonMapper.readValue('{"other":"test","ignored":true}', argumentOf(context, 'test.Test'))

        then:
        bean.ignored == false
        bean.value == 'test'

        when:
        bean = jsonMapper.readValue('{}', argumentOf(context, 'test.Test'))

        then:
        bean.ignored == false
        bean.value == 'default'

        cleanup:
        context.close()

    }

    void "test @JsonProperty records - invalid default value"() {
        // Jackson is using 'defaultValue' only for documentation
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable(validate = false)
record Test(
    @JsonProperty(value = "other", defaultValue = "default")
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, defaultValue = "junk")
    int number
) {}
""")
        when:
        def bean = newInstance(context, 'test.Test', "test", 10)
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"other":"test","number":10}'

        when:
        jsonMapper.readValue('{}', argumentOf(context, 'test.Test'))

        then:
        def e = thrown(IntrospectionException)
        e.cause.message.contains("Constructor Argument [int number] of type [test.Test] defines an invalid default value")

        cleanup:
        context.close()
    }

}
