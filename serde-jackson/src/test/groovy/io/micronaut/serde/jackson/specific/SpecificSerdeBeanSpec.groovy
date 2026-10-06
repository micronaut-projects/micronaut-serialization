package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.serde.Deserializer
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.compiletime.SourceGenGeneratedShape
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors

class SpecificSerdeBeanSpec extends Specification {

    void "a generated serde is created from its definition and its specific serde is reused for later documents"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ObjectMapper objectMapper = context.getBean(ObjectMapper)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(SourceGenGeneratedShape)

        when:
        def first = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def second = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def equalArgument = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), Argument.of(SourceGenGeneratedShape))
        def firstSerializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(null), argument)
        def secondSerializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(null), argument)

        then:
        first.class.simpleName == 'SerdeSourceGenGeneratedShapeDeserializer'
        first.is(second)
        first.is(equalArgument)
        firstSerializer.class.simpleName == 'SerdeSourceGenGeneratedShapeSerializer'
        firstSerializer.is(secondSerializer)
        objectMapper.readValue('{"name":"a","count":1}', SourceGenGeneratedShape) == new SourceGenGeneratedShape('a', 1)
        objectMapper.writeValueAsString(new SourceGenGeneratedShape('b', 2)) == '{"name":"b","count":2}'

        cleanup:
        context.close()
    }

    void "a specific serde is not reused for an argument with other annotation metadata or for a view"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(SourceGenGeneratedShape)
        def annotated = Argument.of(SourceGenGeneratedShape, 'shape', new MutableAnnotationMetadata())

        when:
        def plain = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def firstAnnotated = registry.findDeserializer(annotated).createSpecific(registry.newDecoderContext(null), annotated)
        def secondAnnotated = registry.findDeserializer(annotated).createSpecific(registry.newDecoderContext(null), annotated)
        def firstView = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(String), argument)
        def secondView = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(String), argument)

        then:
        !plain.is(firstAnnotated)
        firstAnnotated.is(secondAnnotated)
        !firstView.is(plain)
        !firstView.is(secondView)

        cleanup:
        context.close()
    }

    void "a generated serde with a nested contextual serde is created for every document"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(SecretHolder)

        when:
        def first = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def second = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def firstSerializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(null), argument)
        def secondSerializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(null), argument)

        then:
        first.class.simpleName == 'SerdeSecretHolderDeserializer'
        !first.is(second)
        firstSerializer.class.simpleName == 'SerdeSecretHolderSerializer'
        !firstSerializer.is(secondSerializer)

        and: 'the specific serde is recorded as bound to the context, so later documents do not probe it again'
        def kept = registry.findDeserializer(argument).@cache.get(argument)
        kept != null
        !(kept instanceof Deserializer)
        def keptSerializer = registry.findSerializer(argument).@cache.get(argument)
        keptSerializer != null
        !(keptSerializer instanceof Serializer)

        cleanup:
        context.close()
    }

    void "a generated serde that resolves a recursive property lazily is created for every document"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ObjectMapper objectMapper = context.getBean(ObjectMapper)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(RecursiveParent)
        def json = '{"name":"p","child":{"name":"c","parent":{"name":"q","child":null}}}'

        when:
        def first = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def second = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)

        then:
        first.class.simpleName == 'SerdeRecursiveParentDeserializer'
        !first.is(second)
        (1..3).every {
            objectMapper.readValue(json, RecursiveParent) == new RecursiveParent('p', new RecursiveChild('c', new RecursiveParent('q', null)))
        }

        cleanup:
        context.close()
    }

    void "a generated serde with a property deserialized by the runtime object deserializer is created for every document"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ObjectMapper objectMapper = context.getBean(ObjectMapper)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def argument = Argument.of(IdentityGroupHolder)

        when:
        def first = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def second = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(null), argument)
        def holder = objectMapper.readValue('{"label":"a","group":{"owner":{"id":1,"name":"Ada"},"manager":1}}', IdentityGroupHolder)
        def again = objectMapper.readValue('{"label":"b","group":{"owner":{"id":1,"name":"Bob"},"manager":1}}', IdentityGroupHolder)

        then:
        first.class.simpleName == 'SerdeIdentityGroupHolderDeserializer'
        // The runtime object deserializer keeps the property deserializers in descriptions shared by every document
        !first.is(second)
        holder.group().manager.is(holder.group().owner)
        holder.group().owner.name == 'Ada'
        again.group().manager.is(again.group().owner)
        again.group().owner.name == 'Bob'

        when: 'a document refers to an identifier only an earlier document defined'
        objectMapper.readValue('{"label":"c","group":{"manager":1}}', IdentityGroupHolder)

        then:
        def e = thrown(Exception)
        e.message.contains('1')

        cleanup:
        context.close()
    }

    void "a context shared between threads never reuses a specific serde bound to the context"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        def sharedContext = registry.newDecoderContext(null)
        def holder = Argument.of(SecretHolder)
        def shape = Argument.of(SourceGenGeneratedShape)
        def pool = Executors.newFixedThreadPool(4)

        when:
        def futures = (1..400).collect { i ->
            pool.submit({
                def argument = i % 2 == 0 ? holder : shape
                registry.findDeserializer(argument).createSpecific(sharedContext, argument)
            } as Callable<Object>)
        }
        def created = futures.collect { it.get() }
        def holders = created.findAll { it.class.simpleName == 'SerdeSecretHolderDeserializer' }
        def shapes = created.findAll { it.class.simpleName == 'SerdeSourceGenGeneratedShapeDeserializer' }

        then:
        holders.size() == 200
        holders.toSet().size() == 200
        shapes.size() == 200
        // Threads that create the first specific serde at the same time can each keep their own
        shapes.toSet().size() <= 4

        cleanup:
        pool.shutdown()
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
