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
        type       | data                                 | settings                  | result
        byte       | [value: 10]                          | [pattern: '$###,###.###'] | '{"value":"$10"}'
        Byte       | [value: 10]                          | [pattern: '$###,###.###'] | '{"value":"$10"}'
        int        | [value: 10]                          | [pattern: '$###,###.###'] | '{"value":"$10"}'
        Integer    | [value: 10]                          | [pattern: '$###,###.###'] | '{"value":"$10"}'
        long       | [value: 100000l]                     | [pattern: '$###,###.###'] | '{"value":"$100,000"}'
        Long       | [value: 100000l]                     | [pattern: '$###,###.###'] | '{"value":"$100,000"}'
        short      | [value: 10000]                       | [pattern: '$###,###.###'] | '{"value":"$10,000"}'
        Short      | [value: 10000]                       | [pattern: '$###,###.###'] | '{"value":"$10,000"}'
        double     | [value: 100000.12d]                  | [pattern: '$###,###.###'] | '{"value":"$100,000.12"}'
        Double     | [value: 100000.12d]                  | [pattern: '$###,###.###'] | '{"value":"$100,000.12"}'
        float      | [value: 100000.12f]                  | [pattern: '$###,###.###'] | '{"value":"$100,000.117"}'
        Float      | [value: 100000.12f]                  | [pattern: '$###,###.###'] | '{"value":"$100,000.117"}'
        BigDecimal | [value: new BigDecimal("100000.12")] | [pattern: '$###,###.###'] | '{"value":"$100,000.12"}'
        BigDecimal | [value: new BigDecimal("100000.12")] | [pattern: '$###,###.###'] | '{"value":"$100,000.12"}'
        BigInteger | [value: new BigInteger("100000")]    | [pattern: '$###,###.###'] | '{"value":"$100,000"}'
        BigInteger | [value: new BigInteger("100000")]    | [pattern: '$###,###.###'] | '{"value":"$100,000"}'
    }
}
