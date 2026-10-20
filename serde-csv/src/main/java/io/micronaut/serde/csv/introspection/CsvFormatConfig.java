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

import java.util.List;

/**
 * Immutable, backend independent CSV format resolved from a bean's
 * {@link io.micronaut.core.beans.BeanIntrospection} metadata.
 *
 * @param configured      Whether the type carried an explicit {@link CsvFormat} annotation
 * @param header          The header mode
 * @param explicitColumns Whether the columns were explicitly configured through an annotation
 * @param columns         The ordered column names
 * @param delimiter       The column delimiter
 * @param quote           The quote character
 * @param escape          The escape character
 * @param lineSeparator   The line separator
 * @param nullValue       The value representing {@code null}
 * @param skipEmptyLines  Whether empty lines are skipped while reading
 * @param trim            Whether column values are trimmed
 * @param shape           The record shape constraint
 * @since 3.2.5
 */
public record CsvFormatConfig(
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

    /**
     * The default format used when a type has no CSV annotations.
     */
    public static final CsvFormatConfig DEFAULT = new CsvFormatConfig(
        false,
        CsvFormat.Header.INFER,
        false,
        List.of(),
        ',',
        '"',
        '"',
        "\n",
        "",
        true,
        false,
        CsvFormat.Shape.INFER
    );

    /**
     * Creates a config, defensively copying the columns.
     */
    public CsvFormatConfig {
        columns = List.copyOf(columns);
    }

    /**
     * @return Whether the format uses a header row for reading
     */
    public boolean hasHeader() {
        return header != CsvFormat.Header.ABSENT;
    }

    /**
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
        return new CsvFormatConfig(
            configured,
            header,
            explicit,
            columns,
            delimiter,
            quote,
            escape,
            lineSeparator,
            nullValue,
            skipEmptyLines,
            trim,
            shape
        );
    }
}
