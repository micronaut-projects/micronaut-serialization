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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.naming.IdentityStrategy;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * A Serde is an annotation that can be applied to type to indicate that
 * it is allowed to be serialized and deserialized to and from a format like JSON.
 *
 * <p>This annotation is meta-annotated with {@link Serdeable.Serializable} and
 * {@link Serdeable.Deserializable} which allow a type to be either serializable or deserializable</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Introspected
@SerdeConfig
@Serdeable.Serializable
@Serdeable.Deserializable
public @interface Serdeable {
    /**
     * @return Whether build time validation should fail compilation on definition errors.
     */
    @AliasFor(annotation = SerdeConfig.class, member =SerdeConfig.VALIDATE)
    boolean validate() default true;

    /**
     * @return Naming strategy to use for both serialization and deserialization.
     */
    @AliasFor(annotation = SerdeConfig.class, member = SerdeConfig.NAMING)
    Class<? extends PropertyNamingStrategy> naming() default IdentityStrategy.class;

    /**
     * Annotation used to indicate a type is serializable.
     */
    @Introspected(indexed = @Introspected.IndexedAnnotation(annotation = SerdeConfig.SerValue.class))
    @SerdeConfig
    @interface Serializable {
        /**
         * @return Whether serialization is enabled. Defaults to true.
         */
        boolean enabled() default true;

        /**
         * @return The {@link io.micronaut.serde.Serializer} to use.
         */
        Class<? extends Serializer> serializer() default Serializer.class;

        /**
         * @return Whether build time validation should fail compilation on definition errors.
         */
        @AliasFor(annotation = SerdeConfig.class, member = SerdeConfig.VALIDATE)
        boolean validate() default true;

        /**
         * Use the given class to serialize this type.
         * @return A type that is a subclass of the annotated type.
         */
        @AliasFor(annotation = SerdeConfig.class, member = SerdeConfig.SERIALIZE_AS)
        Class<?> as() default void.class;
    }

    /**
     * Annotation used to indicate a type is deserializable.
     */
    @SerdeConfig
    @interface Deserializable {
        /**
         * @return Whether serialization is enabled. Defaults to true.
         */
        boolean enabled() default true;

        /**
         * @return The deserializer.
         */
        Class<? extends Deserializer> deserializer() default Deserializer.class;

        /**
         * @return Whether build time validation should fail compilation on definition errors.
         */
        @AliasFor(annotation = SerdeConfig.class, member = SerdeConfig.VALIDATE)
        boolean validate() default true;

        /**
         * Use the given class to deserialize this type.
         * @return A type that is a subclass of the annotated type.
         */
        @AliasFor(annotation = SerdeConfig.class, member = SerdeConfig.DESERIALIZE_AS)
        Class<?> as() default void.class;
    }
}
