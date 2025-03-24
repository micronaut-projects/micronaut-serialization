package io.micronaut.serde.jackson.mixin

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.jackson.mixin.outer.MuxedEvent4
import io.micronaut.serde.jackson.mixin.outer.MuxedEvent5
import spock.lang.PendingFeature
import spock.lang.Specification

class SerdeMixinSpec extends Specification {

    void "should deserialize"() {
        given:
            def context = ApplicationContext.run()
        expect:
            def read = context.getBean(ObjectMapper).readValue('{"payload": {"type": "MyTestClass", "name": "Some name"}}', FooMessage)

            read.getClass().name.endsWith 'FooMessage'
            read.payload().getClass().name.endsWith 'MyTestClass'
            read.payload().name() == 'Some name'

        cleanup:
            context.close()
    }

    void "should serialize with mixin"() {
        given:
            def context = ApplicationContext.run()
            def objectMapper = context.getBean(ObjectMapper)
            def muxedEvent = new MuxedEvent3("c1", "text")
        expect:
            def str = objectMapper.writeValueAsString(muxedEvent)
            str == '{"compartment":"c1","content":"text"}'

        cleanup:
            context.close()
    }

    @PendingFeature(reason = "https://github.com/micronaut-projects/micronaut-core/pull/11680")
    void "should serialize different package with mixin"() {
        given:
            def context = ApplicationContext.run()
            def objectMapper = context.getBean(ObjectMapper)
            def muxedEvent = new MuxedEvent4("c1", "text")
        when:
            def str = objectMapper.writeValueAsString(muxedEvent)
        then:
            noExceptionThrown()
            str == '{"compartment":"c1","content":"text"}'

        cleanup:
            context.close()
    }

    @PendingFeature(reason = "https://github.com/micronaut-projects/micronaut-core/pull/11680")
    void "should serialize different package with mixin 2"() {
        given:
            def context = ApplicationContext.run()
            def objectMapper = context.getBean(ObjectMapper)
            def muxedEvent = new MuxedEvent5("c1", "text")
        when:
            def str = objectMapper.writeValueAsString(muxedEvent)
        then:
            noExceptionThrown()
            str == '{"compartment":"c1","content":"text"}'

        cleanup:
            context.close()
    }
}
