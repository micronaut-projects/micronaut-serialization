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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvMappingException;
import io.micronaut.serde.csv.error.CsvParseException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.serde.csv.mapper.CsvObjectMapper;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;
import tools.jackson.dataformat.csv.CsvWriteFeature;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Jackson-based {@link CsvMapper} implementation.
 *
 * <p>The Jackson CSV parser and generator are used for the record layer only. Records are
 * converted to and from bean property maps and then mapped by Jackson Databind, so CSV column
 * handling is shared with the Apache Commons CSV backend.</p>
 *
 * @since 3.2.5
 */
@Singleton
@Secondary
@BootstrapContextCompatible
public final class JacksonCsvMapper implements CsvMapper, CsvObjectMapper {

    private static final CsvSchema DEFAULT_SCHEMA = CsvSchema.emptySchema().withHeader();

    private final tools.jackson.dataformat.csv.CsvMapper csvMapper;
    private final Map<Class<?>, CsvSchema> writeSchemaCache = new ConcurrentHashMap<>();
    private final Map<CsvFormatConfig, CsvSchema> readSchemaCache = new ConcurrentHashMap<>();

    /**
     * Creates a Jackson-backed CSV mapper.
     */
    public JacksonCsvMapper() {
        this.csvMapper = new tools.jackson.dataformat.csv.CsvMapper();
    }

    /**
     * Resolves and caches the CSV schema used for reading a type.
     */
    private CsvSchema readSchemaFor(CsvFormatConfig config) {
        return readSchemaCache.computeIfAbsent(
            config,
            c -> c.columns().isEmpty() ? DEFAULT_SCHEMA : schemaBuilder(c, false).build()
        );
    }

    /**
     * Resolves and caches the CSV schema used for writing a type.
     */
    private CsvSchema writeSchemaFor(Class<?> type) {
        return writeSchemaCache.computeIfAbsent(type, t -> {
            CsvFormatConfig config = CsvIntrospection.formatFor(t);
            return config.columns().isEmpty() ? csvMapper.schemaFor(t) : schemaBuilder(config, true).build();
        });
    }

    private CsvSchema.Builder schemaBuilder(CsvFormatConfig config, boolean write) {
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

    private CsvSchema.Builder schemaBuilderWithoutColumns(CsvFormatConfig config) {
        return CsvSchema.builder()
            .setColumnSeparator(config.delimiter())
            .setQuoteChar(config.quote())
            .setEscapeChar(config.escape())
            .setLineSeparator(config.lineSeparator())
            .setNullValue(config.nullValue());
    }

    private ObjectReader mapReader(CsvFormatConfig config) {
        ObjectReader reader = csvMapper.readerForMapOf(String.class).with(readSchemaFor(config));
        if (config.skipEmptyLines()) {
            reader = reader.with(CsvReadFeature.SKIP_EMPTY_LINES)
                .with(CsvReadFeature.ALLOW_COMMENTS);
        } else {
            reader = reader.without(CsvReadFeature.SKIP_EMPTY_LINES)
                .without(CsvReadFeature.ALLOW_COMMENTS);
        }
        if (config.trim()) {
            reader = reader.with(CsvReadFeature.TRIM_SPACES)
                .with(CsvReadFeature.TRIM_HEADER_SPACES);
        } else {
            reader = reader.without(CsvReadFeature.TRIM_SPACES)
                .without(CsvReadFeature.TRIM_HEADER_SPACES);
        }
        return reader;
    }

    @Override
    public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        return toValues(decodeRecords(value, config), type, config);
    }

    @Override
    public <T> T read(List<String> value, Class<T> type) throws CsvException {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        Map<String, @Nullable String> properties = CsvRecordMappings.rowToPropertyMap(config, value);
        try {
            Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                config,
                properties,
                this::decodeInline
            );
            return Objects.requireNonNull(csvMapper.convertValue(expanded, type));
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    @Override
    public <T> Iterable<T> read(Iterable<List<String>> values, Class<T> type) throws CsvException {
        return () -> new Iterator<>() {
            private final Iterator<List<String>> iterator = values.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                try {
                    return read(iterator.next(), type);
                } catch (CsvException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public <T> Iterable<T> read(InputStream input, String charset, Class<T> type) throws CsvException {
        return read(input, Charset.forName(charset), type);
    }

    @Override
    public <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) throws CsvException {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        return toValues(decodeRecords(new InputStreamReader(input, charset), config), type, config);
    }

    private Iterable<Map<String, String>> decodeRecords(String value, CsvFormatConfig config) {
        return () -> new RecordIterator(mapReader(config).readValues(value));
    }

    private Iterable<Map<String, String>> decodeRecords(Reader reader, CsvFormatConfig config) {
        return () -> new RecordIterator(mapReader(config).readValues(reader));
    }

    private Map<String, Object> decodeInline(String value, CsvFormatConfig.CsvPropertyFormat format) throws CsvException {
        CsvFormatConfig config = format.config();
        Iterator<Map<String, String>> iterator = decodeRecords(value, config).iterator();
        if (!iterator.hasNext()) {
            return Map.of();
        }
        Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, iterator.next());
        return CsvRecordMappings.expandInlineProperties(config, properties, this::decodeInline);
    }

    private <T> Iterable<T> toValues(Iterable<Map<String, String>> records, Class<T> type, CsvFormatConfig config) {
        return () -> new Iterator<>() {
            private final Iterator<Map<String, String>> iterator = records.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                Map<String, String> record = iterator.next();
                if (Map.class.isAssignableFrom(type)) {
                    return type.cast(CsvRecordMappings.toPropertyMap(config, record));
                }
                try {
                    Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, record);
                    Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                        config,
                        properties,
                        JacksonCsvMapper.this::decodeInline
                    );
                    return Objects.requireNonNull(csvMapper.convertValue(expanded, type));
                } catch (Exception e) {
                    throw new RuntimeException(toCsvException(e, type));
                }
            }
        };
    }

    @Override
    public List<String> writeAsLine(@Nullable Object value) throws CsvException {
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

    @Override
    public String writeToString(Iterable<?> values) throws CsvException {
        Iterator<?> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return "";
        }
        Object first = iterator.next();
        Class<?> type = first.getClass();
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        try {
            Map<String, @Nullable String> firstRecord = toColumnMap(first, config);
            CsvSchema schema;
            if (config.columns().isEmpty()) {
                CsvSchema.Builder builder = schemaBuilderWithoutColumns(config).setUseHeader(config.writeHeader());
                for (String column : firstRecord.keySet()) {
                    builder.addColumn(column);
                }
                schema = builder.build();
            } else {
                schema = writeSchemaFor(type);
            }
            Iterable<Map<String, @Nullable String>> records =
                records(iterator, first, value -> {
                    try {
                        return toColumnMap(value, config);
                    } catch (CsvException e) {
                        throw new RuntimeException(e);
                    }
                });
            return csvMapper.writer(schema).writeValueAsString(records);
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    @Override
    public String writeToString(Iterable<?> values, Collection<String> header) throws CsvException {
        Iterator<?> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return "";
        }
        Object first = iterator.next();
        Class<?> type = first.getClass();
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        try {
            CsvSchema.Builder builder = schemaBuilderWithoutColumns(config).setUseHeader(true);
            for (String column : header) {
                builder.addColumn(column);
            }
            Iterable<Map<String, @Nullable String>> records =
                records(iterator, first, value -> {
                    try {
                        return toHeaderMap(value, config, header);
                    } catch (CsvException e) {
                        throw new RuntimeException(e);
                    }
                });
            return csvMapper.writer(builder.build()).writeValueAsString(records);
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    private static Iterable<Map<String, @Nullable String>> records(
        Iterator<?> iterator,
        Object first,
        Function<Object, Map<String, @Nullable String>> mapper
    ) {
        return () -> new Iterator<>() {
            private boolean firstPending = true;

            @Override
            public boolean hasNext() {
                return firstPending || iterator.hasNext();
            }

            @Override
            public Map<String, @Nullable String> next() {
                Object value;
                if (firstPending) {
                    firstPending = false;
                    value = first;
                } else {
                    value = iterator.next();
                }
                return mapper.apply(value);
            }
        };
    }

    private Map<String, @Nullable String> toColumnMap(Object value, CsvFormatConfig config) throws CsvException {
        Map<String, ?> properties;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> raw = new LinkedHashMap<>(map.size());
            map.forEach((key, entryValue) -> raw.put(String.valueOf(key), entryValue));
            properties = raw;
        } else {
            properties = asMap(value);
        }
        Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
            config,
            properties,
            this::encodeInline
        );
        return CsvRecordMappings.toColumnMap(config, collapsed);
    }

    private Map<String, @Nullable String> toHeaderMap(Object value, CsvFormatConfig config, Collection<String> header) throws CsvException {
        Map<String, ?> properties;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> raw = new LinkedHashMap<>(map.size());
            map.forEach((key, entryValue) -> raw.put(String.valueOf(key), entryValue));
            properties = raw;
        } else {
            properties = asMap(value);
        }
        Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
            config,
            properties,
            this::encodeInline
        );
        Map<String, @Nullable String> result = new LinkedHashMap<>(header.size());
        for (String column : header) {
            String property = propertyForColumn(config, column);
            Object entryValue = collapsed.containsKey(property) ? collapsed.get(property) : collapsed.get(column);
            result.put(column, entryValue == null ? null : entryValue.toString());
        }
        return result;
    }

    private String encodeInline(Object value, CsvFormatConfig.CsvPropertyFormat format) throws CsvException {
        CsvFormatConfig config = format.config();
        Map<String, ?> properties = asMap(value);
        Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
            config,
            properties,
            this::encodeInline
        );
        Map<String, @Nullable String> columns = CsvRecordMappings.toColumnMap(config, collapsed);
        CsvSchema.Builder builder = schemaBuilderWithoutColumns(config).setUseHeader(false);
        for (String column : columns.keySet()) {
            builder.addColumn(column);
        }
        try {
            String encoded = csvMapper.writer(builder.build())
                .with(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
                .writeValueAsString(columns);
            return stripLineSeparator(encoded, config.lineSeparator());
        } catch (Exception e) {
            throw toCsvException(e, format.type());
        }
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

    private static String stripLineSeparator(String encoded, String lineSeparator) {
        return encoded.endsWith(lineSeparator)
            ? encoded.substring(0, encoded.length() - lineSeparator.length())
            : encoded;
    }

    private static String propertyForColumn(CsvFormatConfig config, String column) {
        int index = config.columns().indexOf(column);
        return index >= 0 && index < config.propertyNames().size() ? config.propertyNames().get(index) : column;
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

    public byte[] writeValueAsBytes(@Nullable Object value) throws CsvException {
        return writeCsvValueAsBytes(value);
    }

    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T value) throws CsvException {
        return writeCsvValueAsBytes(type, value);
    }

    // CsvObjectMapper methods (different signatures)

    @Override
    public <T> @Nullable T readCsvValue(InputStream inputStream, Argument<T> type) throws CsvException {
        Iterable<T> records = read(inputStream, Charset.defaultCharset(), type.getType());
        var it = records.iterator();
        return it.hasNext() ? it.next() : null;
    }

    @Override
    public <T> @Nullable T readCsvValue(byte[] bytes, Argument<T> type) throws CsvException {
        try (var is = new ByteArrayInputStream(bytes)) {
            return readCsvValue(is, type);
        } catch (Exception e) {
            throw new CsvException("Failed to read CSV from bytes", e);
        }
    }

    @Override
    public <T> void writeCsvValue(OutputStream outputStream, Argument<T> type, @Nullable T value, Charset charset) throws CsvException {
        writeValue(outputStream, value, charset);
    }

    @Override
    public byte[] writeCsvValueAsBytes(@Nullable Object value) throws CsvException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeValue(baos, value, StandardCharsets.UTF_8);
        return baos.toByteArray();
    }

    @Override
    public <T> byte[] writeCsvValueAsBytes(Argument<T> type, @Nullable T value) throws CsvException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeCsvValue(baos, type, value, StandardCharsets.UTF_8);
        return baos.toByteArray();
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws CsvException {
        throw new UnsupportedOperationException("writeValueToTree is not supported for CSV mapper");
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws CsvException {
        throw new UnsupportedOperationException("writeValueToTree is not supported for CSV mapper");
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws CsvException {
        throw new UnsupportedOperationException("readValueFromTree is not supported for CSV mapper");
    }

    private static boolean isScalar(Object value) {
        return value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Enum<?>;
    }

    private static CsvException toCsvException(Exception e, Class<?> targetType) {
        if (e instanceof IOException) {
            return new CsvParseException("CSV parse error: " + e.getMessage(), e);
        }
        return new CsvMappingException("CSV mapping error: " + e.getMessage(), targetType, e);
    }

    /**
     * Returns the stream configuration for JSON streaming.
     *
     * @return the stream configuration
     */
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    private static final class RecordIterator implements Iterator<Map<String, String>> {
        private final MappingIterator<Map<String, String>> iterator;
        private boolean closed;

        private RecordIterator(MappingIterator<Map<String, String>> iterator) {
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
        public Map<String, String> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
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
