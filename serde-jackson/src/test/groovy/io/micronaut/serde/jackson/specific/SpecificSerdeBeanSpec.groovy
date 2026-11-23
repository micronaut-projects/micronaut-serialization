package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.compiletime.SourceGenGeneratedShape
import spock.lang.Specification

class SpecificSerdeBeanSpec extends Specification {

    void "a generated serde is created from its definition for every specific serde"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ObjectMapper objectMapper = context.getBean(ObjectMapper)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(SourceGenGeneratedShape)

        when:
        def first = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def second = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)

        then:
        first.class.simpleName == 'SerdeSourceGenGeneratedShapeDeserializer'
        !first.is(second)
        objectMapper.readValue('{"name":"a","count":1}', SourceGenGeneratedShape) == new SourceGenGeneratedShape('a', 1)
        objectMapper.writeValueAsString(new SourceGenGeneratedShape('b', 2)) == '{"name":"b","count":2}'

        cleanup:
        context.close()
    }

    void "a generated serde passes the context of each document to a nested contextual serde"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ObjectMapper objectMapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every {
            objectMapper.readValue('{"secret":"s"}', SecretHolder) == new SecretHolder(new Secret('s'))
                && objectMapper.writeValueAsString(new SecretHolder(new Secret('t'))) == '{"secret":"t"}'
        }

        cleanup:
        context.close()
    }

    void "a contextual serde with a created bean listener is still created by the bean context"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'SpecificSerdeBeanSpec'])
        ObjectMapper objectMapper = context.getBean(ObjectMapper)
        SecretDeserializerListener listener = context.getBean(SecretDeserializerListener)

        when:
        def holders = (1..3).collect { objectMapper.readValue('{"secret":"s"}', SecretHolder) }

        then:
        holders.every { it == new SecretHolder(new Secret('s')) }
        listener.created.get() == 3

        cleanup:
        context.close()
    }
}
