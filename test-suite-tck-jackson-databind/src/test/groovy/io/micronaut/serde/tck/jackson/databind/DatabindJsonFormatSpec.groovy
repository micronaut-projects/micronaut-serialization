package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.JsonFormatSpec
import spock.lang.Ignore
import spock.lang.PendingFeature

import java.time.Instant

class DatabindJsonFormatSpec extends JsonFormatSpec {

    @PendingFeature
    void "UNSUPPORTED Instant test json format for date #type and settings #settings"() {
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
""", [value: value])
            def result = writeJson(jsonMapper, beanUnderTest)
            def read = jsonMapper.readValue(result, typeUnderTest)

        expect:
            result.startsWith('{"value":"') // was serialized as string, not long
            typeUnderTest.type.isInstance(read)
            resolver(read.value) == resolver(value)

        cleanup:
            context.close()

        where:
            type           | value                                     | settings                                | resolver
            Instant        | Instant.now()                             | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"] | { Instant i -> i.toEpochMilli() }
    }

    @Ignore // Passes on CI fails locally ...
    void "UNSUPPORTED SQL Date test json format for date #type and settings #settings"() {
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
""", [value: value])
            def result = writeJson(jsonMapper, beanUnderTest)
            def read = jsonMapper.readValue(result, typeUnderTest)

        expect:
            result.startsWith('{"value":"') // was serialized as string, not long
            typeUnderTest.type.isInstance(read)
            resolver(read.value) == resolver(value)

        cleanup:
            context.close()

        where:
            type           | value                                     | settings                                | resolver
            java.sql.Date  | new java.sql.Date(2021, 9, 15)            | [pattern: "yyyy-MM-dd"]                 | { java.sql.Date d -> d }
    }


}
