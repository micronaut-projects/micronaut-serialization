package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Specification

class DefaultTimeZoneDateFormatSpec extends Specification {

    void "a configured date format without a time zone follows the default time zone of the JVM, and is cached"() {
        given:
        def defaultTimeZone = TimeZone.getDefault()
        def context = ApplicationContext.run(['micronaut.serde.date-format': 'yyyy-MM-dd HH:mm'])
        def mapper = context.getBean(JacksonJsonMapper).cloneWithConfiguration(null, null, null)

        when:
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
        def utc = mapper.writeValueAsString(new Date(0))
        def utcNested = mapper.writeValueAsString(new Dated(date: new Date(0)))
        def utcRead = mapper.readValue('"1970-01-01 00:00"', Date)
        TimeZone.setDefault(TimeZone.getTimeZone('America/Los_Angeles'))
        def losAngeles = mapper.writeValueAsString(new Date(0))
        def losAngelesNested = mapper.writeValueAsString(new Dated(date: new Date(0)))
        def losAngelesRead = mapper.readValue('"1969-12-31 16:00"', Date)

        then:
        utc == '"1970-01-01 00:00"'
        utcNested == '{"date":"1970-01-01 00:00"}'
        losAngeles == '"1969-12-31 16:00"'
        losAngelesNested == '{"date":"1969-12-31 16:00"}'
        utcRead.time == 0
        losAngelesRead.time == 0
        mapper.@serdeCache.size() == 3

        cleanup:
        TimeZone.setDefault(defaultTimeZone)
        context.close()
    }

    void "a configured date format with a time zone keeps it"() {
        given:
        def defaultTimeZone = TimeZone.getDefault()
        def context = ApplicationContext.run(['micronaut.serde.date-format': 'yyyy-MM-dd HH:mm', 'micronaut.serde.time-zone': 'UTC'])
        def mapper = context.getBean(JacksonJsonMapper).cloneWithConfiguration(null, null, null)

        when:
        TimeZone.setDefault(TimeZone.getTimeZone('America/Los_Angeles'))
        def first = mapper.writeValueAsString(new Date(0))
        def second = mapper.writeValueAsString(new Date(0))

        then:
        first == '"1970-01-01 00:00"'
        second == first
        mapper.@serdeCache.@serializers.size() == 1

        cleanup:
        TimeZone.setDefault(defaultTimeZone)
        context.close()
    }

    @Serdeable
    static class Dated {
        Date date
    }
}
