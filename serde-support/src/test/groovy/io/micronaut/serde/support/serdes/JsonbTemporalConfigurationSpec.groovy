package io.micronaut.serde.support.serdes

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class JsonbTemporalConfigurationSpec extends Specification {
    def 'duration string mapping is opt-in'() {
        given:
        def ctx = ApplicationContext.run(config)
        def mapper = ctx.getBean(ObjectMapper)

        expect:
        mapper.writeValueAsString(Duration.ofHours(1).plusSeconds(1)) == expected
        mapper.readValue(input, Duration) == Duration.ofHours(1).plusSeconds(1)

        cleanup:
        ctx.close()

        where:
        config                                           | expected          | input
        [:]                                              | '3601000000000'   | '3601000000000'
        ['micronaut.serde.write-durations-as-strings': true] | '"PT1H1S"'     | '"PT1H1S"'
    }

    def 'java util date zone id mapping is opt-in'() {
        given:
        def ctx = ApplicationContext.run(config)
        def mapper = ctx.getBean(ObjectMapper)

        expect:
        mapper.writeValueAsString(Date.from(Instant.parse('1969-12-31T23:00:00Z'))) == expected

        cleanup:
        ctx.close()

        where:
        config                                                  | expected
        [:]                                                     | '"1969-12-31T23:00:00Z"'
        ['micronaut.serde.write-java-util-dates-with-zone-id': true] | '"1969-12-31T23:00:00Z[UTC]"'
    }

    def 'java util date zone id parsing is opt-in'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.write-java-util-dates-with-zone-id': true])
        def mapper = ctx.getBean(ObjectMapper)

        expect:
        mapper.readValue('"1969-12-31T23:00:00Z[UTC]"', Date) == Date.from(Instant.parse('1969-12-31T23:00:00Z'))

        cleanup:
        ctx.close()
    }

    def 'strict ijson temporal mapping is opt-in'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.write-date-times-as-strict-ijson': true])
        def mapper = ctx.getBean(ObjectMapper)

        expect:
        mapper.writeValueAsString(Instant.parse('1970-01-01T00:00:00Z')) == '"1970-01-01T00:00:00Z+00:00"'
        mapper.writeValueAsString(LocalDate.of(1970, 1, 1)) == '"1970-01-01T00:00:00Z+00:00"'
        mapper.writeValueAsString(LocalDateTime.of(1970, 1, 1, 1, 1, 1)) == '"1970-01-01T01:01:01Z+00:00"'
        mapper.writeValueAsString(Date.from(Instant.parse('1970-01-01T00:00:00Z'))) == '"1970-01-01T00:00:00Z+00:00"'

        cleanup:
        ctx.close()
    }

    def 'deprecated three letter time zone ids are accepted by default'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(ObjectMapper)

        expect:
        mapper.readValue('"CST"', TimeZone) instanceof TimeZone

        cleanup:
        ctx.close()
    }

    def 'deprecated three letter time zone id rejection can be enabled'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.reject-deprecated-three-letter-time-zone-ids': true])
        def mapper = ctx.getBean(ObjectMapper)

        when:
        mapper.readValue('"CST"', TimeZone)

        then:
        thrown(Exception)

        cleanup:
        ctx.close()
    }
}
