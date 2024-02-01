package io.micronaut.serde.jackson.mixin

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
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
}
