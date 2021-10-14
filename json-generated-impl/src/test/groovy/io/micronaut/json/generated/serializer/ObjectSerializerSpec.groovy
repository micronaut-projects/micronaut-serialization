package io.micronaut.json.generated.serializer

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.generated.GeneratedObjectMapper
import spock.lang.Specification

class ObjectSerializerSpec extends Specification {
    def test() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        expect:
        codec.readValue('{"foo":"bar"}', Argument.OBJECT_ARGUMENT) == ["foo": "bar"]
        codec.readValue('["foo"]', Argument.OBJECT_ARGUMENT) == ["foo"]
        codec.readValue('42', Argument.OBJECT_ARGUMENT) == 42
    }
}
