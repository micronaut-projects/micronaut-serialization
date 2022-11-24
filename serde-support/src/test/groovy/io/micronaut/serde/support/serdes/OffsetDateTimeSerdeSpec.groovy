package io.micronaut.serde.support.serdes

import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

import java.time.OffsetDateTime

//@Property(name = "micronaut.serde.write-dates-as-timestamps", value = StringUtils.FALSE)
@MicronautTest(startApplication = false)
class OffsetDateTimeSerdeSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    @Unroll
    void "deserialization should work by default as String or timestamp"(Object value) {
        given:
        String json = "{\"timeCreated\":${value}}"

        when:
        OffsetDateTimePojo pojo = objectMapper.readValue(json, OffsetDateTimePojo)

        then:
        pojo.timeCreated == OffsetDateTime.parse('2022-01-01T12:30:00.123Z')

        where:
        value << ['"2022-01-01T12:30:00.123Z"', 1641040200123]
    }

    @Unroll
    void "OffsetDateTime by default is serialized as timestamp"() {
        given:
        OffsetDateTimePojo pojo = new OffsetDateTimePojo()
        pojo.timeCreated = OffsetDateTime.parse('2022-01-01T12:30:00.123Z')

        when:
        String json = objectMapper.writeValueAsString(pojo)

        then:
        '{"timeCreated":1641040200123}' == json
    }

    @Serdeable.Deserializable
    static class OffsetDateTimePojo {
        private OffsetDateTime timeCreated;

        OffsetDateTime getTimeCreated() {
            return timeCreated
        }

        void setTimeCreated(OffsetDateTime timeCreated) {
            this.timeCreated = timeCreated
        }
    }
}
