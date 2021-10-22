package io.micronaut.serde.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.micronaut.core.annotation.Internal;

/**
 * Meta-annotation with meta annotation members that different annotation
 * models can be bind to.
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
public @interface SerdeMeta {
}
