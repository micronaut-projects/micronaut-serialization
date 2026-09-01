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
package io.micronaut.serde.protobuf.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Assigns a Protocol Buffers field number, and optionally a wire representation, to a property.
 *
 * <p>Field numbers are the identity of a property on the wire; names are never transmitted.
 * Every property of a message serialized by the protobuf backend must carry this annotation.</p>
 *
 * <p>This is prototype API and subject to change.</p>
 *
 * @since 3.2
 */
@Experimental
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
public @interface ProtoField {

    /**
     * The lowest legal Protocol Buffers field number.
     */
    int MIN_FIELD_NUMBER = 1;

    /**
     * The highest legal Protocol Buffers field number.
     */
    int MAX_FIELD_NUMBER = 536870911;

    /**
     * The field number. Must be between {@link #MIN_FIELD_NUMBER} and {@link #MAX_FIELD_NUMBER},
     * and must not fall in the reserved range 19000-19999.
     *
     * @return The field number
     */
    int value();

    /**
     * The wire representation to use. Defaults to {@link ProtoType#DEFAULT}, which picks the
     * representation from the Java type.
     *
     * @return The proto type
     */
    ProtoType type() default ProtoType.DEFAULT;
}
