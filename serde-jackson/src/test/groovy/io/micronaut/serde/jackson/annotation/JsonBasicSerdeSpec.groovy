package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonCompileSpec

class JsonBasicSerdeSpec extends JsonCompileSpec {

    void "test basic collection type #type with include NON_ABSENT"() {
        given:
        def context = buildContext('test.Test', """
package test;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;

@com.fasterxml.jackson.annotation.JsonClassDescription
class Test {
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private $type value;
    public void setValue($type value) {
        this.value = value;
    } 
    public $type getValue() {
        return value;
    }
}
""", data)
        expect:
        writeJson(jsonMapper, beanUnderTest) == result
        def read = jsonMapper.readValue(result, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == data.value

        cleanup:
        context.close()

        where:
        type                           | data                            | result
        "Optional<String>"             | [value: Optional.empty()]       | '{}'
        "OptionalInt"                  | [value: OptionalInt.empty()]    | '{}'
        "OptionalDouble"               | [value: OptionalDouble.empty()] | '{}'
        "OptionalLong"                 | [value: OptionalLong.empty()]   | '{}'
        "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
        "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
        "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
        "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
        "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
        "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
        "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
        "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
        "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
        "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
        "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'

    }
}
