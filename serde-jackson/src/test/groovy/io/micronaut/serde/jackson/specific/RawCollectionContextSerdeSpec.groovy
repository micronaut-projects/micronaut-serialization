package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

/**
 * {@link SecretSerde.SecretSerializer} requires the context of the call it serves to be the one it was created with.
 */
@Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1516")
class RawCollectionContextSerdeSpec extends Specification {

    @Unroll
    void "the element serializer of a #shape property is created with the context of each document (generated: #generated)"() {
        given:
        def context = ApplicationContext.run(generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def mapper = context.getBean(ObjectMapper)

        expect:
        mapper.writeValueAsString(bean) == json
        mapper.writeValueAsString(bean) == json

        cleanup:
        context.close()

        where:
        shape         | generated | bean                                                         | json
        'raw List'    | true      | new RawListHolder(values: [new Secret("a"), "x"])            | '{"values":["a","x"]}'
        'raw List'    | false     | new RawListHolder(values: [new Secret("a"), "x"])            | '{"values":["a","x"]}'
        'List<Object>' | true     | new ObjectListHolder(values: [new Secret("b")])              | '{"values":["b"]}'
        'List<Object>' | false    | new ObjectListHolder(values: [new Secret("b")])              | '{"values":["b"]}'
        'Object'      | true      | new ObjectHolder(value: new Secret("c"))                     | '{"value":"c"}'
        'Object'      | false     | new ObjectHolder(value: new Secret("c"))                     | '{"value":"c"}'
        'raw Map'     | true      | new RawMapHolder(values: [k: new Secret("d")])               | '{"values":{"k":"d"}}'
        'raw Map'     | false     | new RawMapHolder(values: [k: new Secret("d")])               | '{"values":{"k":"d"}}'
        'nested bean' | true      | new ObjectHolder(value: new SecretHolder(new Secret("e")))   | '{"value":{"secret":"e"}}'
        'nested bean' | false     | new ObjectHolder(value: new SecretHolder(new Secret("e")))   | '{"value":{"secret":"e"}}'
        'subtype'     | true      | new ShapeHolder(shape: new SecretShape(secret: new Secret("f"))) | '{"shape":{"type":"secret","secret":"f"}}'
        'subtype'     | false     | new ShapeHolder(shape: new SecretShape(secret: new Secret("f"))) | '{"shape":{"type":"secret","secret":"f"}}'
        'Object[]'    | true      | new ObjectArrayHolder(values: [new Secret("g"), "x"] as Object[]) | '{"values":["g","x"]}'
        'Object[]'    | false     | new ObjectArrayHolder(values: [new Secret("g"), "x"] as Object[]) | '{"values":["g","x"]}'
        'Optional<Object>' | true  | new OptionalHolder(value: Optional.of(new Secret("h")))     | '{"value":"h"}'
        'Optional<Object>' | false | new OptionalHolder(value: Optional.of(new Secret("h")))     | '{"value":"h"}'
    }

    @Unroll
    void "documents written in parallel each use the serializers of their own context (generated: #generated)"() {
        given:
        def context = ApplicationContext.run(generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ])
        def mapper = context.getBean(ObjectMapper)
        def pool = java.util.concurrent.Executors.newFixedThreadPool(8)

        when:
        def results = pool.invokeAll((1..400).collect { i ->
            (java.util.concurrent.Callable<Boolean>) {
                def bean = new ObjectListHolder(values: [new Secret("a$i"), "x", new Secret("b$i")])
                mapper.writeValueAsString(bean) == "{\"values\":[\"a$i\",\"x\",\"b$i\"]}"
            }
        }).collect { it.get() }

        then:
        results.every()

        cleanup:
        pool.shutdown()
        context.close()

        where:
        generated << [true, false]
    }

    void "a new application context does not see the serializers of a closed one"() {
        expect:
        (1..3).every {
            def context = ApplicationContext.run()
            try {
                context.getBean(ObjectMapper).writeValueAsString(new ObjectHolder(value: new Secret("c$it"))) == "{\"value\":\"c$it\"}"
            } finally {
                context.close()
            }
        }
    }

    void "the serializers of finished documents are not kept by a serializer of runtime types"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'RawCollectionContextSerdeSpec',
            'micronaut.serde.serialization.disable-generated-serializer': true])
        def mapper = context.getBean(ObjectMapper)
        CountingTokenSerializer.INSTANCES.clear()

        when:
        (1..50).each { assert mapper.writeValueAsString(new ObjectListHolder(values: [new Token("t$it")])) == "{\"values\":[\"t$it\"]}" }
        def alive = 50
        for (int i = 0; i < 20 && alive > 1; i++) {
            System.gc()
            Thread.sleep(20)
            alive = CountingTokenSerializer.INSTANCES.count { it.get() != null }
        }

        then:
        CountingTokenSerializer.INSTANCES.size() == 50
        alive <= 1

        cleanup:
        context.close()
    }

    @Unroll
    void "the serializer of a runtime type is created once per document (generated: #generated)"() {
        given:
        def context = ApplicationContext.run(['spec.name': 'RawCollectionContextSerdeSpec'] + (generated ? [:] : [
            'micronaut.serde.serialization.disable-generated-serializer': true,
            'micronaut.serde.deserialization.disable-generated-deserializer': true
        ]))
        def mapper = context.getBean(ObjectMapper)
        CountingTokenSerializer.CREATED.set(0)
        def bean = new ObjectListHolder(values: [new Token("a"), "x", new Token("b"), new Token("c")])

        expect:
        mapper.writeValueAsString(bean) == '{"values":["a","x","b","c"]}'
        CountingTokenSerializer.CREATED.get() == 1
        mapper.writeValueAsString(bean) == '{"values":["a","x","b","c"]}'
        CountingTokenSerializer.CREATED.get() == 2

        cleanup:
        context.close()

        where:
        generated << [true, false]
    }

    static class Token {
        final String value

        Token(String value) {
            this.value = value
        }
    }

    @Prototype
    @Requires(property = 'spec.name', value = 'RawCollectionContextSerdeSpec')
    static class CountingTokenSerializer implements Serializer<Token> {
        static final AtomicInteger CREATED = new AtomicInteger()
        static final List<java.lang.ref.WeakReference<CountingTokenSerializer>> INSTANCES = Collections.synchronizedList([])
        private final Serializer.EncoderContext constructorContext

        CountingTokenSerializer(@Parameter Serializer.EncoderContext context, @Parameter Argument<?> type) {
            this.constructorContext = context
            CREATED.incrementAndGet()
            INSTANCES << new java.lang.ref.WeakReference<>(this)
        }

        @Override
        void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends Token> type, Token value) throws IOException {
            if (constructorContext != context) {
                throw new IOException("EncoderContext was not passed to the serializer constructor")
            }
            encoder.encodeString(value.value)
        }
    }

    @Serdeable
    static class RawListHolder {
        List values
    }

    @Serdeable
    static class ObjectListHolder {
        List<Object> values
    }

    @Serdeable
    static class ObjectHolder {
        Object value
    }

    @Serdeable
    static class RawMapHolder {
        Map values
    }

    @Serdeable
    static class ObjectArrayHolder {
        Object[] values
    }

    @Serdeable
    static class OptionalHolder {
        Optional<Object> value
    }

    @Serdeable
    static class ShapeHolder {
        Shape shape
    }

    @Serdeable
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME, property = 'type')
    @com.fasterxml.jackson.annotation.JsonSubTypes([
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = PlainShape, name = 'plain'),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = SecretShape, name = 'secret')
    ])
    static abstract class Shape {
    }

    @Serdeable
    static class PlainShape extends Shape {
        String side
    }

    @Serdeable
    static class SecretShape extends Shape {
        Secret secret
    }
}
