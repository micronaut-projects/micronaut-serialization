package io.micronaut.serde.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Experimental
@Retention(RetentionPolicy.RUNTIME)
public @interface RecursiveSerialization {
}
