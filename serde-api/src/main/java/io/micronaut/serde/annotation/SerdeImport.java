/*
 * Copyright 2017-2021 original authors
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Annotation to allow external classes to be enabled for serialization / deserialization.
 */
@Internal
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(SerdeImport.Repeated.class)
@SerdeConfig
public @interface SerdeImport {
    /**
     * Allows making an external class or interface serializable.
     *
     * <p>Note that mixins only work with publicly accessible members (constructors, getters/setters etc.),
     * anything not public will not be includes in the serialization/deserialization result.</p>
     *
     * @return The type to enable serialization for.
     */
    Class<?> value() default void.class;

    /**
     * Specifies a package name where types can be found. Only publicaly accessible types will be processed.
     *
     * @return The package name where types can be found to import.
     * @since 2.2.2
     */
    @Experimental
    String packageName() default "";

    /**
     * Apply a mixin type. This is equivalent to Jackson {@code objectMapper.addMixInAnnotations(..)} allowing to mixin
     * the annotations to the imported type.
     *
     * <p>Any fields or methods that match fields or methods declared in the type specified to {@link #value()} will have
     * the annotations applied to the imported type.</p>
     *
     * @return The mixin type
     */
    Class<?> mixin() default void.class;

    /**
     * @return Whether to import for serialization. Defaults to {@code true}.
     */
    boolean serializable() default true;

    /**
     * @return Whether to import for deserialization. Defaults to {@code true}.
     */
    boolean deserializable() default true;

    /**
     * Repeated wrapper for this annotation.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Repeated {
        SerdeImport[] value();
    }
}
