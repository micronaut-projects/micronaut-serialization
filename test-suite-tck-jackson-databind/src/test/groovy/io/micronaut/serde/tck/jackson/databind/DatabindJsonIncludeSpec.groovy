package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.JsonIncludeSpec
import spock.lang.Unroll

class DatabindJsonIncludeSpec extends JsonIncludeSpec {

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
            type                           | data                            | result
            "Optional<String>"             | [value: null]                   | '{}'
            "OptionalInt"                  | [value: null]                   | '{}'
            "OptionalDouble"               | [value: null]                   | '{}'
            "OptionalLong"                 | [value: null]                   | '{}'
            "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
    }

}
