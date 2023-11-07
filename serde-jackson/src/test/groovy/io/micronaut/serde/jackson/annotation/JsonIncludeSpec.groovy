package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*

class JsonIncludeSpec extends JsonCompileSpec {

    @Unroll
    void "test @JsonInclude(#include) for #type with #value"() {
        given:
        def context = buildContext("""
package jsoninclude;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import java.util.*;

@Serdeable
record Test(
    @JsonInclude(${include.name()})
    $type test
) {}
""")
        def bean = newInstance(context, 'jsoninclude.Test', value)
        String json = writeJson(jsonMapper, bean)

        expect:
        json == result

        cleanup:
        context.close()

        where:
        include    | type                  | value            | result
        ALWAYS     | "String"              | ""               | '{"test":""}'
        ALWAYS     | "String"              | null             | '{"test":null}'
        ALWAYS     | "String"              | "test"           | '{"test":"test"}'
        NON_NULL   | "String"              | ""               | '{"test":""}'
        NON_NULL   | "String"              | null             | '{}'
        NON_NULL   | "String"              | "test"           | '{"test":"test"}'
        NON_ABSENT | "String"              | ""               | '{"test":""}'
        NON_ABSENT | "String"              | null             | '{}'
        NON_ABSENT | "String"              | "test"           | '{"test":"test"}'
        NON_EMPTY  | "String"              | ""               | '{}'
        NON_EMPTY  | "String"              | null             | '{}'
        NON_EMPTY  | "String"              | "test"           | '{"test":"test"}'

        ALWAYS     | "List<String>"        | []               | '{"test":[]}'
        ALWAYS     | "List<String>"        | null             | '{"test":null}'
        ALWAYS     | "List<String>"        | ["test"]         | '{"test":["test"]}'
        NON_NULL   | "List<String>"        | []               | '{"test":[]}'
        NON_NULL   | "List<String>"        | null             | '{}'
        NON_NULL   | "List<String>"        | ["test"]         | '{"test":["test"]}'
        NON_ABSENT | "List<String>"        | []               | '{"test":[]}'
        NON_ABSENT | "List<String>"        | null             | '{}'
        NON_ABSENT | "List<String>"        | ["test"]         | '{"test":["test"]}'
        NON_EMPTY  | "List<String>"        | []               | '{}'
        NON_EMPTY  | "List<String>"        | null             | '{}'
        NON_EMPTY  | "List<String>"        | ["test"]         | '{"test":["test"]}'

        ALWAYS     | "Map<String, String>" | [:]              | '{"test":{}}'
        ALWAYS     | "Map<String, String>" | null             | '{"test":null}'
        ALWAYS     | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'
        NON_NULL   | "Map<String, String>" | [:]              | '{"test":{}}'
        NON_NULL   | "Map<String, String>" | null             | '{}'
        NON_NULL   | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'
        NON_ABSENT | "Map<String, String>" | [:]              | '{"test":{}}'
        NON_ABSENT | "Map<String, String>" | null             | '{}'
        NON_ABSENT | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'
        NON_EMPTY  | "Map<String, String>" | [:]              | '{}'
        NON_EMPTY  | "Map<String, String>" | null             | '{}'
        NON_EMPTY  | "Map<String, String>" | ["test": "test"] | '{"test":{"test":"test"}}'

    }

    @Unroll
    void "test @JsonInclude(#include) on class for #type with #value"() {
        given:
        def context = buildContext("""
package jsoninclude;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.*;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@Serdeable
@JsonInclude(${include.name()})
record Test(
    $type test
) {}
""")
        def bean = newInstance(context, 'jsoninclude.Test', value)
        String json = writeJson(jsonMapper, bean)

        expect:
        json == result

        cleanup:
        context.close()

        where:
        include    | type                  | value            | result
        ALWAYS     | "String"              | ""               | '{"test":""}'
        ALWAYS     | "String"              | null             | '{"test":null}'
        ALWAYS     | "String"              | "test"           | '{"test":"test"}'
        NON_NULL   | "String"              | ""               | '{"test":""}'
        NON_NULL   | "String"              | null             | '{}'
        NON_NULL   | "String"              | "test"           | '{"test":"test"}'
        NON_ABSENT | "String"              | ""               | '{"test":""}'
        NON_ABSENT | "String"              | null             | '{}'
        NON_ABSENT | "String"              | "test"           | '{"test":"test"}'
        NON_EMPTY  | "String"              | ""               | '{}'
        NON_EMPTY  | "String"              | null             | '{}'
        NON_EMPTY  | "String"              | "test"           | '{"test":"test"}'

    }
}
