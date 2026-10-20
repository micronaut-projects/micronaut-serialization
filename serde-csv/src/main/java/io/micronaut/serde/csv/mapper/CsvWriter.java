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

import io.micronaut.serde.csv.error.CsvException;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Writes typed values as CSV data.
 * @since 3.2.5
 */
public interface CsvWriter {

    /**
     * Encodes a value as a single CSV line.
     *
     * @param value The value to encode
     * @return The encoded line as a list of cell values
     * @throws CsvException If the value cannot be encoded
     */
    List<String> writeAsLine(@Nullable Object value) throws CsvException;

    /**
     * Encodes an iterable as a CSV string.
     *
     * @param values The values to encode
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    String writeToString(Iterable<?> values) throws CsvException;

    /**
     * Encodes an iterable as a CSV string with an explicit header.
     *
     * @param values The values to encode
     * @param header The explicit header columns
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    String writeToString(Iterable<?> values, Collection<String> header) throws CsvException;

    /**
     * Encodes a value as CSV and writes to an output stream.
     *
     * @param outputStream The output stream
     * @param value        The value to encode
     * @param charset      The charset to use
     * @throws CsvException If the value cannot be encoded or written
     */
    default void writeValue(OutputStream outputStream, @Nullable Object value, Charset charset) throws CsvException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        if (value == null) {
            return;
        }
        String csv = writeToString(List.of(value));
        try {
            outputStream.write(csv.getBytes(charset));
        } catch (Exception e) {
            throw new CsvException("Failed to write CSV to output stream", e);
        }
    }

    /**
     * Encodes an iterable as CSV and writes to an output stream.
     *
     * @param outputStream The output stream
     * @param values       The values to encode
     * @param charset      The charset to use
     * @throws CsvException If the values cannot be encoded or written
     */
    default void writeValues(OutputStream outputStream, Iterable<?> values, Charset charset) throws CsvException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        String csv = writeToString(values);
        try {
            outputStream.write(csv.getBytes(charset));
        } catch (Exception e) {
            throw new CsvException("Failed to write CSV to output stream", e);
        }
    }
}
