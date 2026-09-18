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
package io.micronaut.serde.processor.sourcegen.records;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.List;

/**
 * Shape model for record-based source generation.
 *
 * @param canonicalConstructor The canonical constructor used for instantiation.
 * @param components The ordered record components.
 */
public record RecordSerdeShape(
    MethodElement canonicalConstructor,
    List<RecordComponent> components
) {
    /**
     * Record component metadata used by source generation.
     *
     * @param name The component name.
     * @param serializationName The component name used for serialization.
     * @param deserializationName The component name used for deserialization.
     * @param type The component type.
     * @param propertyElement The associated bean property element.
     */
    public record RecordComponent(
        String name,
        String serializationName,
        String deserializationName,
        ClassElement type,
        PropertyElement propertyElement
    ) {
    }
}
