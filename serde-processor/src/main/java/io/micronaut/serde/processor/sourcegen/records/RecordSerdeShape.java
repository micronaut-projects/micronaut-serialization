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
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
     * @param serializedName The serialized component name.
     * @param type The component type.
     * @param keyMetadata Pre-resolved metadata contributed with the component key.
     * @param include The inclusion resolved at build time, or {@code null} when the configuration decides.
     * @param required Whether the component has to be present in the input.
     * @param aliases Additional names the component is read from.
     * @param nonNull Whether the component rejects null, declared on the parameter or the property.
     * @param nullable Whether the component accepts null, declared on the parameter or the property.
     * @param propertyElement The associated bean property element.
     */
    public record RecordComponent(
        String name,
        String serializedName,
        ClassElement type,
        Map<String, String> keyMetadata,
        SerdeConfig.@Nullable SerInclude include,
        boolean required,
        List<String> aliases,
        boolean nonNull,
        boolean nullable,
        PropertyElement propertyElement
    ) {
        /**
         * Creates a component whose serialized name matches its Java name and has no key metadata.
         *
         * @param name The component name.
         * @param type The component type.
         * @param propertyElement The associated bean property element.
         */
        public RecordComponent(String name, ClassElement type, PropertyElement propertyElement) {
            this(name, name, type, Map.of(), null, false, List.of(), propertyElement.isNonNull(), propertyElement.isNullable(), propertyElement);
        }
    }
}
