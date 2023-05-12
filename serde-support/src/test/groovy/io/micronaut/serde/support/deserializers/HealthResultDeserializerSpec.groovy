package io.micronaut.serde.support.deserializers

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.health.HealthStatus
import io.micronaut.json.JsonMapper
import io.micronaut.management.health.indicator.HealthResult
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HealthResultDeserializerSpec extends Specification {
    def 'deserialize'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:
        def hr = mapper.readValue('{"name":"foo","status":"UP","details":{"bar":"baz"}}', Argument.of(HealthResult))
        then:
        hr.name == "foo"
        hr.status == HealthStatus.UP
        hr.details == ["bar": "baz"]

        when:"non-standard status"
        hr = mapper.readValue('{"name":"foo","status":"xyz","details":{"bar":"baz"}}', Argument.of(HealthResult))
        then:
        hr.name == "foo"
        hr.status.name == "xyz"
        hr.details == ["bar": "baz"]

        cleanup:
        ctx.close()
    }

    def 'serialize'() {
        // the serialization is done from the introspection, not by our implementation, so this is just a sanity check that it works

        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:
        def str = new String(mapper.writeValueAsBytes(HealthResult.builder("foo", HealthStatus.UP).details(["bar":"baz"]).build()), StandardCharsets.UTF_8)
        then:
        str == '{"name":"foo","status":"UP","details":{"bar":"baz"}}'

        cleanup:
        ctx.close()
    }
}
