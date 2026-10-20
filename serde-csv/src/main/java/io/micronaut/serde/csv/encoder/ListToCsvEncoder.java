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
package io.micronaut.serde.csv.encoder;

import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Encoder that maps lines to CSV strings.
 * @since 3.2.5
 */
public interface ListToCsvEncoder extends CsvEncoder {

    /**
     * Encodes a single line as a CSV line.
     *
     * @param value The line (list of cell values)
     * @param config The CSV format configuration
     * @return The encoded CSV line as a list of cell values
     * @throws CsvException If the value cannot be encoded
     */
    @Override
    List<String> encodeLine(@Nullable Object value, CsvFormatConfig config) throws CsvException;

    /**
     * Encodes lines as a CSV string.
     *
     * @param values The lines (each a list of cell values)
     * @param config The CSV format configuration
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    @Override
    String encodeToString(Iterable<?> values, CsvFormatConfig config) throws CsvException;

    /**
     * Encodes lines as a CSV string with an explicit header.
     *
     * @param values The lines
     * @param header The explicit header columns
     * @param config The CSV format configuration
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    @Override
    String encodeToString(Iterable<?> values, Collection<String> header, CsvFormatConfig config) throws CsvException;
}
