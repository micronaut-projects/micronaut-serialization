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
package io.micronaut.serde.processor.sourcegen.beans;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Shape model for bean-based source generation.
 *
 * @param defaultConstructor The selected default constructor.
 * @param properties         Bean properties included in generated serialization.
 */
public record BeanSerdeShape(
    MethodElement defaultConstructor,
    List<BeanProperty> properties
) {
    /**
     * Bean property metadata used by source generation.
     *
     * @param name                Property name.
     * @param serializationType   Property type used for serialization.
     * @param deserializationType Property type used for deserialization.
     * @param nullable            Whether the property is nullable.
     * @param readMethod          Bean getter method.
     * @param writeMethod         Bean setter method.
     * @param readField           Bean field used for reading.
     * @param writeField          Bean field used for writing.
     */
    public record BeanProperty(
        String name,
        ClassElement serializationType,
        ClassElement deserializationType,
        boolean nullable,
        @Nullable MethodElement readMethod,
        @Nullable MethodElement writeMethod,
        @Nullable FieldElement readField,
        @Nullable FieldElement writeField
    ) {
    }
}
