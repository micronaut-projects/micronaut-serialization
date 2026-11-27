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
package io.micronaut.serde.csv;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared column/property-name resolution for CSV bean serdes. Both
 * {@link CsvBeanSerializer} and {@link CsvBeanDeserializer} delegate here so the
 * empty-columns fallback (use bean property names) is defined once.
 */
final class CsvColumnResolution {

    private CsvColumnResolution() {
    }

    static List<String> resolveColumns(CsvFormatConfig fmt, BeanIntrospection<?> introspection) {
        List<String> columns = fmt.columns();
        if (!columns.isEmpty()) {
            return columns;
        }
        List<String> resolved = new ArrayList<>(introspection.getBeanProperties().size());
        for (BeanProperty<?, ?> p : introspection.getBeanProperties()) {
            resolved.add(p.getName());
        }
        return resolved;
    }

    static List<String> resolvePropNames(CsvFormatConfig fmt, BeanIntrospection<?> introspection, List<String> columns) {
        if (!columns.isEmpty()) {
            return fmt.propertyNames();
        }
        List<String> resolved = new ArrayList<>(columns.size());
        for (BeanProperty<?, ?> p : introspection.getBeanProperties()) {
            resolved.add(p.getName());
        }
        return resolved;
    }
}
