/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde

import io.micronaut.http.HttpStatus
import spock.lang.Unroll

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.Year
import java.time.ZoneOffset
import java.time.ZonedDateTime

abstract class AbstractBasicSerdeCompileSpec extends AbstractJsonCompileSpec {

    @Unroll
    void "test basic type #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    } 
    public $type.name getValue() {
        return value;
    }
}
""", data)
        expect:
        def bytes = jsonMapper.writeValueAsBytes(beanUnderTest)
        def read = jsonMapper.readValue(bytes, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == data.value

        cleanup:
        context.close()

        where:
        type       | data                                   | result
        BigDecimal | [value: 10.1]                          | '{"value":10.1}'
        BigInteger | [value: BigInteger.valueOf(10l)]       | '{"value":10}'
        String     | [value: "Test"]                        | '{"value":"Test"}'
        boolean    | [value: true]                          | '{"value":true}'
        byte       | [value: 10]                            | '{"value":10}'
        short      | [value: 10]                            | '{"value":10}'
        int        | [value: 10]                            | '{"value":10}'
        long       | [value: 10]                            | '{"value":10}'
        double     | [value: 10.1d]                         | '{"value":10.1}'
        float      | [value: 10.1f]                         | '{"value":10.1}'
        char       | [value: 'a' as char]                   | '{"value":97}'
        //wrappers
        Boolean    | [value: true]                          | '{"value":true}'
        Byte       | [value: 10]                            | '{"value":10}'
        Short      | [value: 10]                            | '{"value":10}'
        Integer    | [value: 10]                            | '{"value":10}'
        Long       | [value: 10]                            | '{"value":10}'
        Double     | [value: 10.1d]                         | '{"value":10.1}'
        Float      | [value: 10.1f]                         | '{"value":10.1}'
        Character  | [value: 'a' as char]                   | '{"value":97}'
        HttpStatus | [value: HttpStatus.ACCEPTED]           | '{"value":"ACCEPTED"}'

        // other common classes
        URI        | [value: URI.create("https://foo.com")] | '{"value":"https://foo.com"}'
        URL        | [value: new URL("https://foo.com")]    | '{"value":"https://foo.com"}'
        Charset    | [value: StandardCharsets.UTF_8]        | '{"value":"UTF-8"}'
        TimeZone   | [value: TimeZone.getTimeZone("GMT")]   | '{"value":"GMT"}'
        Locale     | [value: Locale.CANADA_FRENCH]          | '{"value":"fr-CA"}'
    }

    @Unroll
    void "test basic type #type missing value"() {
        given:
        def context = buildContext("""
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @io.micronaut.core.annotation.Nullable
    private $type value;
    public void setValue($type value) {
        this.value = value;
    } 
    public $type getValue() {
        return value;
    }
}
""")

        def typeUnderTest = argumentOf(context, 'test.Test')

        expect:
        def read = jsonMapper.readValue(jsonAsBytes('{}'), typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == defaultValue

        cleanup:
        context.close()

        where:
        type << ['Integer', 'int', 'int[]']
        defaultValue << [null, 0, null]
    }

    @Unroll
    void "test basic type #type - null value"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    } 
    public $type.name getValue() {
        return value;
    }
}
""", data)
        expect:
        def bytes = jsonMapper.writeValueAsBytes(beanUnderTest)
        def read = jsonMapper.readValue(bytes, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == null

        cleanup:
        context.close()

        where:
        type       | data          | result
        BigDecimal | [value: null] | '{"value":null}'
        BigInteger | [value: null] | '{"value":null}'
        String     | [value: null] | '{"value":null}'
        //wrappers
        Boolean    | [value: null] | '{"value":null}'
        Byte       | [value: null] | '{"value":null}'
        Short      | [value: null] | '{"value":null}'
        Integer    | [value: null] | '{"value":null}'
        Long       | [value: null] | '{"value":null}'
        Double     | [value: null] | '{"value":null}'
        Float      | [value: null] | '{"value":null}'
        Character  | [value: null] | '{"value":null}'
    }

    @Unroll
    void "test basic array type #type with #data"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private $type.componentType.name[] value;
    public void setValue($type.componentType.name[] value) {
        this.value = value;
    } 
    public $type.componentType.name[] getValue() {
        return value;
    }
}
""", data)
        expect:
        def bytes = jsonMapper.writeValueAsBytes(beanUnderTest)
        def bean = jsonMapper.readValue(bytes, argumentOf(context, 'test.Test'))
        bean.value == data.value

        cleanup:
        context.close()

        where:
        type        | data                          | result
        String[]    | [value: ["Test"] as String[]] | '{"value":["Test"]}'
        boolean[]   | [value: [true] as boolean[]]  | '{"value":[true]}'
        byte[]      | [value: [10] as byte[]]       | '{"value":[10]}'
        short[]     | [value: [10] as short[]]      | '{"value":[10]}'
        int[]       | [value: [10] as int[]]        | '{"value":[10]}'
        long[]      | [value: [10] as long[]]       | '{"value":[10]}'
        double[]    | [value: [10.1] as double[]]   | '{"value":[10.1]}'
        float[]     | [value: [10.1] as float[]]    | '{"value":[10.1]}'
        char[]      | [value: ['a'] as char[]]      | '{"value":[97]}'
        //wrappers
        Boolean[]   | [value: [true] as Boolean[]]  | '{"value":[true]}'
        Byte[]      | [value: [10] as Byte[]]       | '{"value":[10]}'
        Short[]     | [value: [10] as Short[]]      | '{"value":[10]}'
        Integer[]   | [value: [10] as Integer[]]    | '{"value":[10]}'
        Long[]      | [value: [10] as Long[]]       | '{"value":[10]}'
        Double[]    | [value: [10.1] as Double[]]   | '{"value":[10.1]}'
        Float[]     | [value: [10.1] as Float[]]    | '{"value":[10.1]}'
        Character[] | [value: ['a'] as Character[]] | '{"value":[97]}'
        // null
        String[]    | [value: null]                 | '{"value":null}'
        boolean[]   | [value: null]                 | '{"value":null}'
        byte[]      | [value: null]                 | '{"value":null}'
        short[]     | [value: null]                 | '{"value":null}'
        int[]       | [value: null]                 | '{"value":null}'
        long[]      | [value: null]                 | '{"value":null}'
        double[]    | [value: null]                 | '{"value":null}'
        float[]     | [value: null]                 | '{"value":null}'
        char[]      | [value: null]                 | '{"value":null}'

    }

    @Unroll
    void "test basic array type #type with arrays and null values"() {
        given:
        def context = buildContext("""
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private $type.componentType.name[] value;
    public void setValue($type.componentType.name[] value) {
        this.value = value;
    } 
    public $type.componentType.name[] getValue() {
        return value;
    }
}
""")
        def bean = jsonMapper.readValue(jsonAsBytes(data), argumentOf(context, 'test.Test'))

        expect:
        bean.value == expected

        cleanup:
        context.close()

        where:
        type      | expected                   | data
        String[]  | ["Test", null] as String[] | '{"value":["Test", null]}'
        boolean[] | [true, false] as boolean[] | '{"value":[true, null]}'
        byte[]    | [10, 0] as byte[]          | '{"value":[10, null]}'
        short[]   | [10, 0] as short[]         | '{"value":[10, null]}'
        int[]     | [10, 0] as int[]           | '{"value":[10, null]}'
        long[]    | [10, 0] as long[]          | '{"value":[10, null]}'
        double[]  | [10.1, 0d] as double[]     | '{"value":[10.1, null]}'
        float[]   | [10.1, 0f] as float[]      | '{"value":[10.1, null]}'
        char[]    | ['a', 0 as char] as char[] | '{"value":[97, null]}'
    }

    @Unroll
    void "test basic collection type #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.http.HttpStatus;
import java.util.*;

@Serdeable
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
        expect:
        def bytes = jsonMapper.writeValueAsBytes(beanUnderTest)
        def read = jsonMapper.readValue(bytes, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == data.value

        cleanup:
        context.close()

        where:
        type                           | data                               | result
        "List<String>"                 | [value: ["Test"]]                  | '{"value":["Test"]}'
        "Optional<String>"             | [value: Optional.of("Test")]       | '{"value":"Test"}'
        "Optional<String>"             | [value: Optional.empty()]          | '{"value":null}'
        "List<? extends CharSequence>" | [value: ["Test"]]                  | '{"value":["Test"]}'
        "List<Boolean>"                | [value: [true]]                    | '{"value":[true]}'
        "Iterable<String>"             | [value: ["Test"]]                  | '{"value":["Test"]}'
        "Iterable<Boolean>"            | [value: [true]]                    | '{"value":[true]}'
        "Set<String>"                  | [value: ["Test"] as Set]           | '{"value":["Test"]}'
        "Set<Boolean>"                 | [value: [true] as Set]             | '{"value":[true]}'
        "Collection<String>"           | [value: ["Test"]]                  | '{"value":["Test"]}'
        "Collection<Boolean>"          | [value: [true]]                    | '{"value":[true]}'
        "Map<String, Boolean>"         | [value: [foo: true]]               | '{"value":{"foo":true}}'
        "EnumSet<HttpStatus>"          | [value: EnumSet.of(HttpStatus.OK)] | '{"value":["OK"]}'
    }

    @Unroll
    void "test java.time #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    } 
    public $type.name getValue() {
        return value;
    }
}
""", [value: value])
        def result = writeJson(jsonMapper, beanUnderTest)
        def read = jsonMapper.readValue(jsonAsBytes(result), typeUnderTest)

        expect:
        typeUnderTest.type.isInstance(read)
        resolver(read.value) == resolver(value)

        cleanup:
        context.close()

        where:
        type          | value                  | resolver
        Instant       | Instant.now()          | { Instant i -> i.toEpochMilli() }
        LocalTime     | LocalTime.now()        | { LocalTime i -> i.toSecondOfDay() }
        LocalDate     | LocalDate.now()        | { LocalDate d -> d }
        LocalDateTime | LocalDateTime.now()    | { LocalDateTime i -> i.toInstant(ZoneOffset.from(ZoneOffset.UTC)).toEpochMilli() }
        ZonedDateTime | ZonedDateTime.now()    | { ZonedDateTime i -> i.toInstant().toEpochMilli() }
        Duration      | Duration.ofSeconds(10) | { Duration d -> d.toSeconds() }
        Period        | Period.ofWeeks(7)      | { Period p -> p }
        Year          | Year.of(2021)          | { Year y -> y }
    }


    byte[] jsonAsBytes(String json) {
        json.getBytes(StandardCharsets.UTF_8)
    }

}
