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
package io.micronaut.serde.csv.jackson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.mapper.AbstractCsvMapper;
import org.jspecify.annotations.Nullable;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for Jackson CSV mapper providing Jackson-specific implementations.
 *
 * @since 3.2.5
 */
@Internal
abstract class JacksonCsvMapperBase extends AbstractCsvMapper {

    protected final tools.jackson.dataformat.csv.CsvMapper csvMapper;

    /**
     * Creates a new Jackson CSV mapper base.
     *
     * @param csvMapper The Jackson CSV mapper
     */
    protected JacksonCsvMapperBase(tools.jackson.dataformat.csv.CsvMapper csvMapper) {
        this.csvMapper = csvMapper;
    }

    @Override
    protected String encodeInlineRecord(CsvFormatConfig config, Map<String, @Nullable String> columns) throws CsvException {
        CsvSchema.Builder builder = schemaBuilderWithoutColumns(config).setUseHeader(false);
        for (String column : columns.keySet()) {
            builder.addColumn(column);
        }
        try {
            String encoded = csvMapper.writer(builder.build())
                .with(tools.jackson.dataformat.csv.CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
                .writeValueAsString(columns);
            return stripLineSeparator(encoded, config.lineSeparator());
        } catch (Exception e) {
            throw toCsvException(e, Map.class);
        }
    }

    @Override
    protected Iterable<Map<String, @Nullable String>> decodeRecords(String value, CsvFormatConfig config) {
        return () -> new RecordIterator(mapReader(config).readValues(value));
    }

    /**
     * Decodes records from a reader.
     *
     * @param reader The reader
     * @param config The CSV format configuration
     * @return The decoded records
     */
    protected Iterable<Map<String, @Nullable String>> decodeRecords(Reader reader, CsvFormatConfig config) {
        return () -> new RecordIterator(mapReader(config).readValues(reader));
    }

    /**
     * Creates a map reader for the given config.
     *
     * @param config The CSV format configuration
     * @return The object reader
     */
    protected tools.jackson.databind.ObjectReader mapReader(CsvFormatConfig config) {
        tools.jackson.databind.ObjectReader reader = csvMapper.readerForMapOf(String.class).with(readSchema(config));
        if (config.skipEmptyLines()) {
            reader = reader.with(tools.jackson.dataformat.csv.CsvReadFeature.SKIP_EMPTY_LINES)
                .with(tools.jackson.dataformat.csv.CsvReadFeature.ALLOW_COMMENTS);
        } else {
            reader = reader.without(tools.jackson.dataformat.csv.CsvReadFeature.SKIP_EMPTY_LINES)
                .without(tools.jackson.dataformat.csv.CsvReadFeature.ALLOW_COMMENTS);
        }
        if (config.trim()) {
            reader = reader.with(tools.jackson.dataformat.csv.CsvReadFeature.TRIM_SPACES)
                .with(tools.jackson.dataformat.csv.CsvReadFeature.TRIM_HEADER_SPACES);
        } else {
            reader = reader.without(tools.jackson.dataformat.csv.CsvReadFeature.TRIM_SPACES)
                .without(tools.jackson.dataformat.csv.CsvReadFeature.TRIM_HEADER_SPACES);
        }
        return reader;
    }

    @Override
    protected Map<String, ?> toPropertyMap(Object value) {
        return asMap(value);
    }

    @Override
    protected <T> T fromPropertyMap(Map<String, ?> map, Class<T> type) {
        return csvMapper.convertValue(map, type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> raw = new LinkedHashMap<>(map.size());
            map.forEach((key, entryValue) -> raw.put(String.valueOf(key), entryValue));
            return raw;
        }
        return csvMapper.convertValue(value, Map.class);
    }

    /**
     * Creates a schema builder for the given config.
     *
     * @param config The CSV format configuration
     * @param write  Whether the schema is for writing
     * @return The schema builder
     */
    protected CsvSchema.Builder schemaBuilder(CsvFormatConfig config, boolean write) {
        CsvSchema.Builder builder = CsvSchema.builder()
            .setColumnSeparator(config.delimiter())
            .setQuoteChar(config.quote())
            .setEscapeChar(config.escape())
            .setLineSeparator(config.lineSeparator())
            .setUseHeader(write ? config.writeHeader() : config.hasHeader());
        if (write) {
            builder.setNullValue(config.nullValue());
        }
        for (String column : config.columns()) {
            builder.addColumn(column);
        }
        return builder;
    }

    /**
     * Creates a schema builder without columns for the given config.
     *
     * @param config The CSV format configuration
     * @return The schema builder
     */
    protected CsvSchema.Builder schemaBuilderWithoutColumns(CsvFormatConfig config) {
        return CsvSchema.builder()
            .setColumnSeparator(config.delimiter())
            .setQuoteChar(config.quote())
            .setEscapeChar(config.escape())
            .setLineSeparator(config.lineSeparator())
            .setNullValue(config.nullValue());
    }

    /**
     * Strips the line separator from the end of the encoded string.
     *
     * @param encoded       The encoded string
     * @param lineSeparator The line separator
     * @return The string without the trailing line separator
     */
    protected String stripLineSeparator(String encoded, String lineSeparator) {
        return encoded.endsWith(lineSeparator)
            ? encoded.substring(0, encoded.length() - lineSeparator.length())
            : encoded;
    }

    /**
     * Creates the read-time schema for a type.
     */
    protected abstract CsvSchema createReadSchema(CsvFormatConfig config);

    /**
     * Creates the write-time schema for a type with a specific first record.
     */
    protected abstract CsvSchema createWriteSchema(CsvFormatConfig config, Map<String, ?> firstRecord);

    /**
     * Returns the read schema for a config, cached.
     */
    protected final CsvSchema readSchema(CsvFormatConfig config) {
        return createReadSchema(config);
    }

    /**
     * Returns the write schema for a type with a specific first record.
     */
    protected final CsvSchema writeSchema(CsvFormatConfig config, Map<String, ?> firstRecord) {
        return createWriteSchema(config, firstRecord);
    }

    private static final class RecordIterator implements Iterator<Map<String, @Nullable String>> {
        private final tools.jackson.databind.MappingIterator<Map<String, @Nullable String>> iterator;
        private boolean closed;

        private RecordIterator(tools.jackson.databind.MappingIterator<Map<String, @Nullable String>> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }
            boolean hasNext = iterator.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        @Override
        public Map<String, @Nullable String> next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            return new LinkedHashMap<>(iterator.next());
        }

        private void close() {
            if (!closed) {
                closed = true;
                iterator.close();
            }
        }
    }
}
