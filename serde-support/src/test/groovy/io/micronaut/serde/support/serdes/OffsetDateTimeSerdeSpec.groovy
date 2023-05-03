package io.micronaut.serde.support.serdes

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class OffsetDateTimeSerdeSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    @Unroll
    void "deserialization should work by default with a String"(Object value) {
        given:
        String json = "{\"timeCreated\":${value}}"

        when:
        OffsetDateTimePojo pojo = objectMapper.readValue(json, OffsetDateTimePojo)

        then:
        pojo.timeCreated == OffsetDateTime.parse('2022-01-01T12:30:00.123Z')

        where:
        value << ['"2022-01-01T12:30:00.123Z"']
    }


    @Unroll
    void "deserialization should work by default with a timestamp"(Object value) {
        given:
        String json = "{\"timeCreated\":${value}}"

        when:
        OffsetDateTimePojo pojo = objectMapper.readValue(json, OffsetDateTimePojo)

        then:
        pojo.timeCreated == OffsetDateTime.parse('2022-01-01T12:30:00.123Z')

        where:
        value << [1641040200.123]
    }

    @Unroll
    void "OffsetDateTime can be serialized as number"() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.serde.time-write-shape': 'integer',
                'micronaut.serde.numeric-time-unit': 'legacy',
        ])
        OffsetDateTimePojo pojo = new OffsetDateTimePojo()
        pojo.timeCreated = OffsetDateTime.parse('2022-01-01T12:30:00.123Z')

        when:
        String json = ctx.getBean(ObjectMapper).writeValueAsString(pojo)

        then:
        '{"timeCreated":1641040200123}' == json

        cleanup:
        ctx.close()
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
