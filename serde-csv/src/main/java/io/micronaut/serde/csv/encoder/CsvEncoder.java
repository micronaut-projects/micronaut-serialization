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

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Encodes values to CSV strings.
 * @since 3.2.5
 */
public interface CsvEncoder {

    /**
     * Encodes a single value as a CSV line.
     *
     * @param value The value to encode
     * @param config The CSV format configuration
     * @return The encoded CSV line as a list of cell values; individual cells may be {@code null}
     * @throws CsvException If the value cannot be encoded
     */
    List<@Nullable String> encodeLine(@Nullable Object value, CsvFormatConfig config) throws CsvException;

    /**
     * Encodes values as a CSV string.
     *
     * @param values The values to encode
     * @param config The CSV format configuration
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    String encodeToString(Iterable<?> values, CsvFormatConfig config) throws CsvException;

    /**
     * Encodes values as a CSV string with an explicit header.
     *
     * @param values The values to encode
     * @param header The explicit header columns
     * @param config The CSV format configuration
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    String encodeToString(Iterable<?> values, Collection<String> header, CsvFormatConfig config) throws CsvException;

    /**
     * Encodes values as CSV and writes to an output stream.
     *
     * @param outputStream The output stream
     * @param values       The values to encode
     * @param charset      The charset to use
     * @param config       The CSV format configuration
     * @throws CsvException If the values cannot be encoded or written
     */
    default void writeValues(OutputStream outputStream, Iterable<?> values, Charset charset, CsvFormatConfig config) throws CsvException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        String csv = encodeToString(values, config);
        try {
            outputStream.write(csv.getBytes(charset));
        } catch (Exception e) {
            throw new CsvException("Failed to write CSV to output stream", e);
        }
    }
}
