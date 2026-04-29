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
package io.micronaut.serde.processor.sourcegen;

import java.util.EnumSet;

/**
 * Source-generation eligibility outcome for a candidate type.
 *
 * @param shapeKind The detected candidate shape kind.
 * @param serializerEligible Whether serializer source generation is supported for the type.
 * @param deserializerEligible Whether deserializer source generation is supported for the type.
 * @param serializerFallbackReasons Reasons serializer generation falls back to introspection.
 * @param deserializerFallbackReasons Reasons deserializer generation falls back to introspection.
 */
public record SimpleSerdeShapeDecision(
    ShapeKind shapeKind,
    boolean serializerEligible,
    boolean deserializerEligible,
    EnumSet<FallbackReason> serializerFallbackReasons,
    EnumSet<FallbackReason> deserializerFallbackReasons
) {
    /**
     * Supported simple shapes for source-generated serdes.
     */
    public enum ShapeKind {
        RECORD,
        DEFAULT_CONSTRUCTOR_BEAN,
        ENUM,
        UNSUPPORTED
    }

    /**
     * Reasons a type falls back to introspection-backed serde handling.
     */
    public enum FallbackReason {
        UNWRAPPED,
        ANY_GETTER,
        ANY_SETTER,
        /**
         * Shapes using {@code @JsonInclude} require introspection-backed handling of inclusion semantics.
         */
        INCLUDE,
        COMPLEX_CREATOR,
        COMPLEX_ENUM,
        SUBTYPED,
        UNSUPPORTED_SHAPE
    }
}
