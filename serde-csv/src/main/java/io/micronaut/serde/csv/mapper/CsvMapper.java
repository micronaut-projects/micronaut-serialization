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
import java.util.Collection;
import java.util.List;

/**
 * Main CSV mapper interface combining reading and writing capabilities.
 *
 * @see CsvReader
 * @see CsvWriter
 * @see CsvObjectMapper
 * @since 3.2.5
 */
public interface CsvMapper extends CsvReader, CsvWriter {

    /**
     * Reads a CSV input stream into typed values using a charset name. Values are parsed lazily.
     * The returned iterable is single-use because input streams cannot be replayed; consume it
     * once, on one thread.
     *
     * @param input   The input stream
     * @param charset The charset name
     * @param type    The element type
     * @param <T>     The element type
     * @return The decoded values
     * @throws CsvException If the value cannot be decoded
     */
    <T> Iterable<T> read(InputStream input, String charset, Class<T> type) throws CsvException;

    /**
     * Encodes a value as a single CSV line.
     *
     * @param value The value to encode
     * @return The encoded line
     * @throws CsvException If the value cannot be encoded
     */
    @Override
    List<String> writeAsLine(@Nullable Object value) throws CsvException;

    /**
     * Encodes an iterable as a CSV string.
     *
     * @param values The values to encode
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    @Override
    String writeToString(Iterable<?> values) throws CsvException;

    /**
     * Encodes an iterable as a CSV string with an explicit header.
     *
     * @param values The values to encode
     * @param header The explicit header columns
     * @return The encoded CSV string
     * @throws CsvException If the values cannot be encoded
     */
    @Override
    String writeToString(Iterable<?> values, Collection<String> header) throws CsvException;
}
