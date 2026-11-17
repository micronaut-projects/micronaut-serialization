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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Reads CSV data into typed values.
 * @since 3.2.5
 */
public interface CsvReader {

    /**
     * Reads a single CSV line into a typed value.
     *
     * @param value The CSV line as a list of cell values
     * @param type  The element type
     * @param <T>   The element type
     * @return The decoded value
     * @throws CsvException If the value cannot be decoded
     */
    <T> T read(List<String> value, Class<T> type) throws CsvException;

    /**
     * Reads multiple CSV lines into typed values. Rows are consumed lazily while the returned
     * iterable is iterated.
     *
     * @param values The CSV lines
     * @param type   The element type
     * @param <T>    The element type
     * @return The decoded values
     * @throws CsvException If the values cannot be decoded
     */
    <T> Iterable<T> read(Iterable<List<String>> values, Class<T> type) throws CsvException;

    /**
     * Reads a CSV string into typed values. Values are parsed lazily, and the returned iterable
     * may be iterated multiple times; each iterator parses the input independently.
     *
     * @param value The CSV string
     * @param type  The element type
     * @param <T>   The element type
     * @return The decoded values
     * @throws CsvException If the value cannot be decoded
     */
    <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException;

    /**
     * Reads a CSV input stream into typed values. Values are parsed lazily. The returned
     * iterable is single-use because input streams cannot be replayed; consume it once, on one
     * thread.
     *
     * @param input   The input stream
     * @param charset The charset
     * @param type    The element type
     * @param <T>     The element type
     * @return The decoded values
     * @throws CsvException If the value cannot be decoded
     */
    <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) throws CsvException;

    /**
     * Reads a single CSV row from a CSV string and returns the first element.
     *
     * @param value The CSV string
     * @param type  The element type
     * @param <T>   The element type
     * @return The first decoded value or {@code null} when the CSV is empty
     * @throws CsvException If the value cannot be decoded
     */
    default <T> @Nullable T read(String value, Class<T> type) throws CsvException {
        Iterable<T> records = readAll(value, type);
        var it = records.iterator();
        return it.hasNext() ? it.next() : null;
    }
}
