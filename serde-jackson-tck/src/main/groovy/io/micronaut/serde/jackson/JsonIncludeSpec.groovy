/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.jackson


import spock.lang.Unroll

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

abstract class JsonIncludeSpec extends JsonCompileSpec {

    @Unroll
    void "test basic deserialize #result of type #type"() {
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
            "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
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
            "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            "Collection<Boolean>"          | [value: null]                   | '{}'
            "Map<String, Boolean>"         | [value: null]                   | '{}'
    }

    @Unroll
    void "test serialize #data of type #type with include #include"() {
        given:
            def context = buildContext('test.Test', """
package test;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;

@com.fasterxml.jackson.annotation.JsonClassDescription
class Test {
    @JsonInclude(${include.name()})
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
            def bean = newInstance(context, "test.Test")
            bean.value = data.value
            def json = jsonMapper.writeValueAsString(bean)
        then:
            json == result

        cleanup:
            context.close()

        where:
            include    | type                           | data                            | result
            NON_ABSENT | "Optional<String>"             | [value: null]                   | '{}'
            NON_ABSENT | "OptionalInt"                  | [value: null]                   | '{}'
            NON_ABSENT | "OptionalDouble"               | [value: null]                   | '{}'
            NON_ABSENT | "OptionalLong"                 | [value: null]                   | '{}'
            NON_ABSENT | "Optional<String>"             | [value: Optional.empty()]       | '{}'
            NON_ABSENT | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{}'
            NON_ABSENT | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{}'
            NON_ABSENT | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{}'
            NON_ABSENT | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            NON_ABSENT | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            NON_ABSENT | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            NON_ABSENT | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            NON_ABSENT | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            NON_ABSENT | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_ABSENT | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            NON_ABSENT | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            NON_ABSENT | "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            NON_ABSENT | "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            NON_ABSENT | "Collection<Boolean>"          | [value: null]                   | '{}'
            NON_ABSENT | "Map<String, Boolean>"         | [value: null]                   | '{}'

            NON_NULL   | "Optional<String>"             | [value: null]                   | '{}'
            NON_NULL   | "OptionalInt"                  | [value: null]                   | '{}'
            NON_NULL   | "OptionalDouble"               | [value: null]                   | '{}'
            NON_NULL   | "OptionalLong"                 | [value: null]                   | '{}'
            NON_NULL   | "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            NON_NULL   | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            NON_NULL   | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            NON_NULL   | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            NON_NULL   | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            NON_NULL   | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            NON_NULL   | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            NON_NULL   | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            NON_NULL   | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            NON_NULL   | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_NULL   | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            NON_NULL   | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            NON_NULL   | "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            NON_NULL   | "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            NON_NULL   | "Collection<Boolean>"          | [value: null]                   | '{}'
            NON_NULL   | "Map<String, Boolean>"         | [value: null]                   | '{}'

            NON_EMPTY  | "Optional<String>"             | [value: null]                   | '{}'
            NON_EMPTY  | "OptionalInt"                  | [value: null]                   | '{}'
            NON_EMPTY  | "OptionalDouble"               | [value: null]                   | '{}'
            NON_EMPTY  | "OptionalLong"                 | [value: null]                   | '{}'
            NON_EMPTY  | "Optional<String>"             | [value: Optional.empty()]       | '{}'
            NON_EMPTY  | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{}'
            NON_EMPTY  | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{}'
            NON_EMPTY  | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{}'
            NON_EMPTY  | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            NON_EMPTY  | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            NON_EMPTY  | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            NON_EMPTY  | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            NON_EMPTY  | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            NON_EMPTY  | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            NON_EMPTY  | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            NON_EMPTY  | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            NON_EMPTY  | "Collection<Boolean>"          | [value: []]                     | '{}'
            NON_EMPTY  | "Map<String, Boolean>"         | [value: [:]]                    | '{}'
            NON_EMPTY  | "Collection<Boolean>"          | [value: null]                   | '{}'
            NON_EMPTY  | "Map<String, Boolean>"         | [value: null]                   | '{}'

            ALWAYS     | "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            ALWAYS     | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            ALWAYS     | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            ALWAYS     | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            ALWAYS     | "Optional<String>"             | [value: Optional.empty()]       | '{"value":null}'
            ALWAYS     | "OptionalInt"                  | [value: OptionalInt.empty()]    | '{"value":null}'
            ALWAYS     | "OptionalDouble"               | [value: OptionalDouble.empty()] | '{"value":null}'
            ALWAYS     | "OptionalLong"                 | [value: OptionalLong.empty()]   | '{"value":null}'
            ALWAYS     | "List<String>"                 | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "Optional<String>"             | [value: Optional.of("Test")]    | '{"value":"Test"}'
            ALWAYS     | "List<? extends CharSequence>" | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "List<Boolean>"                | [value: [true]]                 | '{"value":[true]}'
            ALWAYS     | "Iterable<String>"             | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "Iterable<Boolean>"            | [value: [true]]                 | '{"value":[true]}'
            ALWAYS     | "Set<String>"                  | [value: ["Test"] as Set]        | '{"value":["Test"]}'
            ALWAYS     | "Set<Boolean>"                 | [value: [true] as Set]          | '{"value":[true]}'
            ALWAYS     | "Collection<String>"           | [value: ["Test"]]               | '{"value":["Test"]}'
            ALWAYS     | "Collection<Boolean>"          | [value: [true]]                 | '{"value":[true]}'
            ALWAYS     | "Map<String, Boolean>"         | [value: [foo: true]]            | '{"value":{"foo":true}}'
            ALWAYS     | "Collection<Boolean>"          | [value: []]                     | '{"value":[]}'
            ALWAYS     | "Map<String, Boolean>"         | [value: [:]]                    | '{"value":{}}'
            ALWAYS     | "Collection<Boolean>"          | [value: null]                   | '{"value":null}'
            ALWAYS     | "Map<String, Boolean>"         | [value: null]                   | '{"value":null}'
    }

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
