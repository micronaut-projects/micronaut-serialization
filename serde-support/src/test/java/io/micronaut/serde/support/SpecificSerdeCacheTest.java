package io.micronaut.serde.support;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.util.SpecificSerdeTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecificSerdeCacheTest {

    @Test
    void argumentsAreTheSameWhenTheirTypesNamesParametersAndMetadataAre() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        assertTrue(SpecificSerdeCache.sameArgument(Argument.of(String.class), Argument.of(String.class)));
        assertTrue(SpecificSerdeCache.sameArgument(Argument.listOf(String.class), Argument.listOf(String.class)));
        assertTrue(SpecificSerdeCache.sameArgument(
            Argument.of(String.class, "value", metadata),
            Argument.of(String.class, "value", metadata)));
        assertTrue(SpecificSerdeCache.sameArgument(
            Argument.of(String.class, "value", AnnotationMetadata.EMPTY_METADATA),
            Argument.of(String.class, "value", new MutableAnnotationMetadata())));

        assertFalse(SpecificSerdeCache.sameArgument(Argument.of(String.class), Argument.of(Integer.class)));
        assertFalse(SpecificSerdeCache.sameArgument(Argument.listOf(String.class), Argument.listOf(Integer.class)));
        assertFalse(SpecificSerdeCache.sameArgument(Argument.of(List.class), Argument.listOf(Integer.class)));
        assertFalse(SpecificSerdeCache.sameArgument(Argument.of(String.class, "a"), Argument.of(String.class, "b")));
        assertFalse(SpecificSerdeCache.sameArgument(
            Argument.of(String.class, "value", annotated()),
            Argument.of(String.class, "value", annotated())));
        assertFalse(SpecificSerdeCache.sameArgument(
            Argument.mapOf(Argument.of(String.class), Argument.of(String.class, "v", annotated())),
            Argument.mapOf(Argument.of(String.class), Argument.of(String.class, "v", annotated()))));
    }

    @Test
    void keepsSpecificSerdesUpToTheLimit() {
        SpecificSerdeCache<Object> cache = new SpecificSerdeCache<>();
        Object serde = new Object();
        Argument<String> argument = Argument.of(String.class);
        assertNull(cache.get(argument));
        cache.put(argument, serde);
        assertSame(serde, cache.get(argument));
        assertSame(serde, cache.get(Argument.of(String.class)));

        for (int i = 0; i < SpecificSerdeCache.MAX_ARGUMENTS * 2; i++) {
            Argument<String> annotated = Argument.of(String.class, "value", annotated());
            cache.put(annotated, new Object());
        }
        assertEquals(SpecificSerdeCache.MAX_ARGUMENTS, cache.size());
        Argument<String> extra = Argument.of(String.class, "value", annotated());
        cache.put(extra, serde);
        assertNull(cache.get(extra));
    }

    @Test
    void recordsArgumentsWhoseSpecificSerdeIsBoundToTheContext() {
        SpecificSerdeCache<Object> cache = new SpecificSerdeCache<>();
        Argument<String> bound = Argument.of(String.class);
        Argument<Integer> independent = Argument.of(Integer.class);
        Object serde = new Object();
        cache.putBound(bound);
        cache.put(independent, serde);

        assertTrue(SpecificSerdeCache.isBound(cache.get(bound)));
        assertTrue(SpecificSerdeCache.isBound(cache.get(Argument.of(String.class))));
        assertSame(serde, cache.get(independent));
        assertFalse(SpecificSerdeCache.isBound(cache.get(independent)));
        assertFalse(SpecificSerdeCache.isBound(null));
        assertNull(cache.get(Argument.of(Long.class)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aProbingContextRecordsWhatBindsTheCreationToTheContext() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            DefaultSerdeRegistry registry = context.getBean(DefaultSerdeRegistry.class);

            DefaultDecoderContext probing = DefaultDecoderContext.probing(registry);
            assertSame(registry.findDeserializer(Argument.STRING), probing.findDeserializer(Argument.STRING));
            assertFalse(probing.isBound());
            // The runtime object deserializer creates the deserializer of arbitrary values with a probing context
            Deserializer<?> objectDeserializer = probing.findDeserializer(Argument.OBJECT_ARGUMENT);
            assertNotSame(registry.findDeserializer(Argument.OBJECT_ARGUMENT), objectDeserializer);
            assertNotNull(objectDeserializer.createSpecific(probing, (Argument) Argument.OBJECT_ARGUMENT));
            assertFalse(probing.isBound());
            // but not the deserializer of a bean, which it keeps in descriptions shared by every document
            Argument<SpecificSerdeCacheTest> bean = Argument.of(SpecificSerdeCacheTest.class);
            probing.findDeserializer(Argument.OBJECT_ARGUMENT).createSpecific(probing, (Argument) bean);
            assertTrue(probing.isBound());

            DefaultDecoderContext marked = DefaultDecoderContext.probing(registry);
            SpecificSerdeTracker.markContextBound(marked.withFeatures(Set.of(DeserializationConfiguration.Feature.values()[0]), Set.of()));
            assertTrue(marked.isBound());

            DefaultDecoderContext custom = DefaultDecoderContext.probing(registry);
            assertThrows(SpecificSerdeCache.BoundToContextException.class, () -> custom.findCustomDeserializer(Deserializer.class));
            assertTrue(custom.isBound());

            DefaultEncoderContext probingEncoder = DefaultEncoderContext.probing(registry);
            assertSame(registry.findSerializer(Argument.STRING), probingEncoder.findSerializer(Argument.STRING));
            assertFalse(probingEncoder.isBound());
            probingEncoder.findSerializer(Argument.of(SpecificSerdeCacheTest.class));
            assertTrue(probingEncoder.isBound());

            DefaultDecoderContext document = (DefaultDecoderContext) registry.newDecoderContext(null);
            document.markContextBound();
            assertFalse(document.isBound());
        }
    }

    private static AnnotationMetadata annotated() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addAnnotation("test.Marker", Map.of());
        return metadata;
    }
}
