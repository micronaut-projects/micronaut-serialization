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
        private final Serializer.EncoderContext constructorContext

        CountingTokenSerializer(@Parameter Serializer.EncoderContext context, @Parameter Argument<?> type) {
            this.constructorContext = context
            CREATED.incrementAndGet()
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
}
