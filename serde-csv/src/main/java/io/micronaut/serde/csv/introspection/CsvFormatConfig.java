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
package io.micronaut.serde.csv.introspection;

import io.micronaut.serde.csv.annotation.CsvFormat;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Immutable, backend independent CSV format resolved from a bean's
 * {@link io.micronaut.core.beans.BeanIntrospection} metadata.
 *
 * @param configured      Whether the type carried an explicit {@link CsvFormat} annotation
 * @param header          The header mode
 * @param explicitColumns Whether the columns were explicitly configured through an annotation
 * @param columns         The ordered CSV column names
 * @param propertyNames   The serialized bean property names aligned with {@code columns}
 * @param delimiter       The column delimiter
 * @param quote           The quote character
 * @param escape          The escape character
 * @param lineSeparator   The line separator
 * @param nullValue       The value representing {@code null}
 * @param skipEmptyLines  Whether empty lines are skipped while reading
 * @param trim            Whether column values are trimmed
 * @param shape           The record shape constraint
 * @param propertyFormats Formats for properties that are encoded inline
 * @since 3.2.5
 */
public record CsvFormatConfig(
    boolean configured,
    CsvFormat.Header header,
    boolean explicitColumns,
    List<String> columns,
    List<String> propertyNames,
    char delimiter,
    char quote,
    char escape,
    String lineSeparator,
    String nullValue,
    boolean skipEmptyLines,
    boolean trim,
    CsvFormat.Shape shape,
    Map<String, CsvPropertyFormat> propertyFormats
) {

    /**
     * The default format used when a type has no CSV annotations.
     */
    public static final CsvFormatConfig DEFAULT = new CsvFormatConfig(
        false,
        CsvFormat.Header.INFER,
        false,
        List.of(),
        List.of(),
        ',',
        '"',
        '"',
        "\n",
        "",
        true,
        false,
        CsvFormat.Shape.INFER,
        Map.of()
    );

    /**
     * Creates a config, defensively copying the columns and property formats.
     */
    public CsvFormatConfig {
        columns = List.copyOf(columns);
        propertyNames = List.copyOf(propertyNames);
        propertyFormats = Map.copyOf(propertyFormats);
    }

    /**
     * Creates a config without property-level inline formats.
     *
     * @param configured      Whether the type carried an explicit {@link CsvFormat} annotation
     * @param header          The header mode
     * @param explicitColumns Whether the columns were explicitly configured
     * @param columns         The ordered CSV column names
     * @param propertyNames   The serialized bean property names aligned with {@code columns}
     * @param delimiter       The column delimiter
     * @param quote           The quote character
     * @param escape          The escape character
     * @param lineSeparator   The line separator
     * @param nullValue       The value representing {@code null}
     * @param skipEmptyLines  Whether empty lines are skipped while reading
     * @param trim            Whether column values are trimmed
     * @param shape           The record shape constraint
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public CsvFormatConfig(
        boolean configured,
        CsvFormat.Header header,
        boolean explicitColumns,
        List<String> columns,
        List<String> propertyNames,
        char delimiter,
        char quote,
        char escape,
        String lineSeparator,
        String nullValue,
        boolean skipEmptyLines,
        boolean trim,
        CsvFormat.Shape shape
    ) {
        this(configured, header, explicitColumns, columns, propertyNames, delimiter, quote, escape,
            lineSeparator, nullValue, skipEmptyLines, trim, shape, Map.of());
    }

    /**
     * Creates a config without a separate property-name mapping.
     *
     * @param configured      Whether the type carried an explicit {@link CsvFormat} annotation
     * @param header          The header mode
     * @param explicitColumns Whether the columns were explicitly configured
     * @param columns         The ordered CSV column names
     * @param delimiter       The column delimiter
     * @param quote           The quote character
     * @param escape          The escape character
     * @param lineSeparator   The line separator
     * @param nullValue       The value representing {@code null}
     * @param skipEmptyLines  Whether empty lines are skipped while reading
     * @param trim            Whether column values are trimmed
     * @param shape           The record shape constraint
     */
    public CsvFormatConfig(
        boolean configured,
        CsvFormat.Header header,
        boolean explicitColumns,
        List<String> columns,
        char delimiter,
        char quote,
        char escape,
        String lineSeparator,
        String nullValue,
        boolean skipEmptyLines,
        boolean trim,
        CsvFormat.Shape shape
    ) {
        this(configured, header, explicitColumns, columns, columns, delimiter, quote, escape,
            lineSeparator, nullValue, skipEmptyLines, trim, shape, Map.of());
    }

    /**
     * Checks whether the format uses a header row for reading.
     *
     * @return Whether the format uses a header row for reading
     */
    public boolean hasHeader() {
        return header != CsvFormat.Header.ABSENT;
    }

    /**
     * Checks whether a header row should be emitted when writing.
     *
     * @return Whether a header row should be emitted when writing
     */
    public boolean writeHeader() {
        return explicitColumns || header == CsvFormat.Header.PRESENT;
    }

    /**
     * Returns a copy of this config with the given resolved columns and explicit-columns flag.
     *
     * @param explicit Whether the columns were explicitly configured
     * @param columns  The resolved columns
     * @return The new config
     */
    public CsvFormatConfig withColumns(boolean explicit, List<String> columns) {
        return withColumns(explicit, columns, columns);
    }

    /**
     * Returns a copy of this config with the given resolved columns and property names.
     *
     * @param explicit      Whether the columns were explicitly configured
     * @param columns       The resolved CSV column names
     * @param propertyNames The serialized bean property names aligned with {@code columns}
     * @return The new config
     */
    public CsvFormatConfig withColumns(boolean explicit, List<String> columns, List<String> propertyNames) {
        return new CsvFormatConfig(
            configured,
            header,
            explicit,
            columns,
            propertyNames,
            delimiter,
            quote,
            escape,
            lineSeparator,
            nullValue,
            skipEmptyLines,
            trim,
            shape,
            propertyFormats
        );
    }

    /**
     * Returns a copy of this config with the given property-level inline formats.
     *
     * @param formats The property formats keyed by bean or serialized property name
     * @return The new config
     */
    public CsvFormatConfig withPropertyFormats(Map<String, CsvPropertyFormat> formats) {
        return new CsvFormatConfig(
            configured,
            header,
            explicitColumns,
            columns,
            propertyNames,
            delimiter,
            quote,
            escape,
            lineSeparator,
            nullValue,
            skipEmptyLines,
            trim,
            shape,
            formats
        );
    }

    /**
     * Returns the inline format for a property.
     *
     * @param propertyName The bean or serialized property name
     * @return The property format, or {@code null} when the property is not inline encoded
     */
    public @Nullable CsvPropertyFormat propertyFormat(String propertyName) {
        return propertyFormats.get(propertyName);
    }

    /**
     * Inline format metadata for a bean property annotated with {@link CsvFormat}.
     *
     * @param type   The property type
     * @param config The inline CSV format
     */
    public record CsvPropertyFormat(Class<?> type, CsvFormatConfig config) {
    }
}
