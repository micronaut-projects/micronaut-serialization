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
package io.micronaut.serde.csv.apache.commons.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.mapper.AbstractCsvMapper;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for Apache Commons CSV mapper providing Commons-specific implementations.
 *
 * @since 3.2.5
 */
@Internal
abstract class CommonsCsvMapperBase extends AbstractCsvMapper {

    protected final io.micronaut.serde.csv.decoder.CsvToMapDecoder csvDecoder;
    protected final io.micronaut.serde.csv.encoder.MapToCsvEncoder csvEncoder;
    protected final ObjectMapper objectMapper;

    /**
     * Creates a new Commons CSV mapper base.
     *
     * @param csvDecoder   The CSV to map decoder
     * @param csvEncoder   The map to CSV encoder
     * @param objectMapper The object mapper
     */
    protected CommonsCsvMapperBase(
        io.micronaut.serde.csv.decoder.CsvToMapDecoder csvDecoder,
        io.micronaut.serde.csv.encoder.MapToCsvEncoder csvEncoder,
        ObjectMapper objectMapper
    ) {
        this.csvDecoder = csvDecoder;
        this.csvEncoder = csvEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected String encodeInlineRecord(CsvFormatConfig config, Map<String, @Nullable String> columns) throws CsvException {
        return CsvFormats.encodeLine(config, columns.values());
    }

    @Override
    protected Iterable<Map<String, String>> decodeRecords(String value, CsvFormatConfig config) throws CsvException {
        CSVFormat format = createReadFormat(config);
        List<String> columns = config.hasHeader() ? List.of() : config.columns();
        return decodeAsRecord(format, columns, value);
    }

    /**
     * Decodes records from a reader.
     *
     * @param reader The reader
     * @param config The CSV format configuration
     * @return The decoded records
     * @throws CsvException If the records cannot be decoded
     */
    protected Iterable<Map<String, String>> decodeRecords(Reader reader, CsvFormatConfig config) throws CsvException {
        CSVFormat format = createReadFormat(config);
        List<String> columns = config.hasHeader() ? List.of() : config.columns();
        return decodeAsRecord(format, columns, reader);
    }

    /**
     * Decodes records from a string value.
     *
     * @param format  The CSV format
     * @param columns The column names
     * @param value   The CSV string value
     * @return The decoded records
     */
    protected Iterable<Map<String, String>> decodeAsRecord(CSVFormat format, List<String> columns, String value) {
        return decode(format, () -> new StringReader(value), record -> toMap(record, columns));
    }

    /**
     * Decodes records from a reader.
     *
     * @param format  The CSV format
     * @param columns The column names
     * @param reader  The reader
     * @return The decoded records
     */
    protected Iterable<Map<String, String>> decodeAsRecord(CSVFormat format, List<String> columns, Reader reader) {
        return decode(format, reader, record -> toMap(record, columns));
    }

    /**
     * Decodes records from an input stream.
     *
     * @param format   The CSV format
     * @param columns  The column names
     * @param input    The input stream
     * @param charset  The charset
     * @return The decoded records
     */
    protected Iterable<Map<String, String>> decodeAsRecord(CSVFormat format,
                                                           List<String> columns,
                                                           InputStream input,
                                                           Charset charset) {
        return decode(format, () -> new InputStreamReader(input, charset), record -> toMap(record, columns));
    }

    /**
     * Creates a decoding iterable from a reader.
     *
     * @param format  The CSV format
     * @param reader  The reader
     * @param mapper  The record mapper
     * @param <T>     The result type
     * @return The iterable
     */
    protected <T> Iterable<T> decode(CSVFormat format,
                                     Reader reader,
                                     java.util.function.Function<org.apache.commons.csv.CSVRecord, T> mapper) {
        return () -> new RecordIterator<>(format, reader, mapper);
    }

    /**
     * Creates a decoding iterable from a reader supplier.
     *
     * @param format       The CSV format
     * @param readerSupplier The reader supplier
     * @param mapper       The record mapper
     * @param <T>          The result type
     * @return The iterable
     */
    protected <T> Iterable<T> decode(CSVFormat format,
                                     java.util.function.Supplier<Reader> readerSupplier,
                                     java.util.function.Function<org.apache.commons.csv.CSVRecord, T> mapper) {
        return () -> new RecordIterator<>(format, readerSupplier.get(), mapper);
    }

    /**
     * Converts a CSV record to a map.
     *
     * @param record  The CSV record
     * @param columns The column names
     * @return The map
     */
    protected static Map<String, String> toMap(org.apache.commons.csv.CSVRecord record, List<String> columns) {
        Map<String, String> map;
        if (columns.isEmpty()) {
            map = record.toMap();
        } else {
            List<String> values = record.toList();
            map = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                map.put(columns.get(i), i < values.size() ? values.get(i) : null);
            }
        }
        if (map.isEmpty()) {
            throw new IllegalStateException(
                "CSV data map is empty. It may be caused when no header passed at first line."
            );
        }
        return map;
    }

    @Override
    protected Map<String, ?> toPropertyMap(Object value) {
        try {
            JsonNode tree = writeValueToTree(value);
            if (tree.isObject()) {
                return asMap(tree.getValue());
            }
            return Map.of();
        } catch (CsvException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("NullAway")
    @Override
    protected <T> T fromPropertyMap(Map<String, ?> map, Class<T> type) {
        try {
            return objectMapper.readValueFromTree(JsonNode.from(map), type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, ?> asMap(@Nullable Object value) {
        return (Map<String, ?>) Objects.requireNonNull(value);
    }

    /**
     * Creates the read-time format for a config.
     */
    protected abstract CSVFormat createReadFormat(CsvFormatConfig config);

    /**
     * Creates the write-time format for a config.
     */
    protected abstract CSVFormat createWriteFormat(CsvFormatConfig config);

    /**
     * Creates the write-time format for a config with an explicit header.
     */
    protected abstract CSVFormat createWriteFormat(CsvFormatConfig config, java.util.Collection<String> header);

    protected final CSVFormat writeFormat(CsvFormatConfig config) {
        return createWriteFormat(config);
    }

    protected final CSVFormat writeFormat(CsvFormatConfig config, java.util.Collection<String> header) {
        return createWriteFormat(config, header);
    }

    protected final CSVFormat readFormat(CsvFormatConfig config) {
        return createReadFormat(config);
    }

    private static final class RecordIterator<T> implements Iterator<T> {
        private final java.util.function.Function<org.apache.commons.csv.CSVRecord, T> mapper;
        private final org.apache.commons.csv.CSVParser parser;
        private final Iterator<org.apache.commons.csv.CSVRecord> records;
        private boolean closed;

        private RecordIterator(CSVFormat format, Reader reader, java.util.function.Function<org.apache.commons.csv.CSVRecord, T> mapper) {
            this.mapper = mapper;
            try {
                this.parser = format.parse(reader);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("Failed to parse CSV", e);
            }
            this.records = parser.iterator();
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }
            boolean hasNext = records.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return mapper.apply(records.next());
        }

        private void close() {
            if (!closed) {
                closed = true;
                try {
                    parser.close();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException("Failed to close CSV parser", e);
                }
            }
        }
    }
}
