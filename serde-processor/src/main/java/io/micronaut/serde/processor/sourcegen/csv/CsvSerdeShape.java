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
package io.micronaut.serde.processor.sourcegen.csv;

import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * CSV-specific shape model for source generation. Wraps a {@link BeanSerdeShape} (which
 * carries the bean properties, constructor, inclusion, etc.) and adds the resolved CSV
 * column names and per-property inline format flags.
 *
 * @param beanShape        the underlying bean serde shape
 * @param columns          the resolved CSV column names
 * @param inlineProperties per-property inline format flags
 * @param propertyToColumn mapping from property names to CSV column names
 */
public record CsvSerdeShape(
    BeanSerdeShape beanShape,
    List<String> columns,
    Map<String, String> propertyToColumn,
    Map<String, Boolean> inlineProperties
) {
    public @Nullable String columnFor(String propertyName) {
        return propertyToColumn.get(propertyName);
    }

    public boolean isInline(String propertyName) {
        return Boolean.TRUE.equals(inlineProperties.get(propertyName));
    }
}
