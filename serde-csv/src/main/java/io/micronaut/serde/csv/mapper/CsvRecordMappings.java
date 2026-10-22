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
package io.micronaut.serde.csv.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between CSV column names and bean property names so that both CSV backends apply
 * the same column mapping, including {@link io.micronaut.serde.csv.annotation.CsvProperty}
 * renames, ordering, and ignored properties.
 *
 * @since 3.2.5
 */
@Internal
public final class CsvRecordMappings {

    private CsvRecordMappings() {
    }

    /**
     * Converts a record keyed by CSV column names to a record keyed by bean property names.
     *
     * @param config The resolved CSV format
     * @param values The values keyed by CSV column name
     * @return The values keyed by bean property name
     */
    public static Map<String, @Nullable String> toPropertyMap(CsvFormatConfig config, Map<String, @Nullable String> values) {
        List<String> columns = config.columns();
        List<String> propertyNames = config.propertyNames();
        if (columns.isEmpty() || columns.size() != propertyNames.size()) {
            return normalizeValues(config, values);
        }
        Map<String, @Nullable String> result = new LinkedHashMap<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            if (values.containsKey(column)) {
                result.put(propertyNames.get(i), normalize(config, values.get(column)));
            }
        }
        return result;
    }

    /**
     * Converts a record keyed by bean property names to a record keyed by CSV column names.
     *
     * @param config The resolved CSV format
     * @param values The values keyed by bean property name, or by CSV column name
     * @return The values keyed by CSV column name
     */
    public static Map<String, @Nullable String> toColumnMap(CsvFormatConfig config, Map<String, ?> values) {
        List<String> columns = config.columns();
        if (columns.isEmpty()) {
            Map<String, @Nullable String> result = new LinkedHashMap<>(values.size());
            values.forEach((key, value) -> result.put(key, stringify(value)));
            return result;
        }
        List<String> propertyNames = config.propertyNames();
        Map<String, @Nullable String> result = new LinkedHashMap<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            String propertyName = propertyNames.size() == columns.size() ? propertyNames.get(i) : column;
            Object value = values.containsKey(propertyName) ? values.get(propertyName) : values.get(column);
            result.put(column, stringify(value));
        }
        return result;
    }

    /**
     * Projects a record onto the configured CSV columns, preserving column order.
     *
     * @param config The resolved CSV format
     * @param values The values keyed by bean property name, or by CSV column name
     * @return The values in CSV column order
     */
    public static List<@Nullable String> toColumnValues(CsvFormatConfig config, Map<String, ?> values) {
        return new ArrayList<>(toColumnMap(config, values).values());
    }

    /**
     * Expands property values that are encoded as inline CSV strings.
     *
     * @param config  The resolved CSV format
     * @param values  The values keyed by bean property name
     * @param decoder The backend-specific inline decoder
     * @return The values with inline properties replaced by nested property maps
     * @throws CsvException If an inline value cannot be decoded
     */
    public static Map<String, Object> expandInlineProperties(
        CsvFormatConfig config,
        Map<String, @Nullable String> values,
        InlinePropertyDecoder decoder
    ) throws CsvException {
        Map<String, Object> result = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, @Nullable String> entry : values.entrySet()) {
            String propertyName = entry.getKey();
            CsvFormatConfig.CsvPropertyFormat format = config.propertyFormat(propertyName);
            String value = entry.getValue();
            if (format != null && value != null) {
                result.put(propertyName, decoder.decode(value, format));
            } else {
                result.put(propertyName, value);
            }
        }
        return result;
    }

    /**
     * Collapses property values that use an inline CSV format into strings.
     *
     * @param config  The resolved CSV format
     * @param values  The values keyed by bean property name
     * @param encoder The backend-specific inline encoder
     * @return The values with inline properties replaced by encoded strings
     * @throws CsvException If an inline value cannot be encoded
     */
    public static Map<String, Object> collapseInlineProperties(
        CsvFormatConfig config,
        Map<String, ?> values,
        InlinePropertyEncoder encoder
    ) throws CsvException {
        Map<String, Object> result = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String propertyName = entry.getKey();
            CsvFormatConfig.CsvPropertyFormat format = config.propertyFormat(propertyName);
            Object value = entry.getValue();
            if (format != null && value != null) {
                result.put(propertyName, encoder.encode(value, format));
            } else {
                result.put(propertyName, value);
            }
        }
        return result;
    }

    /**
     * Converts a positional row to a record keyed by bean property names.
     *
     * @param config The resolved CSV format
     * @param values The positional values
     * @return The values keyed by bean property name
     */
    public static Map<String, @Nullable String> rowToPropertyMap(CsvFormatConfig config, List<String> values) {
        List<String> columns = config.columns();
        List<String> propertyNames = config.propertyNames();
        int size = Math.min(columns.size(), propertyNames.size());
        Map<String, @Nullable String> result = new LinkedHashMap<>(size);
        for (int i = 0; i < size && i < values.size(); i++) {
            result.put(propertyNames.get(i), normalize(config, values.get(i)));
        }
        return result;
    }

    private static Map<String, @Nullable String> normalizeValues(CsvFormatConfig config, Map<String, @Nullable String> values) {
        Map<String, @Nullable String> result = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> result.put(key, normalize(config, value)));
        return result;
    }

    private static @Nullable String normalize(CsvFormatConfig config, @Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = config.trim() ? value.trim() : value;
        return normalized.equals(config.nullValue()) ? null : normalized;
    }

    private static @Nullable String stringify(@Nullable Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Decodes an inline CSV property value into a nested property map.
     */
    @Internal
    @FunctionalInterface
    public interface InlinePropertyDecoder {

        /**
         * Decodes an inline CSV property value into a nested property map.
         *
         * @param value  The encoded inline value
         * @param format The property format
         * @return The nested property map
         * @throws CsvException If the value cannot be decoded
         */
        Map<String, Object> decode(String value, CsvFormatConfig.CsvPropertyFormat format) throws CsvException;
    }

    /**
     * Encodes an inline CSV property value from a nested value.
     */
    @Internal
    @FunctionalInterface
    public interface InlinePropertyEncoder {

        /**
         * Encodes an inline CSV property value from a nested value.
         *
         * @param value  The nested value
         * @param format The property format
         * @return The encoded inline value
         * @throws CsvException If the value cannot be encoded
         */
        String encode(Object value, CsvFormatConfig.CsvPropertyFormat format) throws CsvException;
    }
}
