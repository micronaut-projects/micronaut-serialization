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
package io.micronaut.serde.csv.decoder;

import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * Decodes CSV data into a specific representation.
 *
 * @param <T> The decoded element type
 * @since 3.2.5
 */
public interface CsvDecoder<T> {

    /**
     * Decodes a CSV string into values. Values are decoded lazily, and each iterator decodes the
     * string independently.
     *
     * @param value The CSV string
     * @param config The CSV format configuration
     * @return The decoded values
     * @throws CsvException If the value cannot be decoded
     */
    Iterable<T> decode(String value, CsvFormatConfig config) throws CsvException;

    /**
     * Decodes a CSV input stream into values. Values are decoded lazily. The returned iterable
     * is single-use because input streams cannot be replayed.
     *
     * @param input   The input stream
     * @param charset The charset
     * @param config  The CSV format configuration
     * @return The decoded values
     * @throws CsvException If the value cannot be decoded
     */
    Iterable<T> decode(InputStream input, Charset charset, CsvFormatConfig config) throws CsvException;
}
