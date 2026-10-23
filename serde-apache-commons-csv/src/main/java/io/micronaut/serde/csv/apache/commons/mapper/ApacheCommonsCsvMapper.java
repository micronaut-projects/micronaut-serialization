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

import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.decoder.CsvToMapDecoder;
import io.micronaut.serde.csv.encoder.MapToCsvEncoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvMappingException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link CsvMapper} implementation backed by an {@link ObjectMapper}.
 * @since 3.2.5
 */
@Singleton
public final class ApacheCommonsCsvMapper extends CommonsCsvMapperBase {

    /**
     * Creates an Apache Commons CSV-backed mapper.
     *
     * @param csvDecoder   The decoder used to map CSV data to a tree
     * @param csvEncoder   The encoder used to map a tree to CSV data
     * @param objectMapper The object mapper used to convert between trees and values
     */
    public ApacheCommonsCsvMapper(CsvToMapDecoder csvDecoder, MapToCsvEncoder csvEncoder, ObjectMapper objectMapper) {
        super(csvDecoder, csvEncoder, objectMapper);
    }

    private static boolean isPrimitiveOrStringOrBigDecimal(Class<?> type) {
        return type.isPrimitive()
            || String.class.isAssignableFrom(type)
            || BigDecimal.class.isAssignableFrom(type);
    }

    private static @Nullable Class<?> firstElementType(Iterable<?> values) {
        if (values instanceof Collection<?> collection) {
            for (Object value : collection) {
                return value == null ? null : value.getClass();
            }
        }
        return null;
    }

    private static CsvFormatConfig configFor(@Nullable Class<?> type) {
        return type == null ? CsvFormatConfig.DEFAULT : CsvIntrospection.formatFor(type);
    }

    @Override
    protected CSVFormat createReadFormat(CsvFormatConfig config) {
        return CsvFormats.from(config);
    }

    @Override
    protected CSVFormat createWriteFormat(CsvFormatConfig config) {
        return CsvFormats.writeFormat(config);
    }

    @Override
    protected CSVFormat createWriteFormat(CsvFormatConfig config, Collection<String> header) {
        return CsvFormats.format(config, header);
    }

    @Override
    public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        Iterable<Map<String, @Nullable String>> data = csvDecoder.decode(value, config);
        return readRecords(data, type, config, record -> readRecord(record, type, config));
    }

    @Override
    public <T> T read(List<String> value, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        return readPropertyMap(CsvRecordMappings.rowToPropertyMap(config, value), type, config);
    }

    @Override
    public <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        Iterable<Map<String, @Nullable String>> records = csvDecoder.decode(input, charset, config);
        return readRecords(records, type, config, record -> readRecord(record, type, config));
    }

    @Override
    public String writeToString(Iterable<?> values) throws CsvException {
        try {
            return csvEncoder.encodeToString(convertToMapIterable(values), configFor(firstElementType(values)));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof CsvException csvException) {
                throw csvException;
            }
            throw e;
        }
    }

    @Override
    public String writeToString(Iterable<?> values, Collection<String> header) throws CsvException {
        try {
            return csvEncoder.encodeToString(convertToMapIterable(values), header, configFor(firstElementType(values)));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof CsvException csvException) {
                throw csvException;
            }
            throw e;
        }
    }

    @Override
    public List<@Nullable String> writeAsLine(@Nullable Object value) throws CsvException {
        if (value == null) {
            return List.of();
        }
        JsonNode tree = toJsonTree(value);
        if (tree.isNull()) {
            return List.of();
        }
        List<@Nullable String> result = new ArrayList<>();
        Object raw = Objects.requireNonNull(tree.getValue());
        if (tree.isArray()) {
            List<?> values = (List<?>) raw;
            for (Object v : values) {
                result.add(toStringOrNull(v));
            }
        } else if (tree.isObject()) {
            Map<String, ?> values = asMap(raw);
            CsvFormatConfig config = configFor(value.getClass());
            Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
                config,
                values,
                this::encodeInline
            );
            result.addAll(CsvRecordMappings.toColumnValues(config, collapsed));
        } else {
            result.add(toStringOrNull(raw));
        }
        return result;
    }

    private @Nullable String toStringOrNull(@Nullable Object value) throws CsvException {
        if (value == null) {
            return null;
        }
        if (isPrimitiveOrStringOrBigDecimal(value.getClass())) {
            return value.toString();
        }
        throw new CsvMappingException("Cannot serialize non-primitive value " + value + ".", value.getClass());
    }

    private Iterable<Map<String, @Nullable String>> convertToMapIterable(Iterable<?> values) throws CsvException {
        return mapRecords(values, this::toMap);
    }

    private Map<String, @Nullable String> toMap(@Nullable Object value) throws CsvException {
        CsvRecordMappings.requireRecord(value);
        Object record = Objects.requireNonNull(value);
        if (record instanceof Map<?, ?> map) {
            Map<String, ?> raw = CsvRecordMappings.toStringKeyedMap(map);
            Map<String, @Nullable String> converted = new LinkedHashMap<>(raw.size());
            raw.forEach((key, entryValue) -> converted.put(key, entryValue == null ? null : entryValue.toString()));
            return converted;
        }
        JsonNode tree = toJsonTree(record);
        if (tree.isObject()) {
            Map<String, ?> map = asMap(tree.getValue());
            CsvFormatConfig config = configFor(record.getClass());
            Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
                config,
                map,
                this::encodeInline
            );
            return CsvRecordMappings.toColumnMap(config, collapsed);
        }
        throw new CsvMappingException("Cannot convert " + record + " to a CSV record.", record.getClass());
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
