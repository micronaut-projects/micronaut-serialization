package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * {@link SecretSerde} requires the context of the call it serves to be the context it was created with. The runtime
 * bean serdes keep the property serdes of a type between documents.
 */
@spock.lang.Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1521")
class RuntimeBeanContextSerdeSpec extends Specification {

    private static final Map<String, Object> RUNTIME = [
        'micronaut.serde.serialization.disable-generated-serializer'    : true,
        'micronaut.serde.deserialization.disable-generated-deserializer': true
    ]

    void "the specializations of finished documents are not kept"() {
        given:
        def context = ApplicationContext.run(RUNTIME + ['spec.name': 'RuntimeBeanContextSerdeSpec'])
        def mapper = context.getBean(ObjectMapper)
        TrackedTokenSerializer.CREATED.clear()

        when:
        (1..50).each { assert mapper.writeValueAsString(new TokenHolder(token: new Token(value: "t$it"))) == "{\"token\":\"t$it\"}" }
        def alive = 50
        for (int i = 0; i < 20 && alive > 1; i++) {
            System.gc()
            Thread.sleep(20)
            alive = TrackedTokenSerializer.CREATED.count { it.get() != null }
        }

        then: 'at most the serializer of a document still referenced by the stack'
        TrackedTokenSerializer.CREATED.size() == 50
        alive <= 1

        cleanup:
        context.close()
    }

    void "a merged collection of a type with a contextual serde is updated in place"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)
        def holder = new MergedSecrets(secrets: [new Secret("old")])

        when:
        mapper.updateValueFromTree(holder, mapper.writeValueToTree([secrets: ["new"]]))

        then:
        holder.secrets == [new Secret("old"), new Secret("new")]

        cleanup:
        context.close()
    }

    void "an any setter and an unwrapped map of a type with a contextual serde are read with the context of each document"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every {
            mapper.readValue("{\"a\":\"x$it\"}", AnySecrets).values == [a: new Secret("x$it")] &&
                mapper.readValue("{\"b\":\"y$it\"}", UnwrappedSecrets).values == [b: new Secret("y$it")]
        }

        cleanup:
        context.close()
    }

    @io.micronaut.serde.annotation.Serdeable
    static class MergedSecrets {
        @com.fasterxml.jackson.annotation.JsonMerge
        List<Secret> secrets
    }

    @io.micronaut.serde.annotation.Serdeable
    static class AnySecrets {
        @com.fasterxml.jackson.annotation.JsonIgnore
        Map<String, Secret> values = [:]

        @com.fasterxml.jackson.annotation.JsonAnySetter
        void put(String key, Secret value) {
            values[key] = value
        }
    }

    @io.micronaut.serde.annotation.Serdeable
    static class UnwrappedSecrets {
        @com.fasterxml.jackson.annotation.JsonUnwrapped
        Map<String, Secret> values
    }

    static class Token {
        String value
    }

    @io.micronaut.serde.annotation.Serdeable
    static class TokenHolder {
        Token token
    }

    @io.micronaut.context.annotation.Prototype
    @io.micronaut.context.annotation.Requires(property = 'spec.name', value = 'RuntimeBeanContextSerdeSpec')
    static class TrackedTokenSerializer implements io.micronaut.serde.Serializer<Token> {
        static final List<java.lang.ref.WeakReference<TrackedTokenSerializer>> CREATED = Collections.synchronizedList([])
        private final io.micronaut.serde.Serializer.EncoderContext constructorContext

        TrackedTokenSerializer(@io.micronaut.context.annotation.Parameter io.micronaut.serde.Serializer.EncoderContext context,
                               @io.micronaut.context.annotation.Parameter io.micronaut.core.type.Argument<?> type) {
            this.constructorContext = context
            CREATED << new java.lang.ref.WeakReference<>(this)
        }

        @Override
        void serialize(io.micronaut.serde.Encoder encoder, io.micronaut.serde.Serializer.EncoderContext context,
                       io.micronaut.core.type.Argument<? extends Token> type, Token value) throws IOException {
            if (constructorContext != context) {
                throw new IOException("EncoderContext was not passed to the serializer constructor")
            }
            encoder.encodeString(value.value)
        }
    }

    void "a contextual serde under a bean with class-level features sees them in every document"() {
        given:
        def context = ApplicationContext.run(RUNTIME + ['spec.name': 'RuntimeBeanFeatures'])
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every {
            mapper.writeValueAsString(new FeatureHolder(token: new FeatureToken(value: "a$it"))) == '{"token":"sorted"}' &&
                mapper.readValue("{\"token\":\"x$it\"}", FeatureHolder).token.value == "x$it:single"
        }

        cleanup:
        context.close()
    }

    void "a bean holding a bean with a contextual serde keeps its own property serializer shared"() {
        given:
        def context = ApplicationContext.run(RUNTIME + ['spec.name': 'RuntimeBeanContextSerdeSpec'])
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every {
            mapper.writeValueAsString(new OuterHolder(inner: new TokenHolder(token: new Token(value: "t$it")))) == "{\"inner\":{\"token\":\"t$it\"}}"
        }

        when: 'the property serializers the runtime bean serializers keep'
        def beans = context.getBean(io.micronaut.serde.SerdeRegistry).@objectSerializer.@serBeanMap.values()*.get()
        def serializerOf = { Class type, String property ->
            beans.find { it.@introspection.beanType == type }.@writeProperties.find { it.name == property }.serializer.getClass().simpleName
        }

        then: 'only the property with the contextual serde is specialized per document'
        serializerOf(TokenHolder, 'token').startsWith('PerDocument')
        !serializerOf(OuterHolder, 'inner').startsWith('PerDocument')

        cleanup:
        context.close()
    }

    @io.micronaut.serde.annotation.Serdeable
    static class OuterHolder {
        TokenHolder inner
    }

    @io.micronaut.serde.annotation.Serdeable
    @com.fasterxml.jackson.annotation.JsonFormat(with = [
        com.fasterxml.jackson.annotation.JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES,
        com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY
    ])
    static class FeatureHolder {
        FeatureToken token
    }

    static class FeatureToken {
        String value
    }

    @io.micronaut.context.annotation.Prototype
    @io.micronaut.context.annotation.Requires(property = 'spec.name', value = 'RuntimeBeanFeatures')
    static class FeatureTokenSerializer implements io.micronaut.serde.Serializer<FeatureToken> {
        private final io.micronaut.serde.Serializer.EncoderContext constructorContext

        FeatureTokenSerializer(@io.micronaut.context.annotation.Parameter io.micronaut.serde.Serializer.EncoderContext context,
                               @io.micronaut.context.annotation.Parameter io.micronaut.core.type.Argument<?> type) {
            this.constructorContext = context
        }

        @Override
        void serialize(io.micronaut.serde.Encoder encoder, io.micronaut.serde.Serializer.EncoderContext context,
                       io.micronaut.core.type.Argument<? extends FeatureToken> type, FeatureToken value) throws IOException {
            boolean sorted = constructorContext.features.contains(io.micronaut.serde.config.SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES)
            encoder.encodeString(sorted ? 'sorted' : 'unsorted')
        }
    }

    @io.micronaut.context.annotation.Prototype
    @io.micronaut.context.annotation.Requires(property = 'spec.name', value = 'RuntimeBeanFeatures')
    static class FeatureTokenDeserializer implements io.micronaut.serde.Deserializer<FeatureToken> {
        private final io.micronaut.serde.Deserializer.DecoderContext constructorContext

        FeatureTokenDeserializer(@io.micronaut.context.annotation.Parameter io.micronaut.serde.Deserializer.DecoderContext context,
                                 @io.micronaut.context.annotation.Parameter io.micronaut.core.type.Argument<?> type) {
            this.constructorContext = context
        }

        @Override
        FeatureToken deserialize(io.micronaut.serde.Decoder decoder, io.micronaut.serde.Deserializer.DecoderContext context,
                                 io.micronaut.core.type.Argument<? super FeatureToken> type) throws IOException {
            boolean single = constructorContext.features.contains(io.micronaut.serde.config.DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            return new FeatureToken(value: decoder.decodeString() + (single ? ':single' : ':plain'))
        }
    }

    void "a property with a contextual serde is written with the context of each document by the runtime serializer"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every { mapper.writeValueAsString(new SecretHolder(new Secret("s$it"))) == "{\"secret\":\"s$it\"}" }

        cleanup:
        context.close()
    }

    void "a property with a contextual serde is read with the context of each document by the runtime deserializer"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every { mapper.readValue("{\"secret\":\"s$it\"}", SecretHolder) == new SecretHolder(new Secret("s$it")) }

        cleanup:
        context.close()
    }

    void "a collection of a type with a contextual serde is written and read with the context of each document"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every {
            def json = mapper.writeValueAsString(new SecretListHolder(secrets: [new Secret("a$it"), new Secret("b$it")]))
            json == "{\"secrets\":[\"a$it\",\"b$it\"]}" && mapper.readValue(json, SecretListHolder).secrets == [new Secret("a$it"), new Secret("b$it")]
        }

        cleanup:
        context.close()
    }

    @io.micronaut.serde.annotation.Serdeable
    static class SecretListHolder {
        List<Secret> secrets
    }

    void "documents written and read in parallel each use their own contextual serdes"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)
        def pool = Executors.newFixedThreadPool(8)

        when:
        def results = pool.invokeAll((1..400).collect { i ->
            (Callable<Boolean>) {
                mapper.writeValueAsString(new SecretHolder(new Secret("v$i"))) == "{\"secret\":\"v$i\"}" &&
                    mapper.readValue("{\"secret\":\"v$i\"}", SecretHolder) == new SecretHolder(new Secret("v$i"))
            }
        }).collect { it.get() }

        then:
        results.every()

        cleanup:
        pool.shutdown()
        context.close()
    }
}
