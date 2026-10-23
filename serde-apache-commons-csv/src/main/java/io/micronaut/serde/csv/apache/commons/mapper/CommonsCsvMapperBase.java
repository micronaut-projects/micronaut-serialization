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
import io.micronaut.serde.csv.apache.commons.CsvRecords;
import io.micronaut.serde.csv.apache.commons.decoder.AbstractCsvDecoder;
import io.micronaut.serde.csv.decoder.CsvToMapDecoder;
import io.micronaut.serde.csv.encoder.MapToCsvEncoder;
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
import java.util.Collection;
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

    protected final CsvToMapDecoder csvDecoder;
    protected final MapToCsvEncoder csvEncoder;
    protected final ObjectMapper objectMapper;

    /**
     * Creates a new Commons CSV mapper base.
     *
     * @param csvDecoder   The CSV to map decoder
     * @param csvEncoder   The map to CSV encoder
     * @param objectMapper The object mapper
     */
    protected CommonsCsvMapperBase(
        CsvToMapDecoder csvDecoder,
        MapToCsvEncoder csvEncoder,
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
    protected Iterable<Map<String, @Nullable String>> decodeRecords(String value, CsvFormatConfig config) {
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
     */
    protected Iterable<Map<String, @Nullable String>> decodeRecords(Reader reader, CsvFormatConfig config) {
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
    protected Iterable<Map<String, @Nullable String>> decodeAsRecord(CSVFormat format, List<String> columns, String value) {
        return AbstractCsvDecoder.decodeRecords(format, () -> new StringReader(value), record -> CsvRecords.toMap(record, columns));
    }

    /**
     * Decodes records from a reader.
     *
     * @param format  The CSV format
     * @param columns The column names
     * @param reader  The reader
     * @return The decoded records
     */
    protected Iterable<Map<String, @Nullable String>> decodeAsRecord(CSVFormat format, List<String> columns, Reader reader) {
        return AbstractCsvDecoder.decodeRecords(format, () -> reader, record -> CsvRecords.toMap(record, columns));
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
    protected Iterable<Map<String, @Nullable String>> decodeAsRecord(CSVFormat format,
                                                                     List<String> columns,
                                                                     InputStream input,
                                                                     Charset charset) {
        return AbstractCsvDecoder.decodeRecords(
            format,
            () -> new InputStreamReader(input, charset),
            record -> CsvRecords.toMap(record, columns)
        );
    }

    @Override
    protected Map<String, ?> toPropertyMap(Object value) {
        try {
            JsonNode tree = toJsonTree(value);
            if (tree.isObject()) {
                return asMap(tree.getValue());
            }
            return Map.of();
        } catch (CsvException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts a value to a JSON tree.
     *
     * @param value The value to convert
     * @return The JSON tree
     * @throws CsvException If the value cannot be converted
     */
    protected JsonNode toJsonTree(@Nullable Object value) throws CsvException {
        try {
            return objectMapper.writeValueToTree(value);
        } catch (IOException e) {
            throw new CsvException("Failed to write value to tree", e);
        }
    }

    @Override
    protected <T> T fromPropertyMap(Map<String, ?> map, Class<T> type) {
        try {
            return Objects.requireNonNull(objectMapper.readValueFromTree(JsonNode.from(map), type));
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
}
