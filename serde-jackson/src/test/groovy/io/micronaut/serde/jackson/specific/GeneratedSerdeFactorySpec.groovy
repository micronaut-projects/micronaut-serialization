package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.SpecificSerdeFactory
import io.micronaut.serde.jackson.compiletime.SourceGenGeneratedShape
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

class GeneratedSerdeFactorySpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    ObjectMapper objectMapper = context.getBean(ObjectMapper)
    SerdeRegistry registry = context.getBean(SerdeRegistry)

    void "a generated serde is created by its singleton factory instead of the bean context"() {
        expect: 'the generated serdes are not beans'
        !context.getBeanDefinitions(Serializer).any { it.beanType.simpleName == 'SerdeSourceGenGeneratedShapeSerializer' }
        !context.getBeanDefinitions(Deserializer).any { it.beanType.simpleName == 'SerdeSourceGenGeneratedShapeDeserializer' }

        and: 'their factories are singletons selected for the type'
        def serializerFactory = context.getBeanDefinitions(Serializer).find { it.beanType.simpleName == 'SerdeSourceGenGeneratedShapeSerializerFactory' }
        def deserializerFactory = context.getBeanDefinitions(Deserializer).find { it.beanType.simpleName == 'SerdeSourceGenGeneratedShapeDeserializerFactory' }
        serializerFactory.hasDeclaredAnnotation(SpecificSerdeFactory)
        serializerFactory.hasDeclaredStereotype(Singleton)
        deserializerFactory.hasDeclaredAnnotation(SpecificSerdeFactory)
        deserializerFactory.hasDeclaredStereotype(Singleton)
        registry.findSerializer(Argument.of(SourceGenGeneratedShape)).class == serializerFactory.beanType
        registry.findDeserializer(Argument.of(SourceGenGeneratedShape)).class == deserializerFactory.beanType

        and: 'the generated serdes round-trip'
        objectMapper.readValue('{"name":"a","count":1}', SourceGenGeneratedShape) == new SourceGenGeneratedShape('a', 1)
        objectMapper.writeValueAsString(new SourceGenGeneratedShape('b', 2)) == '{"name":"b","count":2}'
    }

    void "a generated serde passes the context of each document to a nested contextual serde"() {
        expect: 'the holder is handled by the generated serdes'
        context.getBeanDefinitions(Deserializer).any { it.beanType.simpleName == 'SerdeSecretHolderDeserializerFactory' }
        context.getBeanDefinitions(Serializer).any { it.beanType.simpleName == 'SerdeSecretHolderSerializerFactory' }

        and: 'every document reaches the nested serde with its own context'
        (1..3).every {
            objectMapper.readValue('{"secret":"s"}', SecretHolder) == new SecretHolder(new Secret('s'))
                && objectMapper.writeValueAsString(new SecretHolder(new Secret('t'))) == '{"secret":"t"}'
        }
    }
}
