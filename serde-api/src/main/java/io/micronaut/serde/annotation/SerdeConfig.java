package io.micronaut.serde.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.core.annotation.Internal;

/**
 * Meta-annotation with meta annotation members that different annotation
 * models can be bind to.
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
public @interface SerdeConfig {
    /**
     * is this field/property/method ignored.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface Ignored {}
}
