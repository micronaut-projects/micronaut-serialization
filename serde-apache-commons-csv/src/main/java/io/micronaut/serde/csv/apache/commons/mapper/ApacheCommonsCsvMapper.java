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

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.csv.decoder.CsvToMapDecoder;
import io.micronaut.serde.csv.encoder.MapToCsvEncoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvMappingException;
import io.micronaut.serde.csv.error.CsvParseException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
        CSVFormat.Builder builder = CSVFormat.DEFAULT
            .builder()
            .setDelimiter(config.delimiter())
            .setQuote(config.quote())
            .setEscape(config.escape())
            .setRecordSeparator(config.lineSeparator())
            .setNullString(config.nullValue())
            .setIgnoreEmptyLines(config.skipEmptyLines())
            .setIgnoreSurroundingSpaces(config.trim())
            .setQuoteMode(org.apache.commons.csv.QuoteMode.MINIMAL);
        if (config.hasHeader()) {
            if (config.explicitColumns()) {
                builder.setHeader(config.columns().toArray(new String[0]));
            } else {
                builder.setHeader();
            }
            builder.setSkipHeaderRecord(true);
        }
        return builder.get();
    }

    @Override
    protected CSVFormat createWriteFormat(CsvFormatConfig config) {
        CSVFormat.Builder builder = CSVFormat.DEFAULT
            .builder()
            .setDelimiter(config.delimiter())
            .setQuote(config.quote())
            .setEscape(config.escape())
            .setRecordSeparator(config.lineSeparator())
            .setNullString(config.nullValue())
            .setIgnoreEmptyLines(config.skipEmptyLines())
            .setIgnoreSurroundingSpaces(config.trim())
            .setQuoteMode(org.apache.commons.csv.QuoteMode.MINIMAL);
        if (config.writeHeader() && !config.columns().isEmpty()) {
            builder.setHeader(config.columns().toArray(new String[0]));
        }
        return builder.get();
    }

    @Override
    protected CSVFormat createWriteFormat(CsvFormatConfig config, Collection<String> header) {
        CSVFormat.Builder builder = CSVFormat.DEFAULT
            .builder()
            .setDelimiter(config.delimiter())
            .setQuote(config.quote())
            .setEscape(config.escape())
            .setRecordSeparator(config.lineSeparator())
            .setNullString(config.nullValue())
            .setIgnoreEmptyLines(config.skipEmptyLines())
            .setIgnoreSurroundingSpaces(config.trim())
            .setQuoteMode(org.apache.commons.csv.QuoteMode.MINIMAL);
        builder.setHeader(header.toArray(new String[0]));
        return builder.get();
    }

    @Override
    public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        Iterable<Map<String, String>> data = csvDecoder.decode(value, config);
        return readRecords(data, type, config, record -> {
            Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, record);
            if (Map.class.isAssignableFrom(type)) {
                return type.cast(properties);
            }
            try {
                Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                    config,
                    properties,
                    this::decodeInline
                );
                return Objects.requireNonNull(objectMapper.readValueFromTree(JsonNode.from(expanded), type));
            } catch (Exception e) {
                throw toCsvException(e, type);
            }
        });
    }

    @Override
    public <T> T read(List<String> value, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        Map<String, @Nullable String> map = CsvRecordMappings.rowToPropertyMap(config, value);
        try {
            Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(config, map, this::decodeInline);
            return Objects.requireNonNull(objectMapper.readValueFromTree(JsonNode.from(expanded), type));
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
        CsvFormatConfig config = configFor(type);
        Iterable<Map<String, String>> records = csvDecoder.decode(input, charset, config);
        return readRecords(records, type, config, record -> {
            Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, record);
            if (Map.class.isAssignableFrom(type)) {
                return type.cast(properties);
            }
            try {
                Map<String, Object> expanded = CsvRecordMappings.expandInlineProperties(
                    config,
                    properties,
                    this::decodeInline
                );
                return Objects.requireNonNull(objectMapper.readValueFromTree(JsonNode.from(expanded), type));
            } catch (Exception e) {
                throw toCsvException(e, type);
            }
        });
    }

    @Override
    public String writeToString(Iterable<?> values) throws CsvException {
        return csvEncoder.encodeToString(convertToMapIterable(values), configFor(firstElementType(values)));
    }

    @Override
    public String writeToString(Iterable<?> values, Collection<String> header) throws CsvException {
        return csvEncoder.encodeToString(convertToMapIterable(values), header, configFor(firstElementType(values)));
    }

    @Override
    public List<String> writeAsLine(@Nullable Object value) throws CsvException {
        if (value == null) {
            return List.of();
        }
        JsonNode tree;
        try {
            tree = objectMapper.writeValueToTree(value);
        } catch (IOException e) {
            throw new CsvException("Failed to write value to tree", e);
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

    private Map<String, @Nullable String> toMap(Object value) throws CsvException {
        Map<String, @Nullable String> converted = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object entryValue = entry.getValue();
                converted.put(entry.getKey().toString(), entryValue == null ? null : entryValue.toString());
            }
            return converted;
        }
        JsonNode tree = writeValueToTree(value);
        if (tree.isObject()) {
            Map<String, ?> map = asMap(tree.getValue());
            CsvFormatConfig config = configFor(value.getClass());
            Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(
                config,
                map,
                this::encodeInline
            );
            return CsvRecordMappings.toColumnMap(config, collapsed);
        }
        throw new CsvMappingException("Cannot convert " + value + " to a CSV record.", value.getClass());
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws CsvException {
        try {
            return objectMapper.writeValueToTree(value);
        } catch (IOException e) {
            throw new CsvException("Failed to write value to tree", e);
        }
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws CsvException {
        try {
            return objectMapper.writeValueToTree(type, value);
        } catch (IOException e) {
            throw new CsvException("Failed to write value to tree", e);
        }
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws CsvException {
        try {
            return objectMapper.readValueFromTree(tree, type);
        } catch (IOException e) {
            throw new CsvParseException("Failed to read from tree: " + e.getMessage(), e);
        }
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
