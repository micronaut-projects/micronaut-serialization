package io.micronaut.serde.jackson.object

import io.micronaut.http.HttpStatus
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ObjectSerializerCompileSpec extends JsonCompileSpec {

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
        writeJson(jsonMapper, beanUnderTest) == result
        def read = jsonMapper.readValue(result, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == data.value

        cleanup:
        context.close()

        where:
        type       | data                                  | result
        BigDecimal | [value: 10.1]                         | '{"value":10.1}'
        BigInteger | [value: BigInteger.valueOf(10l)]      | '{"value":10}'
        String     | [value: "Test"]                       | '{"value":"Test"}'
        boolean    | [value: true]                         | '{"value":true}'
        byte       | [value: 10]                           | '{"value":10}'
        short      | [value: 10]                           | '{"value":10}'
        int        | [value: 10]                           | '{"value":10}'
        long       | [value: 10]                           | '{"value":10}'
        double     | [value: 10.1d]                        | '{"value":10.1}'
        float      | [value: 10.1f]                        | '{"value":10.1}'
        char       | [value: 'a' as char]                  | '{"value":97}'
        //wrappers
        Boolean    | [value: true]                         | '{"value":true}'
        Byte       | [value: 10]                           | '{"value":10}'
        Short      | [value: 10]                           | '{"value":10}'
        Integer    | [value: 10]                           | '{"value":10}'
        Long       | [value: 10]                           | '{"value":10}'
        Double     | [value: 10.1d]                        | '{"value":10.1}'
        Float      | [value: 10.1f]                        | '{"value":10.1}'
        Character  | [value: 'a' as char]                  | '{"value":97}'
        HttpStatus | [value: HttpStatus.ACCEPTED]          | '{"value":"ACCEPTED"}'

        // other common classes
        URI        | [value: URI.create("http://foo.com")] | '{"value":"http://foo.com"}'
        URL        | [value: new URL("http://foo.com")]    | '{"value":"http://foo.com"}'
        Charset    | [value: StandardCharsets.UTF_8]       | '{"value":"UTF-8"}'
        TimeZone   | [value: TimeZone.getTimeZone("GMT")]  | '{"value":"GMT"}'
        Locale     | [value: Locale.CANADA_FRENCH]         | '{"value":"fr-CA"}'
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
        writeJson(jsonMapper, beanUnderTest) == result
        def read = jsonMapper.readValue(result, typeUnderTest)
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
    void "test basic array type #type"() {
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
        writeJson(jsonMapper, beanUnderTest) == result

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
    }

    @Unroll
    void "test basic collection type #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
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
        writeJson(jsonMapper, beanUnderTest) == result
        def read = jsonMapper.readValue(result, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == data.value

        cleanup:
        context.close()

        where:
        type                           | data                         | result
        "List<String>"                 | [value: ["Test"]]            | '{"value":["Test"]}'
        "Optional<String>"             | [value: Optional.of("Test")] | '{"value":"Test"}'
        "Optional<String>"             | [value: Optional.empty()]    | '{"value":null}'
        "List<? extends CharSequence>" | [value: ["Test"]]            | '{"value":["Test"]}'
        "List<Boolean>"                | [value: [true]]              | '{"value":[true]}'
        "Iterable<String>"             | [value: ["Test"]]            | '{"value":["Test"]}'
        "Iterable<Boolean>"            | [value: [true]]              | '{"value":[true]}'
        "Set<String>"                  | [value: ["Test"] as Set]     | '{"value":["Test"]}'
        "Set<Boolean>"                 | [value: [true] as Set]       | '{"value":[true]}'
        "Collection<String>"           | [value: ["Test"]]            | '{"value":["Test"]}'
        "Collection<Boolean>"          | [value: [true]]              | '{"value":[true]}'
        "Map<String, Boolean>"         | [value: [foo: true]]         | '{"value":{"foo":true}}'

    }

    void "test basic collection type #type with include NON_ABSENT"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
@Serdeable
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
