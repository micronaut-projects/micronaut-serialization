package io.micronaut.serde.support;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SpecificSerdeCacheTest {

    @Test
    void anEqualArgumentFindsTheKeptSerde() {
        SpecificSerdeCache<Object> cache = new SpecificSerdeCache<>();
        Object serde = new Object();
        cache.put(Argument.listOf(String.class), serde);

        assertSame(serde, cache.get(Argument.listOf(String.class)));
        assertNull(cache.get(Argument.listOf(Integer.class)));
        assertNull(cache.get(Argument.of(List.class)));
    }

    @Test
    void anArgumentWithOtherAnnotationMetadataIsAnotherArgument() {
        SpecificSerdeCache<Object> cache = new SpecificSerdeCache<>();
        AnnotationMetadata metadata = annotated();
        Argument<String> annotated = Argument.of(String.class, "value", metadata);
        Object serde = new Object();
        cache.put(annotated, serde);

        assertSame(serde, cache.get(Argument.of(String.class, "value", metadata)));
        assertNull(cache.get(Argument.of(String.class, "value", annotated())));
        assertNull(cache.get(Argument.of(String.class, "other", metadata)));
    }

    @Test
    void theNumberOfKeptArgumentsIsLimited() {
        SpecificSerdeCache<Object> cache = new SpecificSerdeCache<>();
        for (int i = 0; i < SpecificSerdeCache.MAX_ARGUMENTS + 10; i++) {
            cache.put(Argument.of(String.class, "value" + i), new Object());
        }

        assertEquals(SpecificSerdeCache.MAX_ARGUMENTS, cache.size());
        assertNull(cache.get(Argument.of(String.class, "value" + SpecificSerdeCache.MAX_ARGUMENTS)));
    }

    private static AnnotationMetadata annotated() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addAnnotation("test.Annotated", Map.of());
        return metadata;
    }
}
