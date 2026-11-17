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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.csv.error.CsvException;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * Extended CSV mapper that provides convenience value operations.
 * @since 3.2.5
 */
public interface CsvObjectMapper extends CsvReader, CsvWriter {

    /**
     * Reads a typed value from a CSV input stream.
     *
     * @param inputStream The input stream
     * @param type        The type to read
     * @param <T>         The value type
     * @return The decoded value
     * @throws CsvException If the value cannot be decoded
     */
    default <T> @Nullable T readCsvValue(InputStream inputStream, Argument<T> type) throws CsvException {
        Iterable<T> records = read(inputStream, Charset.defaultCharset(), type.getType());
        var it = records.iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Reads a typed value from a CSV byte array.
     *
     * @param bytes The byte array
     * @param type  The type to read
     * @param <T>   The value type
     * @return The decoded value
     * @throws CsvException If the value cannot be decoded
     */
    default <T> @Nullable T readCsvValue(byte[] bytes, Argument<T> type) throws CsvException {
        try (var is = new ByteArrayInputStream(bytes)) {
            return readCsvValue(is, type);
        } catch (Exception e) {
            throw new CsvException("Failed to read CSV from bytes", e);
        }
    }

    /**
     * Writes a value as CSV to an output stream.
     *
     * @param outputStream The output stream
     * @param value        The value to write
     * @param charset      The charset to use
     * @throws CsvException If the value cannot be encoded or written
     */
    @Override
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
     * Writes a typed value as CSV to an output stream.
     *
     * @param outputStream The output stream
     * @param type         The type of the value
     * @param value        The value to write
     * @param charset      The charset to use
     * @param <T>          The value type
     * @throws CsvException If the value cannot be encoded or written
     */
    default <T> void writeCsvValue(OutputStream outputStream, Argument<T> type, @Nullable T value, Charset charset) throws CsvException {
        writeValue(outputStream, value, charset);
    }

    /**
     * Writes a value as CSV bytes.
     *
     * @param value The value to write
     * @return The CSV bytes
     * @throws CsvException If the value cannot be encoded
     */
    default byte[] writeCsvValueAsBytes(@Nullable Object value) throws CsvException {
        var baos = new ByteArrayOutputStream();
        writeValue(baos, value, Charset.defaultCharset());
        return baos.toByteArray();
    }

    /**
     * Writes a typed value as CSV bytes.
     *
     * @param type  The type of the value
     * @param value The value to write
     * @param <T>   The value type
     * @return The CSV bytes
     * @throws CsvException If the value cannot be encoded
     */
    default <T> byte[] writeCsvValueAsBytes(Argument<T> type, @Nullable T value) throws CsvException {
        var baos = new ByteArrayOutputStream();
        writeCsvValue(baos, type, value, Charset.defaultCharset());
        return baos.toByteArray();
    }
}
