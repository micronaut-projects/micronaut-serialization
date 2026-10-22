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
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.decoder.CsvToMapDecoder;
import io.micronaut.serde.csv.encoder.MapToCsvEncoder;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
public final class ApacheCommonsCsvMapper implements CsvMapper, CsvObjectMapper {

    private final CsvToMapDecoder csvDecoder;
    private final MapToCsvEncoder csvEncoder;
    private final ObjectMapper objectMapper;

    /**
     * Creates an Apache Commons CSV-backed mapper.
     *
     * @param csvDecoder   The decoder used to map CSV data to a tree
     * @param csvEncoder   The encoder used to map a tree to CSV data
     * @param objectMapper The object mapper used to convert between trees and values
     */
    public ApacheCommonsCsvMapper(CsvToMapDecoder csvDecoder, MapToCsvEncoder csvEncoder, ObjectMapper objectMapper) {
        this.csvDecoder = csvDecoder;
        this.csvEncoder = csvEncoder;
        this.objectMapper = objectMapper;
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

    @SuppressWarnings("NullAway")
    private <T> Iterable<T> readRecords(Iterable<Map<String, String>> values, Class<T> type, CsvFormatConfig config) {
        return readAll(values, value -> {
            Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, value);
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

    private Map<String, Object> decodeInline(String value, CsvFormatConfig.CsvPropertyFormat format) throws CsvException {
        CsvFormatConfig config = format.config();
        Iterator<Map<String, String>> iterator = csvDecoder.decode(value, config).iterator();
        if (!iterator.hasNext()) {
            return Map.of();
        }
        Map<String, @Nullable String> properties = CsvRecordMappings.toPropertyMap(config, iterator.next());
        return CsvRecordMappings.expandInlineProperties(config, properties, this::decodeInline);
    }

    private <S, T> Iterable<T> readAll(Iterable<S> values, ThrowingFunction<S, T> mapper) {
        return () -> new Iterator<>() {
            final Iterator<S> iterator = values.iterator();

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

    @Override
    public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        Iterable<Map<String, String>> data = csvDecoder.decode(value, config);
        return readRecords(data, type, config);
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
        return readAll(values, line -> read(line, type));
    }

    @Override
    public <T> Iterable<T> read(InputStream input, String charset, Class<T> type) throws CsvException {
        return read(input, Charset.forName(charset), type);
    }

    @Override
    public <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) throws CsvException {
        CsvFormatConfig config = configFor(type);
        Iterable<Map<String, String>> records = csvDecoder.decode(input, charset, config);
        return readRecords(records, type, config);
    }

    private Iterable<Map<String, @Nullable String>> convertToMapIterable(Iterable<?> values) throws CsvException {
        return readAll(values, this::toMap);
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

    @SuppressWarnings("unchecked")
    private static Map<String, ?> asMap(@Nullable Object value) {
        return (Map<String, ?>) Objects.requireNonNull(value);
    }

    private String encodeInline(Object value, CsvFormatConfig.CsvPropertyFormat format) throws CsvException {
        JsonNode tree = value instanceof JsonNode node ? node : writeValueToTree(value);
        if (!tree.isObject()) {
            throw new CsvMappingException(
                "Inline CSV property must be a bean with properties.",
                value.getClass()
            );
        }
        Map<String, ?> properties = asMap(tree.getValue());
        CsvFormatConfig config = format.config();
        Map<String, Object> collapsed = CsvRecordMappings.collapseInlineProperties(config, properties, this::encodeInline);
        Map<String, @Nullable String> columns = CsvRecordMappings.toColumnMap(config, collapsed);
        return CsvFormats.encodeLine(config, columns.values());
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
        Iterable<T> records = read(new ByteArrayInputStream(bytes), Charset.defaultCharset(), type.getType());
        var it = records.iterator();
        return it.hasNext() ? it.next() : null;
    }

    @Override
    public <T> void writeCsvValue(OutputStream outputStream, Argument<T> type, @Nullable T value, Charset charset) throws CsvException {
        writeValue(outputStream, value, charset);
    }

    @Override
    public byte[] writeCsvValueAsBytes(@Nullable Object value) throws CsvException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeValue(baos, value, Charset.defaultCharset());
        return baos.toByteArray();
    }

    @Override
    public <T> byte[] writeCsvValueAsBytes(Argument<T> type, @Nullable T value) throws CsvException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeCsvValue(baos, type, value, Charset.defaultCharset());
        return baos.toByteArray();
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

    private static CsvException toCsvException(Exception e, Class<?> targetType) {
        if (e instanceof IOException) {
            return new CsvParseException("CSV parse error: " + e.getMessage(), e);
        }
        return new CsvMappingException("CSV mapping error: " + e.getMessage(), targetType, e);
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}

