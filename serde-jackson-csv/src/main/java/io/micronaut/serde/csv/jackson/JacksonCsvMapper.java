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
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public final class JacksonCsvMapper extends JacksonCsvMapperBase {

    private static final CsvSchema DEFAULT_SCHEMA = CsvSchema.emptySchema().withHeader();

    private final Map<CsvFormatConfig, CsvSchema> writeSchemaCache = new ConcurrentHashMap<>();
    private final Map<CsvFormatConfig, CsvSchema> readSchemaCache = new ConcurrentHashMap<>();

    /**
     * Creates a Jackson-backed CSV mapper.
     */
    public JacksonCsvMapper() {
        super(new tools.jackson.dataformat.csv.CsvMapper());
    }

    @Override
    protected CsvSchema createReadSchema(CsvFormatConfig config) {
        return readSchemaCache.computeIfAbsent(
            config,
            c -> c.columns().isEmpty() ? DEFAULT_SCHEMA : schemaBuilder(c, false).build()
        );
    }

    @Override
    protected CsvSchema createWriteSchema(CsvFormatConfig config) {
        return writeSchemaCache.computeIfAbsent(
            config,
            c -> c.columns().isEmpty() ? csvMapper.schemaFor(Object.class) : schemaBuilder(c, true).build()
        );
    }

    @Override
    protected CsvSchema createWriteSchema(CsvFormatConfig config, Map<String, ?> firstRecord) {
        if (config.columns().isEmpty()) {
            CsvSchema.Builder builder = schemaBuilderWithoutColumns(config).setUseHeader(config.writeHeader());
            for (String column : firstRecord.keySet()) {
                builder.addColumn(column);
            }
            return builder.build();
        }
        return createWriteSchema(config);
    }

    @Override
    public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        return readRecords(decodeRecords(value, config), type, config, record -> {
            if (Map.class.isAssignableFrom(type)) {
                return type.cast(CsvRecordMappings.toPropertyMap(config, record));
            }
            Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, record);
            Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                config,
                properties,
                this::decodeInline
            );
            return Objects.requireNonNull(csvMapper.convertValue(expanded, type));
        });
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
        return readRecords(decodeRecords(new InputStreamReader(input, charset), config), type, config, record -> {
            if (Map.class.isAssignableFrom(type)) {
                return type.cast(CsvRecordMappings.toPropertyMap(config, record));
            }
            Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, record);
            Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                config,
                properties,
                this::decodeInline
            );
            return Objects.requireNonNull(csvMapper.convertValue(expanded, type));
        });
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
            CsvSchema schema = writeSchema(config, firstRecord);
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
            properties = toPropertyMap(value);
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
            properties = toPropertyMap(value);
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

    /**
     * Returns the stream configuration for JSON streaming.
     *
     * @return the stream configuration
     */
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }
}
