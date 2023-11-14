package io.micronaut.serde.jackson


import spock.lang.Unroll

class JsonPropertySpec extends JsonCompileSpec {

    void "test JsonProperty on private methods"() {
        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    private void setValueInternal(String value) {
        this.value = value.toLowerCase();
    }

    @JsonProperty("value")
    private String getValueInternal() {
        return value.toUpperCase();
    }
}
""", [value: 'test'])
        then:
        def e = thrown(RuntimeException)
        e.message.contains("JSON annotations cannot be used on private methods")
    }

    void "test JsonProperty on protected methods"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    protected void setValueInternal(String value) {
        this.value = value.toLowerCase();
    }

    @JsonProperty("value")
    protected String getValueInternal() {
        return value.toUpperCase();
    }
}
""", [value: 'test'])
        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"value":"TEST"}'

        when:
        def bean =
                jsonMapper.readValue(result, argumentOf(context, 'test.Test'))
        then:
        bean.value == 'test'

        cleanup:
        context.close()
    }

    @Unroll
    void "test invalid defaultValue for #type and value #value"() {

        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(defaultValue = "$value")
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", [:])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid defaultValue [$value] specified")

        where:
        type    | value
        Integer | 'junk'
        URL     | 'ws://junk'
    }

    void "test optional by default primitive field"() {

        given:
        def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private int value = 5;

    public void setValue(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
}
""")

        when:
        def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then:
        bean.value == 5

        cleanup:
        ctx.close()
    }

    void "test optional by default primitive field in constructor"() {

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

    @Unroll
    void "test invalid defaultValue for #type and value #value for records"() {

        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty(defaultValue = "$value")
    $type.name value
) {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid defaultValue [$value] specified")

        where:
        type      | value
        Integer   | 'junk'
        int.class | 'junk'
        URL       | 'ws://junk'
    }

}
