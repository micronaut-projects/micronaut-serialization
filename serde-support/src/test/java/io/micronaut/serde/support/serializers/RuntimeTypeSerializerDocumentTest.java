package io.micronaut.serde.support.serializers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.DocumentSerdes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTypeSerializerDocumentTest {

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
    void documentContextsKeepTheValuesOfTheirOwners() {
        Object owner = new Object();
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer.EncoderContext derivedEncoderContext = encoderContext.withFeatures(Set.of(SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES), Set.of());
        assertNull(DocumentSerdes.get(encoderContext, owner));
        DocumentSerdes.put(derivedEncoderContext, owner, "encoded");
        assertEquals("encoded", DocumentSerdes.get(encoderContext, owner));
        assertEquals("encoded", DocumentSerdes.get(derivedEncoderContext, owner));
        assertNull(DocumentSerdes.get(registry.newEncoderContext(null), owner));

        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer.DecoderContext derivedDecoderContext = decoderContext.withFeatures(Set.of(DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY), Set.of());
        assertNull(DocumentSerdes.get(derivedDecoderContext, owner));
        DocumentSerdes.put(derivedDecoderContext, owner, "decoded");
        assertEquals("decoded", DocumentSerdes.get(decoderContext, owner));
        assertEquals("decoded", DocumentSerdes.get(derivedDecoderContext, owner));
    }

    @Test
    void contextWithoutDocumentSerdesKeepsNothing() {
        Object owner = new Object();
        Serializer.EncoderContext context = proxy(Serializer.EncoderContext.class, registry.newEncoderContext(null));
        DocumentSerdes.put(context, owner, "value");
        assertNull(DocumentSerdes.get(context, owner));
    }

    @Test
    void serializersOfRuntimeTypesWithoutContextualSerdesAreSharedByDocuments() throws Exception {
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        RuntimeTypeSerializer serializer = new RuntimeTypeSerializer(context, null, null, Argument.OBJECT_ARGUMENT);
        assertFalse(serializer.isEmpty(context, "value"));
        assertFalse(serializer.isAbsent(context, "value"));
        java.lang.reflect.Field field = RuntimeTypeSerializer.class.getDeclaredField("serializers");
        field.setAccessible(true);
        Map<?, ?> serializers = (Map<?, ?>) field.get(serializer);
        Object stringSerializer = serializers.get(String.class);

        Serializer.EncoderContext nextDocument = registry.newEncoderContext(null);
        serializer.isEmpty(nextDocument, "other");
        assertSame(stringSerializer, serializers.get(String.class));
        assertNull(DocumentSerdes.get(nextDocument, serializer));
    }

    @Test
    void runtimeTypeSerializersWorkWithContextsWithoutDocumentSerdes() {
        Serializer.EncoderContext context = proxy(Serializer.EncoderContext.class, registry.newEncoderContext(null));
        RuntimeTypeSerializer serializer = new RuntimeTypeSerializer(context, null, null, Argument.OBJECT_ARGUMENT);
        assertFalse(serializer.isEmpty(context, "value"));
        assertTrue(serializer.isEmpty(context, null));
    }

    @Test
    void outerTypeWithoutSerializerIsReported() {
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        Encoder encoder = proxy(Encoder.class, null);
        RuntimeTypeSerializer withoutSerializer = new RuntimeTypeSerializer(context, null, null, Argument.of(Unknown.class));
        assertFalse(withoutSerializer.isEmpty(context, new Unknown()));
        assertFalse(withoutSerializer.isAbsent(context, new Unknown()));
        SerdeException noSerializer = assertThrows(SerdeException.class, () -> withoutSerializer.serialize(encoder, context, Argument.OBJECT_ARGUMENT, new Unknown()));
        assertTrue(noSerializer.getMessage().contains("No serializer found"));

        RuntimeTypeSerializer notIntrospected = new RuntimeTypeSerializer(context, new IntrospectionException("Not introspected"), Argument.of(Unknown.class));
        SerdeException failure = assertThrows(SerdeException.class, () -> notIntrospected.serialize(encoder, context, Argument.OBJECT_ARGUMENT, new Unknown()));
        assertEquals("Not introspected", failure.getMessage());
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

    static final class Unknown {
    }
}
