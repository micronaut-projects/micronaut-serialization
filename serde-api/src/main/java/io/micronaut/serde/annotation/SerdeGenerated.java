package io.micronaut.serde.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Experimental
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SerdeGenerated {
    boolean allowDeserialization() default true;
    boolean allowSerialization() default true;
}
