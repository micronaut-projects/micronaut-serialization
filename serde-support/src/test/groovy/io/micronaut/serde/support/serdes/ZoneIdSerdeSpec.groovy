package io.micronaut.serde.support.serdes

import java.time.ZoneId

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ZoneIdSerdeSpec extends Specification {
    @Inject
    ObjectMapper objectMapper

    void "ZoneId serialized"(String value) {
        given:
        ZoneIdPojo pojo = new ZoneIdPojo(zoneId: ZoneId.of(value))

        when:
        String json = objectMapper.writeValueAsString(pojo)

        then:
        json == "{\"zoneId\":\"${value}\"}"

        where:
        value << ["UTC", "GMT", "Z", "Europe/Moscow", "America/New_York", "UTC+01:00", "GMT-08:00", "UT+05:30", "+11:00", "-10:00"]
    }

    void "ZoneId deserialized"(String value) {
        given:
        String json = "{\"zoneId\":\"${value}\"}"

        when:
        ZoneIdPojo pojo = objectMapper.readValue(json, ZoneIdPojo)

        then:
        pojo.getZoneId() == ZoneId.of(value)

        where:
        value << ["UTC", "GMT", "Z", "Europe/Moscow", "America/New_York", "UTC+01:00", "GMT-08:00", "UT+05:30", "+06", "-03", "+11:00", "-10:00"]
    }

    @Serdeable
    static class ZoneIdPojo {
        private ZoneId zoneId

        ZoneId getZoneId() {
            this.zoneId
        }

        void setZoneId(ZoneId zoneId) {
            this.zoneId = zoneId
        }
    }
}
