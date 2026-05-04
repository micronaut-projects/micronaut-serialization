/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the compile-time generated serde contract for a {@link Serdeable} type.
 *
 * <p>Micronaut Serialization already attempts to generate compile-time serializers and
 * deserializers for simple eligible {@link Serdeable} shapes without this annotation. A shape is
 * currently eligible when it is a simple bean, record, or enum whose serialization model can be
 * represented directly by source generation. Features that require runtime behavior, such as
 * custom serde classes, custom naming or property mappings, unwrapped or subtyped properties,
 * any getters/setters, unsupported Jackson annotations, and complex enum or creator handling,
 * fall back to the runtime introspection serde.</p>
 *
 * <p>This annotation is used when the generated serde decision should be explicit. With the
 * default settings, compilation fails if either the serializer or deserializer cannot be
 * generated. Set {@link #required()} to {@code false} to allow normal runtime fallback while
 * still using generated serdes where they are eligible. Use {@link #skip()} to disable both
 * generated directions, or {@link #skipSerializer()} and {@link #skipDeserializer()} to opt out
 * of only one direction.</p>
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Serdeable
@Experimental
public @interface SerdeableGenerated {

    /**
     * Returns whether compilation should fail when a non-skipped serializer or deserializer cannot be generated.
     *
     * @return {@code true} if compilation should fail when generation is not possible
     */
    boolean required() default true;

    /**
     * Returns whether both generated directions should be skipped.
     *
     * @return {@code true} if both generated serializer and deserializer should be skipped
     */
    boolean skip() default false;

    /**
     * Returns whether the generated serializer should be skipped.
     *
     * @return {@code true} if the generated serializer should be skipped
     */
    boolean skipSerializer() default false;

    /**
     * Returns whether the generated deserializer should be skipped.
     *
     * @return {@code true} if the generated deserializer should be skipped
     */
    boolean skipDeserializer() default false;
}
