package io.micronaut.serde.support;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void aCreationBoundToTheContextBindsTheEnclosingCreation() {
        // Marks outside of a tracked creation are ignored
        SpecificSerdeCache.markContextBound();

        boolean outer = SpecificSerdeCache.beginCreation();
        boolean independent = SpecificSerdeCache.beginCreation();
        assertFalse(SpecificSerdeCache.endCreation(independent));
        boolean bound = SpecificSerdeCache.beginCreation();
        SpecificSerdeCache.markContextBound();
        assertTrue(SpecificSerdeCache.endCreation(bound));
        assertTrue(SpecificSerdeCache.endCreation(outer));

        boolean next = SpecificSerdeCache.beginCreation();
        assertFalse(SpecificSerdeCache.endCreation(next));
    }

    @Test
    void creationsAreTrackedPerThread() throws Exception {
        boolean outer = SpecificSerdeCache.beginCreation();
        Thread other = new Thread(() -> {
            boolean creation = SpecificSerdeCache.beginCreation();
            SpecificSerdeCache.markContextBound();
            SpecificSerdeCache.endCreation(creation);
        });
        other.start();
        other.join();
        assertFalse(SpecificSerdeCache.endCreation(outer));
    }

    private static AnnotationMetadata annotated() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addAnnotation("test.Marker", Map.of());
        return metadata;
    }
}
