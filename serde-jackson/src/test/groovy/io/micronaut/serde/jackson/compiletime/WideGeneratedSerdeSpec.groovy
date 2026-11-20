package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The generated serdes of types with more properties than one method can hold within the JIT size
 * limit split their work into several methods. They must behave like the runtime serdes.
 */
class WideGeneratedSerdeSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext generatedContext = ApplicationContext.run([
        'micronaut.serde.serialization.inclusion'       : 'ALWAYS',
        'micronaut.serde.deserialization.ignore-unknown': false
    ])

    @Shared
    @AutoCleanup
    ApplicationContext runtimeContext = ApplicationContext.run([
        'micronaut.serde.serialization.inclusion'                     : 'ALWAYS',
        'micronaut.serde.deserialization.ignore-unknown'              : false,
        'micronaut.serde.serialization.disable-generated-serializer'  : true,
        'micronaut.serde.deserialization.disable-generated-deserializer': true
    ])

    @Shared
    JsonMapper generated = generatedContext.getBean(JsonMapper)

    @Shared
    JsonMapper runtime = runtimeContext.getBean(JsonMapper)

    void 'the generated serdes of a wide bean are used and match the runtime serdes'() {
        given:
        def registry = generatedContext.getBean(SerdeRegistry)
        def bean = wideBean()

        when:
        String generatedJson = generated.writeValueAsString(bean)
        String runtimeJson = runtime.writeValueAsString(bean)
        SourceGenWideBean decoded = generated.readValue(generatedJson, SourceGenWideBean)

        then:
        generatedClass(registry, SourceGenWideBean, 'Serializer')
        generatedClass(registry, SourceGenWideBean, 'Deserializer')
        generatedJson == runtimeJson
        generated.writeValueAsString(decoded) == generatedJson
        decoded.p0 == 'v0'
        decoded.p1 == 1
        decoded.p2 == 2
        decoded.p3
        decoded.p148 == 'v148'
        decoded.p149 == 149
    }

    void 'a wide bean reads an alias and reports #scenario like the runtime deserializer'() {
        expect:
        failure(generated, json).message.contains(message)
        failure(runtime, json).message.contains(message)

        where:
        scenario                         | json                                                        | message
        'a duplicate property below 64'  | '{"p128":"x","p8":"a","p8":"b"}' | 'Duplicate property [p8]'
        'a duplicate property above 64'  | '{"p128":"x","p120":"a","p120":"b"}'                         | 'Duplicate property [p120]'
        'a duplicate through an alias'   | '{"p128":"x","p100":"a","alias100":"b"}'                     | 'Duplicate property [alias100]'
        'a missing required property'    | '{"p0":"a"}'                                                 | 'p128'
        'an unknown property'            | '{"p128":"x","extra":1}'                                     | 'Unknown property [extra]'
    }

    void 'a wide bean reads its alias'() {
        when:
        SourceGenWideBean decoded = generated.readValue('{"p128":"x","alias100":"a"}', SourceGenWideBean)

        then:
        decoded.p100 == 'a'
        decoded.p128 == 'x'
    }

    void 'the generated serializer of a wide record matches the runtime serializer'() {
        given:
        def registry = generatedContext.getBean(SerdeRegistry)
        Object[] values = (0..<150).collect { i -> value(i) } as Object[]
        def record = SourceGenWideRecord.getDeclaredConstructors()[0].newInstance(values) as SourceGenWideRecord

        when:
        String generatedJson = generated.writeValueAsString(record)
        SourceGenWideRecord decoded = generated.readValue(generatedJson, SourceGenWideRecord)

        then:
        generatedClass(registry, SourceGenWideRecord, 'Serializer')
        generatedJson == runtime.writeValueAsString(record)
        decoded == record
    }

    private static SourceGenWideBean wideBean() {
        def bean = new SourceGenWideBean()
        for (int i = 0; i < 150; i++) {
            bean."p$i" = value(i)
        }
        bean
    }

    private static Object value(int i) {
        switch (i % 4) {
            case 0: return 'v' + i
            case 1: return i
            case 2: return Integer.valueOf(i)
            default: return true
        }
    }

    private static Throwable failure(JsonMapper mapper, String json) {
        try {
            mapper.readValue(json, Argument.of(SourceGenWideBean))
        } catch (SerdeException e) {
            return e
        }
        throw new AssertionError('Expected a failure for ' + json)
    }

    private static boolean generatedClass(SerdeRegistry registry, Class<?> type, String suffix) {
        Argument argument = Argument.of(type)
        Object serde = suffix == 'Serializer'
            ? registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
            : registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        serde.class.name == "${type.package.name}.Serde${type.simpleName}${suffix}".toString()
    }
}
