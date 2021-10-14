package io.micronaut.json.annotation;

import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomSerializer {
    Class<? extends Serializer> serializer() default Serializer.class;

    Class<? extends Deserializer> deserializer() default Deserializer.class;
}
