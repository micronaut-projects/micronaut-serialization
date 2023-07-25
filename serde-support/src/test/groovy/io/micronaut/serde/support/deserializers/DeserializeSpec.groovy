package io.micronaut.serde.support.deserializers

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.health.HealthStatus
import io.micronaut.json.JsonMapper
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.serde.support.TestStatus
import spock.lang.Specification

class DeserializeSpec extends Specification {

    def 'deserialize'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:"Can deserialize with null parameters for @Nullable field of java.lang.Object type"
        def result = mapper.readValue('{"valid":"false","message":"Invalid Input"}', Argument.of(TestStatus))
        then:
        !result.valid
        result.message == 'Invalid Input'
        result.additionalData == null

        when:"Deserialize with all parameters provided and non null"
        result = mapper.readValue('{"valid":"true","message":"In Progress","additionalData":["Step1 Passed", "Step2 InProgress"]}', Argument.of(TestStatus))
        then:
        result.valid
        result.message == 'In Progress'
        result.additionalData != null
        result.additionalData == ["Step1 Passed", "Step2 InProgress"]

        cleanup:
        ctx.close()
    }
}
