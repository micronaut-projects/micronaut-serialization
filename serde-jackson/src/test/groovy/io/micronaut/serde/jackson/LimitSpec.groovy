package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification

class LimitSpec extends Specification {
    def 'depth limit while encoding'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.maximum-nesting-depth': 2])
        def mapper = ctx.getBean(JsonMapper)

        when:
        def val = mapper.writeValueAsString(['foo': ['bar': 'baz']])
        then:
        val == '{"foo":{"bar":"baz"}}'

        when:
        mapper.writeValueAsString(['foo': ['bar': []]])
        then:
        thrown SerdeException

        cleanup:
        ctx.close()
    }

    def 'depth limit while decoding'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.maximum-nesting-depth': 2])
        def mapper = ctx.getBean(JsonMapper)

        when:
        def val = mapper.readValue('{"foo":{"bar":"baz"}}', Map)
        then:
        val == ['foo': ['bar': 'baz']]

        when:
        mapper.readValue('{"foo":{"bar":[]}}', Map)
        then:
        thrown SerdeException

        cleanup:
        ctx.close()
    }
}
