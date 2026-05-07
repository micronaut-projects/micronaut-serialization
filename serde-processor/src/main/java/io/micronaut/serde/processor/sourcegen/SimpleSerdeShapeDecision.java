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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    Map<FallbackReason, String> serializerFallbackReasons,
    Map<FallbackReason, String> deserializerFallbackReasons
) {
    public SimpleSerdeShapeDecision {
        serializerFallbackReasons = Collections.unmodifiableMap(new LinkedHashMap<>(serializerFallbackReasons));
        deserializerFallbackReasons = Collections.unmodifiableMap(new LinkedHashMap<>(deserializerFallbackReasons));
    }

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
        UNWRAPPED("Unwrapped properties not supported"),
        ANY_GETTER("Any getter not supported"),
        ANY_SETTER("Any setter not supported"),
        /**
         * Shapes using {@code @JsonInclude} require introspection-backed handling of inclusion semantics.
         */
        INCLUDE("Include not supported"),
        PROPERTY_ORDER("Property order not supported"),
        UNSUPPORTED_ANNOTATIONS("Annotations not supported"),
        COMPLEX_CREATOR("Complex creator not supported"),
        COMPLEX_ENUM("Complex enum not supported"),
        SUBTYPED("Subtyped serialization not supported"),
        SOURCEGEN_SKIPPED("Source generation skipped"),
        UNSUPPORTED_SHAPE("Unsupported shape");

        private final String message;

        FallbackReason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
