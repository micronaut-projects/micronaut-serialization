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
package io.micronaut.serde.processor.sourcegen.enums;

import java.util.HashSet;
import java.util.List;

/**
 * Shape model for enum-based source generation.
 *
 * @param constants Enum constants participating in generated mapping.
 * @param hasPropertyOverrides Whether any constant has an overridden serialized name.
 * @param serializedValues The serialized value of every constant, in declaration (ordinal) order.
 */
public record EnumSerdeShape(
    List<EnumConstant> constants,
    boolean hasPropertyOverrides,
    List<String> serializedValues
) {

    /**
     * Shape model without the serialized values; the generated code then can't match them.
     *
     * @param constants Enum constants participating in generated mapping.
     * @param hasPropertyOverrides Whether any constant has an overridden serialized name.
     * @deprecated Use {@link #EnumSerdeShape(List, boolean, List)}
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    public EnumSerdeShape(List<EnumConstant> constants, boolean hasPropertyOverrides) {
        this(constants, hasPropertyOverrides, List.of());
    }

    /**
     * Whether every constant has its own serialized value, which then identifies the constant in
     * both directions.
     *
     * @return Whether the serialized values are unique
     */
    public boolean hasUniqueSerializedValues() {
        return !serializedValues.isEmpty() && new HashSet<>(serializedValues).size() == serializedValues.size();
    }

    /**
     * Enum constant metadata used by source generation.
     *
     * @param name Enum constant name.
     * @param serializedValue Serialized value used for the constant.
     */
    public record EnumConstant(
        String name,
        String serializedValue
    ) {
    }
}
