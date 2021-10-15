package io.micronaut.json.generated.serializer

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.DeserializationException
import io.micronaut.json.generated.GeneratedObjectMapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ObjectSerializerSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    def "test object serializer can read maps and primitive values"() {
        when:
        def codec = context.getBean(GeneratedObjectMapper)

        then:
        codec.readValue('{"foo":"bar"}', Argument.OBJECT_ARGUMENT) == ["foo": "bar"]
        codec.readValue('["foo"]', Argument.OBJECT_ARGUMENT) == ["foo"]
        codec.readValue('42', Argument.OBJECT_ARGUMENT) == 42
        codec.readValue('42', Argument.INT) == 42
        codec.readValue('42', Argument.STRING) == "42"

        when:
        codec.readValue('"junk"', Argument.INT)

        then:
        def e = thrown(DeserializationException)
        e.message.contains("Unable to coerce string to integer ")
    }
}
