package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * Compiles the wide bean and record of {@link WideGeneratedSerdeSpec} within the test, so that the source
 * generation of the split serializer and deserializer methods runs with the test coverage.
 */
class WideGeneratedSerdeCompileSpec extends JsonCompileSpec {

    void 'the generated serdes of a compiled wide bean split their methods and round trip'() {
        given:
        ApplicationContext context = buildContext('test.SourceGenWideBean', source('SourceGenWideBean'))
        Class<?> beanType = context.classLoader.loadClass('test.SourceGenWideBean')
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        Argument type = Argument.of(beanType)
        def bean = beanType.getDeclaredConstructor().newInstance()
        for (int i = 0; i < 150; i++) {
            bean."p$i" = value(i)
        }

        expect:
        generatedSerializer(registry, type).class.declaredMethods*.name.contains('serializeProperties0')
        generatedDeserializer(registry, type).class.declaredMethods*.name.contains('deserializeProperty0')

        when:
        String json = jsonMapper.writeValueAsString(bean)
        def decoded = jsonMapper.readValue(json, type)

        then:
        json.contains('"p0":"v0"')
        json.contains('"p149":149')
        jsonMapper.writeValueAsString(decoded) == json
        decoded.p100 == 'v100'
        decoded.p128 == 'v128'

        when:
        def aliased = jsonMapper.readValue('{"p128":"x","alias100":"a"}', type)

        then:
        aliased.p100 == 'a'
        aliased.p128 == 'x'

        expect:
        failure { jsonMapper.readValue('{"p128":"x","p120":"a","p120":"b"}', type) }.message.contains('Duplicate property [p120]')
        failure { jsonMapper.readValue('{"p0":"a"}', type) }.message.contains('p128')

        cleanup:
        context?.close()
    }

    void 'the generated serializer of a compiled wide record splits its methods and round trips'() {
        given:
        ApplicationContext context = buildContext('test.SourceGenWideRecord', source('SourceGenWideRecord'))
        Class<?> recordType = context.classLoader.loadClass('test.SourceGenWideRecord')
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        Argument type = Argument.of(recordType)
        Object[] values = (0..<150).collect { i -> value(i) } as Object[]
        def record = recordType.getDeclaredConstructors()[0].newInstance(values)

        expect:
        generatedSerializer(registry, type).class.declaredMethods*.name.contains('serializeProperties0')

        when:
        String json = jsonMapper.writeValueAsString(record)

        then:
        json.contains('"c0":"v0"')
        json.contains('"c149":149')
        jsonMapper.readValue(json, type) == record

        cleanup:
        context?.close()
    }

    private static String source(String simpleName) {
        new File("src/test/java/io/micronaut/serde/jackson/compiletime/${simpleName}.java").text
            .replace('package io.micronaut.serde.jackson.compiletime;', 'package test;')
    }

    private static Object value(int i) {
        switch (i % 4) {
            case 0: return 'v' + i
            case 1: return i
            case 2: return Integer.valueOf(i)
            default: return true
        }
    }

    private static Serializer generatedSerializer(SerdeRegistry registry, Argument type) {
        Serializer serializer = registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type)
        assert serializer.class.name == "test.Serde${type.type.simpleName}Serializer".toString()
        serializer
    }

    private static Deserializer generatedDeserializer(SerdeRegistry registry, Argument type) {
        Deserializer deserializer = registry.findDeserializer(type).createSpecific(registry.newDecoderContext(Object), type)
        assert deserializer.class.name == "test.Serde${type.type.simpleName}Deserializer".toString()
        deserializer
    }

    private static Throwable failure(Closure<?> action) {
        try {
            action.call()
        } catch (SerdeException e) {
            return e
        }
        throw new AssertionError('Expected a failure')
    }
}
