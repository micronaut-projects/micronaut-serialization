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
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvMappingException;
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
    public <T> Iterable<T> readAll(String value, Class<T> type) {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        return readRecords(decodeRecords(value, config), type, config, record -> readRecord(record, type, config));
    }

    @Override
    public <T> T read(List<String> value, Class<T> type) throws CsvException {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        return readPropertyMap(CsvRecordMappings.rowToPropertyMap(config, value), type, config);
    }

    @Override
    public <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        return readRecords(
            decodeRecords(new InputStreamReader(input, charset), config),
            type,
            config,
            record -> readRecord(record, type, config)
        );
    }

    @Override
    public String writeToString(Iterable<?> values) throws CsvException {
        Iterator<?> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return "";
        }
        Object first = iterator.next();
        if (first == null) {
            throw new CsvMappingException("Cannot write a null CSV record.", Object.class);
        }
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
        if (first == null) {
            throw new CsvMappingException("Cannot write a null CSV record.", Object.class);
        }
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

    private Map<String, @Nullable String> toColumnMap(@Nullable Object value, CsvFormatConfig config) throws CsvException {
        return CsvRecordMappings.toColumnMap(config, collapsedProperties(value, config));
    }

    private Map<String, @Nullable String> toHeaderMap(@Nullable Object value,
                                                      CsvFormatConfig config,
                                                      Collection<String> header) throws CsvException {
        return CsvRecordMappings.toColumnMap(config, collapsedProperties(value, config), header);
    }

    private Map<String, Object> collapsedProperties(@Nullable Object value, CsvFormatConfig config) throws CsvException {
        CsvRecordMappings.requireRecord(value);
        Object record = Objects.requireNonNull(value);
        Map<String, ?> properties = record instanceof Map<?, ?> map
            ? CsvRecordMappings.toStringKeyedMap(map)
            : toPropertyMap(record);
        return CsvRecordMappings.collapseInlineProperties(
            config,
            properties,
            this::encodeInline
        );
    }
}
