package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonIncludeSpec
import spock.lang.Unroll

class SerdeJsonIncludeSpec extends JsonIncludeSpec {

    void "@JsonInclude"() { // TODO: align
        given:
        def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String alwaysString;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String nonNullString;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public String nonAbsentString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String nonEmptyString;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String[] nonEmptyArray;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> nonEmptyList;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> nonAbsentOptionalString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<List<String>> nonEmptyOptionalList;
}
''')
        def with = newInstance(context, 'example.Test')
        with.alwaysString = 'a';
        with.nonNullString = 'a';
        with.nonAbsentString = 'a';
        with.nonEmptyString = 'a';
        with.nonEmptyArray = ['a'];
        with.nonEmptyList = ['a'];
        with.nonAbsentOptionalString = Optional.of('a');
        with.nonEmptyOptionalList = Optional.of(['a']);

        def without = newInstance(context, 'example.Test')
        without.alwaysString = null
        without.nonNullString = null
        without.nonAbsentString = null
        without.nonEmptyString = null
        without.nonEmptyArray = []
        without.nonEmptyList = []
        without.nonAbsentOptionalString = Optional.empty()
        without.nonEmptyOptionalList = Optional.of([])

        expect:
        writeJson(jsonMapper, with) == '{"alwaysString":"a","nonNullString":"a","nonAbsentString":"a","nonEmptyString":"a","nonEmptyArray":["a"],"nonEmptyList":["a"],"nonAbsentOptionalString":"a","nonEmptyOptionalList":["a"]}'
        writeJson(jsonMapper, without) == '{"alwaysString":null}'
    }

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

}
