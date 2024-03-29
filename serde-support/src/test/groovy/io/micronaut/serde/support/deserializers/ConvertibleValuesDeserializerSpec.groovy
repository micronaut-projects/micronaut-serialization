package io.micronaut.serde.support.deserializers


import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification

class ConvertibleValuesDeserializerSpec extends Specification {
    // this mostly matches the spec from micronaut-jackson-databind

    def 'test without value type'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:
        def values = mapper.readValue('{"foo":"bar","fizz":[4]}', Argument.of(ConvertibleValues))
        then:
        values.names() == ["foo", "fizz"] as Set
        values.get("foo", String).get() == "bar"
        values.get("fizz", Argument.of(List, int)).get() == [4]

        cleanup:
        ctx.close()
    }

    def 'test with value type'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:
        def values = mapper.readValue('{"foo":"4","fizz":5}', Argument.of(ConvertibleValues, Integer))
        then:
        values.names() == ["foo", "fizz"] as Set
        // values should be int-typed
        values.get("foo", Object).get() == 4
        values.get("fizz", Object).get() == 5

        cleanup:
        ctx.close()
    }

    def 'test wrong format'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:
        mapper.readValue('[{"foo":"4","fizz":5},4]', Argument.of(ConvertibleValues))
        then:
        thrown SerdeException

        cleanup:
        ctx.close()
    }
}
