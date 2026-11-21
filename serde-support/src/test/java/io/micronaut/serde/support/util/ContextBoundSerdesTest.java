package io.micronaut.serde.support.util;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.DocumentSerdes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBoundSerdesTest {

    private static final Argument<String> STRING = Argument.STRING;
    private static final Encoder ENCODER = proxy(Encoder.class, null);
    private static final Decoder DECODER = proxy(Decoder.class, null);

    private static ApplicationContext applicationContext;
    private static SerdeRegistry registry;

    @BeforeAll
    static void start() {
        applicationContext = ApplicationContext.run();
        registry = applicationContext.getBean(SerdeRegistry.class);
    }

    @AfterAll
    static void stop() {
        applicationContext.close();
    }

    @Test
    void serdeNotBoundToTheContextIsKept() throws Exception {
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        TokenSerializer serializer = new TokenSerializer(encoderContext);
        assertSame(serializer, ContextBoundSerdes.specialize(c -> serializer, encoderContext));

        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        TokenDeserializer deserializer = new TokenDeserializer(decoderContext);
        assertSame(deserializer, ContextBoundSerdes.specialize(c -> deserializer, decoderContext));
    }

    @Test
    void creationOutsideOfASpecializationIsIgnored() throws Exception {
        ContextBoundSerdes.created();
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        TokenSerializer serializer = new TokenSerializer(context);
        assertSame(serializer, ContextBoundSerdes.specialize(c -> serializer, context));
    }

    @Test
    void nestedCreationMakesTheOuterSerializerPerDocument() throws Exception {
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        Serializer<String> outer = ContextBoundSerdes.specialize(
            c -> ContextBoundSerdes.specialize(bound(TokenSerializer::new), c),
            context
        );
        assertFalse(outer instanceof TokenSerializer);
        // the tracking ended with the outermost specialization
        TokenSerializer plain = new TokenSerializer(context);
        assertSame(plain, ContextBoundSerdes.specialize(c -> plain, context));
    }

    @Test
    void serializerIsSpecializedOncePerDocument() throws Exception {
        List<TokenSerializer> created = new ArrayList<>();
        Serializer.EncoderContext first = registry.newEncoderContext(null);
        Serializer<String> serializer = ContextBoundSerdes.specialize(bound(c -> record(created, new TokenSerializer(c))), first);

        serializer.serialize(ENCODER, first, STRING, "a");
        serializer.serialize(ENCODER, first.withFeatures(Set.of(SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES), Set.of()), STRING, "b");
        assertEquals(1, created.size());
        assertEquals(List.of("a", "b"), created.get(0).values);

        Serializer.EncoderContext second = registry.newEncoderContext(null);
        // a specialization stored through a derived context is kept by the document context
        serializer.serialize(ENCODER, second.withFeatures(Set.of(SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES), Set.of()), STRING, "c");
        serializer.serialize(ENCODER, second, STRING, "d");
        assertEquals(2, created.size());
        assertEquals(List.of("c", "d"), created.get(1).values);
        assertFalse(serializer instanceof ObjectSerializer);
    }

    @Test
    void serializerDelegatesTheValueChecks() throws Exception {
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        Serializer<String> serializer = ContextBoundSerdes.specialize(bound(TokenSerializer::new), context);
        assertTrue(serializer.isEmpty(context, ""));
        assertFalse(serializer.isEmpty(context, "a"));
        assertTrue(serializer.isAbsent(context, "absent"));
        assertFalse(serializer.isAbsent(context, "a"));
        assertTrue(serializer.isDefault(context, "default"));
        assertFalse(serializer.isDefault(context, "a"));
    }

    @Test
    void contextWithoutDocumentSerdesSpecializesForEachUse() throws Exception {
        Serializer.EncoderContext documentContext = registry.newEncoderContext(null);
        Serializer.EncoderContext context = proxy(Serializer.EncoderContext.class, documentContext);
        List<TokenSerializer> created = new ArrayList<>();
        Serializer<String> serializer = ContextBoundSerdes.specialize(bound(c -> record(created, new TokenSerializer(c))), context);
        serializer.serialize(ENCODER, context, STRING, "a");
        serializer.serialize(ENCODER, context, STRING, "b");
        assertEquals(3, created.size());
    }

    @Test
    void valueChecksFallBackWhenTheSerializerCannotBeCreated() throws Exception {
        Serializer.EncoderContext context = proxy(Serializer.EncoderContext.class, registry.newEncoderContext(null));
        Serializer<String> serializer = ContextBoundSerdes.specialize(failingAfterFirst(TokenSerializer::new), context);
        assertFalse(serializer.isEmpty(context, ""));
        assertTrue(serializer.isEmpty(context, null));
        assertFalse(serializer.isAbsent(context, "absent"));
        assertFalse(serializer.isDefault(context, "default"));
        assertThrows(SerdeException.class, () -> serializer.serialize(ENCODER, context, STRING, "a"));
    }

    @Test
    void objectSerializerKeepsSerializingIntoObjects() throws Exception {
        Serializer.EncoderContext first = registry.newEncoderContext(null);
        int[] calls = {0};
        Serializer<String> serializer = ContextBoundSerdes.specialize(bound(c -> calls[0]++ == 0 ? new TokenObjectSerializer(c) : new TokenSerializer(c)), first);
        ObjectSerializer<String> objectSerializer = assertInstanceOf(ObjectSerializer.class, serializer);

        objectSerializer.serializeInto(ENCODER, first, STRING, "a");
        TokenObjectSerializer specific = assertInstanceOf(TokenObjectSerializer.class, DocumentSerdes.get(first, objectSerializer));
        assertEquals(List.of("into:a"), specific.values);

        // a later document whose serializer cannot serialize into an object reports it
        Serializer.EncoderContext second = registry.newEncoderContext(null);
        assertThrows(SerdeException.class, () -> objectSerializer.serializeInto(ENCODER, second, STRING, "b"));
    }

    @Test
    void deserializerIsSpecializedOncePerDocument() throws Exception {
        List<TokenDeserializer> created = new ArrayList<>();
        Deserializer.DecoderContext first = registry.newDecoderContext(null);
        Deserializer<String> deserializer = ContextBoundSerdes.specialize(boundDeserializer(c -> record(created, new TokenDeserializer(c))), first);

        assertEquals("token", deserializer.deserialize(DECODER, first, STRING));
        assertEquals("nullable", deserializer.deserializeNullable(DECODER, first.withFeatures(Set.of(DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY), Set.of()), STRING));
        assertEquals("default", deserializer.getDefaultValue(first, STRING));
        assertEquals(1, created.size());

        Deserializer.DecoderContext second = registry.newDecoderContext(null);
        deserializer.deserialize(DECODER, second.withFeatures(Set.of(DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY), Set.of()), STRING);
        deserializer.deserialize(DECODER, second, STRING);
        assertEquals(2, created.size());
        assertFalse(deserializer instanceof UpdatingDeserializer);
    }

    @Test
    void deserializerReportsAFailedCreation() throws Exception {
        Deserializer.DecoderContext context = proxy(Deserializer.DecoderContext.class, registry.newDecoderContext(null));
        Deserializer<String> deserializer = ContextBoundSerdes.specialize(failingDeserializerAfterFirst(TokenDeserializer::new), context);
        assertThrows(IllegalStateException.class, () -> deserializer.getDefaultValue(context, STRING));
        assertThrows(SerdeException.class, () -> deserializer.deserialize(DECODER, context, STRING));
    }

    @Test
    void updatingDeserializerKeepsUpdating() throws Exception {
        Deserializer.DecoderContext first = registry.newDecoderContext(null);
        int[] calls = {0};
        Deserializer<String> deserializer = ContextBoundSerdes.specialize(boundDeserializer(c -> calls[0]++ == 0 ? new TokenUpdatingDeserializer(c) : new TokenDeserializer(c)), first);
        UpdatingDeserializer<String> updating = assertInstanceOf(UpdatingDeserializer.class, deserializer);
        updating.deserializeInto(DECODER, first, STRING, "a");
        assertEquals(1, TokenUpdatingDeserializer.updated.size());

        Deserializer.DecoderContext second = registry.newDecoderContext(null);
        assertThrows(SerdeException.class, () -> updating.deserializeInto(DECODER, second, STRING, "b"));
    }

    private static <T> T record(List<? super T> created, T serde) {
        created.add(serde);
        return serde;
    }

    private static ContextBoundSerdes.SerializerFactory<String> bound(Function<Serializer.EncoderContext, Serializer<String>> factory) {
        return context -> {
            ContextBoundSerdes.created();
            return factory.apply(context);
        };
    }

    private static ContextBoundSerdes.DeserializerFactory<String> boundDeserializer(Function<Deserializer.DecoderContext, Deserializer<String>> factory) {
        return context -> {
            ContextBoundSerdes.created();
            return factory.apply(context);
        };
    }

    private static ContextBoundSerdes.SerializerFactory<String> failingAfterFirst(Function<Serializer.EncoderContext, Serializer<String>> factory) {
        int[] calls = {0};
        return context -> {
            if (calls[0]++ > 0) {
                throw new SerdeException("Cannot create");
            }
            ContextBoundSerdes.created();
            return factory.apply(context);
        };
    }

    private static ContextBoundSerdes.DeserializerFactory<String> failingDeserializerAfterFirst(Function<Deserializer.DecoderContext, Deserializer<String>> factory) {
        int[] calls = {0};
        return context -> {
            if (calls[0]++ > 0) {
                throw new SerdeException("Cannot create");
            }
            ContextBoundSerdes.created();
            return factory.apply(context);
        };
    }

    /**
     * A proxy that forwards to the target, so that the proxy itself keeps no document serdes.
     */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Object target) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if (target == null) {
                return null;
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    static class TokenSerializer implements Serializer<String> {
        final Serializer.EncoderContext context;
        final List<String> values = new ArrayList<>();

        TokenSerializer(Serializer.EncoderContext context) {
            this.context = context;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends String> type, String value) {
            values.add(value);
        }

        @Override
        public boolean isEmpty(EncoderContext context, String value) {
            return value != null && value.isEmpty();
        }

        @Override
        public boolean isAbsent(EncoderContext context, String value) {
            return "absent".equals(value);
        }

        @Override
        public boolean isDefault(EncoderContext context, String value) {
            return "default".equals(value);
        }
    }

    static final class TokenObjectSerializer extends TokenSerializer implements ObjectSerializer<String> {
        TokenObjectSerializer(Serializer.EncoderContext context) {
            super(context);
        }

        @Override
        public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends String> type, String value) {
            values.add("into:" + value);
        }
    }

    static class TokenDeserializer implements Deserializer<String> {
        final Deserializer.DecoderContext context;

        TokenDeserializer(Deserializer.DecoderContext context) {
            this.context = context;
        }

        @Override
        public String deserialize(Decoder decoder, DecoderContext context, Argument<? super String> type) {
            return "token";
        }

        @Override
        public String deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super String> type) {
            return "nullable";
        }

        @Override
        public String getDefaultValue(DecoderContext context, Argument<? super String> type) {
            return "default";
        }
    }

    static final class TokenUpdatingDeserializer extends TokenDeserializer implements UpdatingDeserializer<String> {
        static final List<String> updated = new ArrayList<>();

        TokenUpdatingDeserializer(Deserializer.DecoderContext context) {
            super(context);
        }

        @Override
        public void deserializeInto(Decoder decoder, DecoderContext context, Argument<? super String> type, String value) {
            updated.add(value);
        }
    }
}
