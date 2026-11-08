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
import io.micronaut.serde.csv.error.CsvMappingException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
     * <p>The given map is mutated in place when possible, so callers must not share it
     * between threads.</p>
     *
     * @param config The resolved CSV format
     * @param values The values keyed by CSV column name; may be mutated and returned
     * @return The values keyed by bean property name
     */
    public static Map<String, @Nullable String> toPropertyMap(CsvFormatConfig config, Map<String, @Nullable String> values) {
        return toPropertyMap(config, values, new ColumnMapping(config));
    }

    /**
     * Converts a record keyed by CSV column names to a record keyed by bean property names,
     * using a precomputed {@link ColumnMapping}.
     *
     * <p>The mapping is computed once per resolved {@link CsvFormatConfig} and reused for every
     * row. Only columns that are actually renamed are touched, so a partially mapped bean no
     * longer forces a full map copy. Values are normalized in place and the given map is
     * returned when possible.</p>
     *
     * <p>The given map is mutated in place when possible, so callers must not share it
     * between threads.</p>
     *
     * @param config  The resolved CSV format
     * @param values  The values keyed by CSV column name; may be mutated and returned
     * @param mapping The precomputed column mapping for {@code config}
     * @return The values keyed by bean property name
     * @since 3.3.0
     */
    public static Map<String, @Nullable String> toPropertyMap(CsvFormatConfig config,
                                                              Map<String, @Nullable String> values,
                                                              ColumnMapping mapping) {
        if (!mapping.columnar) {
            return normalizeValues(config, values);
        }
        for (Map.Entry<String, @Nullable String> entry : values.entrySet()) {
            entry.setValue(normalize(config, entry.getValue()));
        }
        if (mapping.identity) {
            return values;
        }
        // Renaming in place is only safe when no target property name is also a column name,
        // otherwise a renamed key could be picked up again as a source in a later iteration.
        if (!mapping.inPlaceSafe) {
            return rebuildPropertyMap(mapping, values);
        }
        for (int index : mapping.renamed) {
            if (values.containsKey(mapping.columns[index]) && values.containsKey(mapping.propertyNames[index])) {
                return rebuildPropertyMap(mapping, values);
            }
        }
        for (int index : mapping.renamed) {
            String column = mapping.columns[index];
            if (values.containsKey(column)) {
                values.put(mapping.propertyNames[index], values.remove(column));
            }
        }
        return values;
    }

    /**
     * Precomputes the per-column mapping for a resolved CSV format.
     *
     * @param config The resolved CSV format
     * @return The column mapping
     * @since 3.3.0
     */
    public static ColumnMapping mapping(CsvFormatConfig config) {
        return new ColumnMapping(config);
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
     * Projects a record onto explicit CSV columns, preserving the given column order.
     *
     * @param config  The resolved CSV format
     * @param values  The values keyed by bean property name, or by CSV column name
     * @param columns The CSV columns to project
     * @return The values keyed by the given CSV column names
     */
    public static Map<String, @Nullable String> toColumnMap(CsvFormatConfig config,
                                                            Map<String, ?> values,
                                                            Collection<String> columns) {
        List<String> configColumns = config.columns();
        List<String> propertyNames = config.propertyNames();
        Map<String, @Nullable String> result = new LinkedHashMap<>(columns.size());
        for (String column : columns) {
            int index = configColumns.indexOf(column);
            String propertyName = index >= 0 && index < propertyNames.size() ? propertyNames.get(index) : column;
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

    /**
     * Rejects a null CSV record value.
     *
     * @param value The record value
     * @throws CsvException If the value is {@code null}
     */
    public static void requireRecord(@Nullable Object value) throws CsvException {
        if (value == null) {
            throw new CsvMappingException("Cannot convert null to a CSV record.", Object.class);
        }
    }

    /**
     * Normalizes a raw record map so that all keys are non-null strings.
     *
     * @param map The raw record map
     * @return The record map with string keys
     * @throws CsvException If a key is {@code null}
     */
    public static Map<String, ?> toStringKeyedMap(Map<?, ?> map) throws CsvException {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                throw new CsvMappingException("CSV record keys cannot be null.", Map.class);
            }
            result.put(key.toString(), entry.getValue());
        }
        return result;
    }

    private static Map<String, @Nullable String> normalizeValues(CsvFormatConfig config, Map<String, @Nullable String> values) {
        Map<String, @Nullable String> result = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> result.put(key, normalize(config, value)));
        return result;
    }

    private static Map<String, @Nullable String> rebuildPropertyMap(ColumnMapping mapping,
                                                                    Map<String, @Nullable String> values) {
        Map<String, @Nullable String> result = new LinkedHashMap<>(mapping.columns.length);
        for (int i = 0; i < mapping.columns.length; i++) {
            String column = mapping.columns[i];
            if (values.containsKey(column)) {
                result.put(mapping.propertyNames[i], values.get(column));
            }
        }
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
     * Precomputed column-to-property mapping for a resolved CSV format.
     *
     * <p>Instances are immutable and can be cached per {@link CsvFormatConfig}, so the
     * column/property comparison and the list of renamed columns are computed once instead of
     * on every row.</p>
     *
     * @since 3.3.0
     */
    @Internal
    public static final class ColumnMapping {

        private final String[] columns;
        private final String[] propertyNames;
        private final int[] renamed;
        private final boolean identity;
        private final boolean columnar;
        private final boolean inPlaceSafe;

        private ColumnMapping(CsvFormatConfig config) {
            List<String> configColumns = config.columns();
            List<String> configPropertyNames = config.propertyNames();
            this.columnar = !configColumns.isEmpty() && configColumns.size() == configPropertyNames.size();
            if (!columnar) {
                this.columns = new String[0];
                this.propertyNames = new String[0];
                this.renamed = new int[0];
                this.identity = false;
                this.inPlaceSafe = false;
                return;
            }
            this.columns = configColumns.toArray(String[]::new);
            this.propertyNames = configPropertyNames.toArray(String[]::new);
            int[] renamedIndexes = new int[this.columns.length];
            int renamedCount = 0;
            for (int i = 0; i < this.columns.length; i++) {
                if (!this.columns[i].equals(this.propertyNames[i])) {
                    renamedIndexes[renamedCount++] = i;
                }
            }
            this.renamed = renamedCount == renamedIndexes.length
                ? renamedIndexes
                : Arrays.copyOf(renamedIndexes, renamedCount);
            this.identity = renamedCount == 0;
            this.inPlaceSafe = identity || !collidesWithColumn(renamed, this.columns, this.propertyNames);
        }

        private static boolean collidesWithColumn(int[] renamed, String[] columns, String[] propertyNames) {
            for (int index : renamed) {
                String propertyName = propertyNames[index];
                for (String column : columns) {
                    if (column.equals(propertyName)) {
                        return true;
                    }
                }
            }
            return false;
        }
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
