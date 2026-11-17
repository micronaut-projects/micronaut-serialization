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
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Decoder that maps CSV records to maps keyed by header.
 * @since 3.2.5
 */
public interface CsvToMapDecoder extends CsvDecoder<Map<String, @Nullable String>> {

    /**
     * Decodes a CSV string using explicit header columns. Records are decoded lazily, and each
     * iterator decodes the string independently.
     *
     * @param value  The CSV string
     * @param header The explicit header columns
     * @return The decoded records; map values may be {@code null}
     * @throws CsvException If the value cannot be decoded
     */
    Iterable<Map<String, @Nullable String>> decode(String value, List<String> header) throws CsvException;

    /**
     * Decodes CSV using a resolved {@link CsvFormatConfig}, honoring the delimiter, header
     * mode and column names declared through CSV annotations.
     *
     * @param value  The CSV string
     * @param config The resolved format
     * @return The decoded records; map values may be {@code null}
     * @throws CsvException If the value cannot be decoded
     */
    @Override
    Iterable<Map<String, @Nullable String>> decode(String value, CsvFormatConfig config) throws CsvException;

    /**
     * Decodes CSV from an input stream using a resolved {@link CsvFormatConfig}.
     *
     * @param input   The input stream
     * @param charset The charset
     * @param config  The resolved format
     * @return The decoded records; map values may be {@code null}
     * @throws CsvException If the value cannot be decoded
     */
    @Override
    Iterable<Map<String, @Nullable String>> decode(InputStream input, Charset charset, CsvFormatConfig config) throws CsvException;
}
