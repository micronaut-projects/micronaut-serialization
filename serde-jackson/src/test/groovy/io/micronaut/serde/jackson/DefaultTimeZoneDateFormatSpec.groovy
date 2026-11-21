package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

class DefaultTimeZoneDateFormatSpec extends Specification {

    void "a configured date format without a time zone follows the default time zone of the JVM"() {
        given:
        def defaultTimeZone = TimeZone.getDefault()
        def context = ApplicationContext.run(['micronaut.serde.date-format': 'yyyy-MM-dd HH:mm'])
        def mapper = context.getBean(ObjectMapper)

        when:
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
        def utc = mapper.writeValueAsString(new Date(0))
        def utcAgain = mapper.writeValueAsString(new Date(0))
        TimeZone.setDefault(TimeZone.getTimeZone('America/Los_Angeles'))
        def losAngeles = mapper.writeValueAsString(new Date(0))

        then:
        utc == '"1970-01-01 00:00"'
        utcAgain == utc
        losAngeles == '"1969-12-31 16:00"'

        cleanup:
        TimeZone.setDefault(defaultTimeZone)
        context.close()
    }

    void "a configured date format with a time zone is cached"() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.date-format': 'yyyy-MM-dd HH:mm', 'micronaut.serde.time-zone': 'UTC'])
        def mapper = context.getBean(JacksonJsonMapper).cloneWithConfiguration(null, null, null)

        when:
        def first = mapper.writeValueAsString(new Date(0))
        def second = mapper.writeValueAsString(new Date(0))

        then:
        first == '"1970-01-01 00:00"'
        second == first
        mapper.@serdeCache.@serializers.size() == 1

        cleanup:
        context.close()
    }
}
