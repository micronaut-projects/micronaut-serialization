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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvFormatConfig.CsvPropertyFormat;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base class for CSV mappers sharing common logic between backends.
 *
 * @since 3.2.5
 */
@Internal
public abstract class AbstractCsvMapper implements CsvMapper, CsvObjectMapper {

    /**
     * Decodes an inline CSV property value into a nested property map.
     *
     * @param value  The encoded inline value
     * @param format The property format
     * @return The nested property map
     * @throws CsvException If the value cannot be decoded
     */
    protected final Map<String, Object> decodeInline(String value, CsvPropertyFormat format) throws CsvException {
        CsvFormatConfig config = format.config();
        Iterator<Map<String, @Nullable String>> iterator = decodeRecords(value, config).iterator();
        if (!iterator.hasNext()) {
            return Map.of();
        }
        Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, iterator.next());
        return CsvRecordMappings.expandInlineProperties(config, properties, this::decodeInline);
    }

    /**
     * Encodes an inline CSV property value from a nested value.
     *
     * @param value  The nested value
     * @param format The property format
     * @return The encoded inline value
     * @throws CsvException If the value cannot be encoded
     */
    protected final String encodeInline(Object value, CsvPropertyFormat format) throws CsvException {
        CsvFormatConfig config = format.config();
        Map<String, ?> properties = toPropertyMap(value);
        Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
            config,
            properties,
            this::encodeInline
        );
        Map<String, @Nullable String> columns = CsvRecordMappings.toColumnMap(config, collapsed);
        return encodeInlineRecord(config, columns);
    }

    /**
     * Backend-specific encoding of a single inline record.
     *
     * @param config  The CSV format configuration
     * @param columns The column values
     * @return The encoded inline record
     * @throws CsvException If the record cannot be encoded
     */
    protected abstract String encodeInlineRecord(CsvFormatConfig config, Map<String, @Nullable String> columns) throws CsvException;

    /**
     * Backend-specific decoding of records from a string.
     *
     * @param value  The CSV string
     * @param config The CSV format configuration
     * @return The decoded records
     * @throws CsvException If the records cannot be decoded
     */
    protected abstract Iterable<Map<String, @Nullable String>> decodeRecords(String value, CsvFormatConfig config) throws CsvException;

    /**
     * Converts a bean value to a property map for column mapping.
     *
     * @param value The bean value
     * @return The property map
     */
    protected abstract Map<String, ?> toPropertyMap(Object value);

    /**
     * Converts a property map to a typed bean.
     *
     * @param map  The property map
     * @param type The bean type
     * @param <T>  The bean type
     * @return The typed bean
     */
    protected abstract <T> T fromPropertyMap(Map<String, ?> map, Class<T> type);

    /**
     * Creates an iterable that maps raw records to typed beans.
     *
     * @param records     The raw records
     * @param recordMapper The record mapper function
     * @param <T>         The bean type
     * @return The iterable of typed beans
     */
    protected final <T> Iterable<T> readRecords(
        Iterable<Map<String, @Nullable String>> records,
        ThrowingFunction<Map<String, @Nullable String>, T> recordMapper
    ) {
        return () -> new Iterator<>() {
            private final Iterator<Map<String, @Nullable String>> iterator = records.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                try {
                    return recordMapper.apply(iterator.next());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Reads a CSV input stream into typed values using a charset name.
     *
     * @param input   The input stream
     * @param charset The charset name
     * @param type    The element type
     * @param <T>     The element type
     * @return The decoded values
     * @throws CsvException If the value cannot be decoded
     */
    @Override
    public <T> Iterable<T> read(InputStream input, String charset, Class<T> type) throws CsvException {
        return read(input, Charset.forName(charset), type);
    }

    /**
     * Reads multiple CSV lines into typed values.
     *
     * @param values The CSV lines
     * @param type   The element type
     * @param <T>    The element type
     * @return The decoded values
     * @throws CsvException If the values cannot be decoded
     */
    @Override
    public <T> Iterable<T> read(Iterable<List<String>> values, Class<T> type) throws CsvException {
        return mapRecords(values, value -> read(value, type));
    }

    /**
     * Converts a raw CSV record to a typed value.
     *
     * @param record The raw CSV record
     * @param type   The target type
     * @param config The resolved CSV format
     * @param <T>    The target type
     * @return The typed value
     * @throws CsvException If the record cannot be converted
     */
    protected final <T> T readRecord(Map<String, @Nullable String> record,
                                     Class<T> type,
                                     CsvFormatConfig config) throws CsvException {
        return readPropertyMap(CsvRecordMappings.toPropertyMap(config, record), type, config);
    }

    /**
     * Converts a property map to a typed value.
     *
     * @param properties The values keyed by bean property name
     * @param type       The target type
     * @param config     The resolved CSV format
     * @param <T>        The target type
     * @return The typed value
     * @throws CsvException If the properties cannot be converted
     */
    protected final <T> T readPropertyMap(Map<String, @Nullable String> properties,
                                          Class<T> type,
                                          CsvFormatConfig config) throws CsvException {
        if (Map.class.isAssignableFrom(type)) {
            return type.cast(properties);
        }
        try {
            Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                config,
                properties,
                this::decodeInline
            );
            return Objects.requireNonNull(fromPropertyMap(expanded, type));
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    /**
     * Creates an iterable that maps each element using the given function.
     *
     * @param records The records to map
     * @param mapper  The mapper function
     * @param <S>     The source type
     * @param <T>     The target type
     * @return The iterable of mapped elements
     */
    protected final <S, T> Iterable<T> mapRecords(Iterable<S> records, ThrowingFunction<S, T> mapper) {
        return () -> new Iterator<>() {
            private final Iterator<S> iterator = records.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                try {
                    return mapper.apply(iterator.next());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Converts a record keyed by CSV column names to a record keyed by bean property names.
     *
     * @param config The resolved CSV format
     * @param record The record keyed by CSV column name
     * @return The record keyed by bean property name
     */
    protected final Map<String, @Nullable String> toPropertyMap(CsvFormatConfig config, Map<String, @Nullable String> record) {
        return CsvRecordMappings.toPropertyMap(config, record);
    }

    /**
     * Converts a record keyed by bean property names to a record keyed by CSV column names.
     *
     * @param config The resolved CSV format
     * @param values The values keyed by bean property name, or by CSV column name
     * @return The values keyed by CSV column name
     */
    protected final Map<String, @Nullable String> toColumnMap(CsvFormatConfig config, Map<String, ?> values) {
        return CsvRecordMappings.toColumnMap(config, values);
    }

    /**
     * Projects a record onto the configured CSV columns, preserving column order.
     *
     * @param config The resolved CSV format
     * @param values The values keyed by bean property name, or by CSV column name
     * @return The values in CSV column order
     */
    protected final List<@Nullable String> toColumnValues(CsvFormatConfig config, Map<String, ?> values) {
        return CsvRecordMappings.toColumnValues(config, values);
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object value, Charset charset) throws CsvException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        if (value == null) {
            return;
        }
        try {
            String csv = writeToString(List.of(value));
            outputStream.write(csv.getBytes(charset));
        } catch (Exception e) {
            throw new CsvException("Failed to write CSV to output stream", e);
        }
    }

    @Override
    public void writeValues(OutputStream outputStream, Iterable<?> values, Charset charset) throws CsvException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        try {
            String csv = writeToString(values);
            outputStream.write(csv.getBytes(charset));
        } catch (Exception e) {
            throw new CsvException("Failed to write CSV to output stream", e);
        }
    }

    @Override
    public byte[] writeCsvValueAsBytes(@Nullable Object value) throws CsvException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeValue(baos, value, Charset.defaultCharset());
        return baos.toByteArray();
    }

    @Override
    public <T> @Nullable T readCsvValue(byte[] bytes, Argument<T> type) throws CsvException {
        try (var is = new ByteArrayInputStream(bytes)) {
            return readCsvValue(is, type);
        } catch (Exception e) {
            throw new CsvException("Failed to read CSV from bytes", e);
        }
    }

    /**
     * Default implementation for writing a single value as a CSV line.
     *
     * @param value The value to encode
     * @return The encoded CSV line as a list of cell values; individual cells may be {@code null}
     * @throws CsvException If the value cannot be encoded
     */
    @Override
    public List<@Nullable String> writeAsLine(@Nullable Object value) throws CsvException {
        if (value == null) {
            return List.of();
        }
        if (isScalar(value)) {
            return List.of(value.toString());
        }
        CsvFormatConfig config = CsvIntrospection.formatFor(value.getClass());
        try {
            return new ArrayList<>(toColumnMap(value, config).values());
        } catch (Exception e) {
            throw toCsvException(e, value.getClass());
        }
    }

    /**
     * Checks if a value is a scalar type that can be written directly.
     *
     * @param value The value to check
     * @return Whether the value is a scalar type
     */
    protected boolean isScalar(Object value) {
        return value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Enum<?>;
    }

    /**
     * Converts an exception to a CSV exception.
     *
     * @param e          The exception
     * @param targetType The target type
     * @return The CSV exception
     */
    protected CsvException toCsvException(Exception e, Class<?> targetType) {
        if (e instanceof IOException) {
            return new io.micronaut.serde.csv.error.CsvParseException("CSV parse error: " + e.getMessage(), e);
        }
        return new io.micronaut.serde.csv.error.CsvMappingException("CSV mapping error: " + e.getMessage(), targetType, e);
    }

    private Map<String, @Nullable String> toColumnMap(Object value, CsvFormatConfig config) throws CsvException {
        Map<String, ?> properties = toPropertyMap(value);
        Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
            config,
            properties,
            this::encodeInline
        );
        return CsvRecordMappings.toColumnMap(config, collapsed);
    }

    /**
     * Functional interface for throwing functions.
     *
     * @param <T> The input type
     * @param <R> The result type
     */
    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
