package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonIncludeSpec
import spock.lang.PendingFeature
import spock.lang.Unroll

class SerdeJsonIncludeSpec extends JsonIncludeSpec {

    // Jackson Databind differentiate between missing property and property is null for Optionals

    @Unroll
    void "test optional deserialize #result of type #type"() {
        given:
            def context = buildContext('test.Test', """
package test;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@com.fasterxml.jackson.annotation.JsonClassDescription
class Test {
    private $type value;
    public void setValue($type value) {
        this.value = value;
    }
    public $type getValue() {
        return value;
    }
}
""", data)
        when:
            def bean = jsonMapper.readValue(result, beanUnderTest.class)
        then:
            bean.value == data.value

        cleanup:
            context.close()

        where:
            type               | data                            | result
            "Optional<String>" | [value: Optional.empty()]       | '{}'
            "OptionalInt"      | [value: OptionalInt.empty()]    | '{}'
            "OptionalDouble"   | [value: OptionalDouble.empty()] | '{}'
            "OptionalLong"     | [value: OptionalLong.empty()]   | '{}'
            "Optional<String>" | [value: Optional.empty()]       | '{"value":null}'
            "OptionalInt"      | [value: OptionalInt.empty()]    | '{"value":null}'
            "OptionalDouble"   | [value: OptionalDouble.empty()] | '{"value":null}'
            "OptionalLong"     | [value: OptionalLong.empty()]   | '{"value":null}'
    }

    @PendingFeature(reason = "Databind in a case of @JsonInclude(NON_DEFAULT) on a class also compares the defaults of the empty bean")
    @Unroll
    void "test @JsonInclude(NON_DEFAULT) on class"() {
        given:
            def context = buildContext('test.Test', """
package test;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@Serdeable
@JsonInclude(NON_DEFAULT)
class Test {
    private String value = "abc";
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
""")
        when:
            def bean = newInstance(context, 'test.Test')
            bean.value = value
            String json = writeJson(jsonMapper, bean)
        then:
            json == result

        cleanup:
            context.close()

        where:
            value | result
            null  | """{"value":null}"""
            "abc"  | """{}"""
            "xyz"  | """{"value":"xyz"}"""
    }

}
