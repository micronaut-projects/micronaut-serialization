package io.micronaut.serde.jackson.object


import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

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
        type       | data                             | result
        BigDecimal | [value: 10.1]                    | '{"value":10.1}'
        BigInteger | [value: BigInteger.valueOf(10l)] | '{"value":10}'
        String     | [value: "Test"]                  | '{"value":"Test"}'
        boolean    | [value: true]                    | '{"value":true}'
        byte       | [value: 10]                      | '{"value":10}'
        short      | [value: 10]                      | '{"value":10}'
        int        | [value: 10]                      | '{"value":10}'
        long       | [value: 10]                      | '{"value":10}'
        double     | [value: 10.1d]                   | '{"value":10.1}'
        float      | [value: 10.1f]                   | '{"value":10.1}'
        char       | [value: 'a' as char]             | '{"value":97}'
        //wrappers
        Boolean    | [value: true]                    | '{"value":true}'
        Byte       | [value: 10]                      | '{"value":10}'
        Short      | [value: 10]                      | '{"value":10}'
        Integer    | [value: 10]                      | '{"value":10}'
        Long       | [value: 10]                      | '{"value":10}'
        Double     | [value: 10.1d]                   | '{"value":10.1}'
        Float      | [value: 10.1f]                   | '{"value":10.1}'
        Character  | [value: 'a' as char]             | '{"value":97}'
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

        cleanup:
        context.close()

        where:
        type                           | data                 | result
        "List<String>"                 | [value: ["Test"]]    | '{"value":["Test"]}'
        "List<? extends CharSequence>" | [value: ["Test"]]    | '{"value":["Test"]}'
        "List<Boolean>"                | [value: [true]]      | '{"value":[true]}'
        "Iterable<String>"             | [value: ["Test"]]    | '{"value":["Test"]}'
        "Iterable<Boolean>"            | [value: [true]]      | '{"value":[true]}'
        "Set<String>"                  | [value: ["Test"]]    | '{"value":["Test"]}'
        "Set<Boolean>"                 | [value: [true]]      | '{"value":[true]}'
        "Collection<String>"           | [value: ["Test"]]    | '{"value":["Test"]}'
        "Collection<Boolean>"          | [value: [true]]      | '{"value":[true]}'
        "Map<String, Boolean>"         | [value: [foo: true]] | '{"value":{"foo":true}}'

    }
}
