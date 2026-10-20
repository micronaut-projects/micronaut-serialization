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

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvMappingException;
import io.micronaut.serde.csv.error.CsvParseException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.serde.csv.mapper.CsvObjectMapper;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.dataformat.csv.CsvSchema;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jackson-based {@link CsvMapper} implementation.
 * @since 3.2.5
 */
@Singleton
@Secondary
@BootstrapContextCompatible
public final class JacksonCsvMapper implements CsvMapper, CsvObjectMapper {

    private static final CsvSchema DEFAULT_SCHEMA = CsvSchema.emptySchema().withHeader();

    private final tools.jackson.dataformat.csv.CsvMapper csvMapper;
    private final Map<Class<?>, CsvSchema> writeSchemaCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, CsvSchema> readSchemaCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> headerCache = new ConcurrentHashMap<>();

    /**
     * Creates a Jackson-backed CSV mapper.
     */
    public JacksonCsvMapper() {
        this.csvMapper = new tools.jackson.dataformat.csv.CsvMapper();
    }

    /**
     * Resolves and caches the CSV schema used for writing a type.
     */
    private CsvSchema writeSchemaFor(Class<?> type) {
        return writeSchemaCache.computeIfAbsent(type, t -> buildSchema(t, true));
    }

    /**
     * Resolves and caches the CSV schema used for reading a type.
     */
    private CsvSchema readSchemaFor(Class<?> type) {
        return readSchemaCache.computeIfAbsent(type, t -> buildSchema(t, false));
    }

    private CsvSchema buildSchema(Class<?> type, boolean write) {
        CsvFormatConfig config = CsvIntrospection.formatFor(type);
        if (!config.configured()) {
            return write ? csvMapper.schemaFor(type) : DEFAULT_SCHEMA;
        }
        CsvSchema.Builder builder = CsvSchema.builder()
            .setColumnSeparator(config.delimiter())
            .setQuoteChar(config.quote())
            .setEscapeChar(config.escape())
            .setLineSeparator(config.lineSeparator())
            .setNullValue(config.nullValue())
            .setUseHeader(write ? config.writeHeader() : config.hasHeader());
        if (!config.columns().isEmpty()) {
            for (String column : config.columns()) {
                builder.addColumn(column);
            }
        } else if (!Map.class.equals(type)) {
            builder.addColumnsFrom(csvMapper.schemaFor(type));
        }
        return builder.build();
    }

    private String headerLineFor(Class<?> type) {
        return headerCache.computeIfAbsent(type, t -> String.join(",", writeSchemaFor(t).getColumnNames()));
    }

    @Override
    public <T> Iterable<T> readAll(String value, Class<T> type) throws CsvException {
        if (Map.class.equals(type)) {
            return (Iterable<T>) decodeRecords(value);
        }
        try {
            List<T> result = new ArrayList<>();
            var iterator = csvMapper.readerFor(type).with(readSchemaFor(type)).readValues(value);
            while (iterator.hasNext()) {
                @SuppressWarnings("unchecked")
                T item = (T) iterator.next();
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    @Override
    public <T> T read(List<String> value, Class<T> type) throws CsvException {
        try {
            CsvSchema schema = writeSchemaFor(type);
            String csv = headerLineFor(type) + '\n' + escapeCsv(value);
            return csvMapper.readerFor(type).with(schema).readValue(csv);
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    @Override
    public <T> Iterable<T> read(Iterable<List<String>> values, Class<T> type) throws CsvException {
        List<T> result = new ArrayList<>();
        for (List<String> line : values) {
            result.add(read(line, type));
        }
        return result;
    }

    @Override
    public <T> Iterable<T> read(InputStream input, String charset, Class<T> type) throws CsvException {
        return read(input, Charset.forName(charset), type);
    }

    @Override
    public <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) throws CsvException {
        try (Reader reader = new InputStreamReader(input, charset)) {
            return read(reader, type);
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    private <T> Iterable<T> read(Reader reader, Class<T> type) throws CsvException {
        if (Map.class.equals(type)) {
            return (Iterable<T>) decodeRecords(reader);
        }
        try {
            List<T> result = new ArrayList<>();
            var iterator = csvMapper.readerFor(type).with(readSchemaFor(type)).readValues(reader);
            while (iterator.hasNext()) {
                @SuppressWarnings("unchecked")
                T item = (T) iterator.next();
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            throw toCsvException(e, type);
        }
    }

    private Iterable<Map<String, String>> decodeRecords(String value) throws CsvException {
        try {
            List<Map<String, String>> result = new ArrayList<>();
            var iterator = csvMapper.readerForMapOf(String.class).with(readSchemaFor(Map.class)).readValues(value);
            while (iterator.hasNext()) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) iterator.next();
                result.add(new LinkedHashMap<>(map));
            }
            return result;
        } catch (Exception e) {
            throw toCsvException(e, Map.class);
        }
    }

    private Iterable<Map<String, String>> decodeRecords(Reader reader) throws CsvException {
        try {
            List<Map<String, String>> result = new ArrayList<>();
            var iterator = csvMapper.readerForMapOf(String.class).with(readSchemaFor(Map.class)).readValues(reader);
            while (iterator.hasNext()) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) iterator.next();
                result.add(new LinkedHashMap<>(map));
            }
            return result;
        } catch (Exception e) {
            throw toCsvException(e, Map.class);
        }
    }

    @Override
    public List<String> writeAsLine(@Nullable Object value) throws CsvException {
        if (value == null) {
            return List.of();
        }
        try {
            String csv = csvMapper.writer(writeSchemaFor(value.getClass())).writeValueAsString(value);
            return fromCsvString(csv);
        } catch (Exception e) {
            throw toCsvException(e, value.getClass());
        }
    }

    @Override
    public String writeToString(Iterable<?> values) throws CsvException {
        List<?> list = toList(values);
        if (list.isEmpty()) {
            return "";
        }
        try {
            CsvSchema schema = writeSchemaFor(list.getFirst().getClass());
            return csvMapper.writer(schema).writeValueAsString(list);
        } catch (Exception e) {
            throw toCsvException(e, list.getFirst().getClass());
        }
    }

    @Override
    public String writeToString(Iterable<?> values, Collection<String> header) throws CsvException {
        List<?> list = toList(values);
        try {
            CsvSchema.Builder builder = CsvSchema.builder();
            for (String column : header) {
                builder.addColumn(column);
            }
            CsvSchema schema = builder.build();
            return csvMapper.writer(schema).writeValueAsString(list);
        } catch (Exception e) {
            throw toCsvException(e, list.isEmpty() ? Object.class : list.getFirst().getClass());
        }
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

    private static List<?> toList(Iterable<?> values) {
        List<Object> list = new ArrayList<>();
        for (Object v : values) {
            list.add(v);
        }
        return list;
    }

    private static List<String> fromCsvString(String csv) {
        List<String> lines = Arrays.asList(csv.split("\n"));
        if (lines.isEmpty()) {
            return List.of();
        }
        String dataLine = lines.get(lines.size() - 1);
        if (dataLine.isEmpty()) {
            return List.of();
        }
        return parseLine(dataLine);
    }

    private static List<String> parseLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    private static String escapeCsv(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String value = values.get(i);
            if (value == null) {
                continue;
            }
            boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n");
            if (needsQuotes) {
                sb.append('"');
                sb.append(value.replace("\"", "\"\""));
                sb.append('"');
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
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
}
