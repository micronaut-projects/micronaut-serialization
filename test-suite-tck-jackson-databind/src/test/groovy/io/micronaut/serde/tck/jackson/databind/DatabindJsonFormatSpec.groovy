package io.micronaut.serde.tck.jackson.databind

import com.fasterxml.jackson.annotation.JsonFormat
import io.micronaut.serde.jackson.JsonFormatSpec
import spock.lang.Ignore
import spock.lang.PendingFeature
import spock.lang.Unroll
import tools.jackson.databind.ObjectMapper

import java.time.Instant
import java.util.Date
import java.util.Locale

class DatabindJsonFormatSpec extends JsonFormatSpec {

    @Override
    protected boolean supportsClassLevelJsonFormatPropagation() {
        false
    }

    @PendingFeature(reason = "Remove CompoundLocaleObjectMapperListener when Jackson Databind handles compound @JsonFormat(locale) values")
    void "plain Jackson Databind supports compound JsonFormat locale values"() {
        given:
            def mapper = new ObjectMapper().rebuild()
                    .defaultLocale(Locale.US)
                    .build()
            def value = new CompoundLocaleDate(value: new Date(1665792000000L))

        when:
            def json = mapper.writeValueAsString(value)

        then:
            json.toLowerCase(Locale.ROOT).contains('okt')
            mapper.readValue(json, CompoundLocaleDate).value.time == value.value.time
    }

    @PendingFeature(reason = "Jackson Databind ignores @JsonFormat(pattern) for numeric serializers")
    @Unroll
    void "test json format for #type and settings #settings with record"() {
        expect:
            assertJsonFormatForNumberSettingsWithRecord(type, value, settings, result)

        where:
            variation << jsonFormatNumberSettings()
            type = variation.type
            value = variation.value
            settings = variation.settings
            result = variation.result
    }

    @PendingFeature(reason = "Jackson Databind ignores @JsonFormat(pattern) for numeric serializers")
    @Unroll
    void "test json format for #type and settings #settings"() {
        expect:
            assertJsonFormatForNumberSettings(type, value, settings, result)

        where:
            variation << jsonFormatNumberSettings()
            type = variation.type
            value = variation.value
            settings = variation.settings
            result = variation.result
    }

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


    static class CompoundLocaleDate {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd MMM yyyy", locale = "de_DE", timezone = "UTC")
        Date value
    }
}
