package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

class JsonFormatSpec extends JsonCompileSpec {
    @Unroll
    void "test json format for #type and settings #settings"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    @JsonFormat(${settings.collect { "$it.key=\"$it.value\"" }.join(",")})
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
        type    | data             | settings                  | result
        int     | [value: 10]      | [pattern: '$###,###.###'] | '{"value":"$10"}'
        Integer | [value: 10]      | [pattern: '$###,###.###'] | '{"value":"$10"}'
        long    | [value: 100000l] | [pattern: '$###,###.###'] | '{"value":"$100,000"}'
        Long    | [value: 100000l] | [pattern: '$###,###.###'] | '{"value":"$100,000"}'
    }
}
