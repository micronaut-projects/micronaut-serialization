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
package io.micronaut.serde.csv.apache.commons.decoder;

import io.micronaut.core.annotation.Internal;

import io.micronaut.context.annotation.Primary;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.decoder.CsvToMapDecoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default decoder that maps CSV records to maps keyed by header.
 * @since 3.2.5
 */
@Singleton
@Primary
@Internal
public final class DefaultCsvDecoder extends AbstractCsvDecoder implements CsvToMapDecoder {

    private static final CSVFormat FORMAT = CsvFormats.DEFAULT;
    private static final String EMPTY_MAP_MESSAGE =
        "CSV data map is empty. It may be caused when no header passed at first line.";

    private Iterable<Map<String, String>> decodeAsRecord(CSVFormat format, List<String> columns, String value) {
        return decode(format, () -> new StringReader(value), record -> toMap(record, columns));
    }

    private Iterable<Map<String, String>> decodeAsRecord(CSVFormat format,
                                                         List<String> columns,
                                                         InputStream input,
                                                         Charset charset) {
        return decode(format, () -> new InputStreamReader(input, charset), record -> toMap(record, columns));
    }

    private static Map<String, String> toMap(CSVRecord record, List<String> columns) {
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
            throw new IllegalStateException(EMPTY_MAP_MESSAGE);
        }
        return map;
    }

    @Override
    public Iterable<Map<String, String>> decode(String value, List<String> header) throws CsvException {
        CSVFormat formatWithHeader = FORMAT.builder()
            .setHeader(header.toArray(new String[0]))
            .setSkipHeaderRecord(false)
            .get();
        return decodeAsRecord(formatWithHeader, List.of(), value);
    }

    public Iterable<Map<String, String>> decode(String value) throws CsvException {
        return decodeAsRecord(FORMAT, List.of(), value);
    }

    public Iterable<Map<String, String>> decode(InputStream input, Charset charset) throws CsvException {
        return decodeAsRecord(FORMAT, List.of(), input, charset);
    }

    @Override
    public Iterable<Map<String, String>> decode(String value, CsvFormatConfig config) throws CsvException {
        CSVFormat format = CsvFormats.from(config);
        List<String> columns = config.hasHeader() ? List.of() : config.columns();
        return decodeAsRecord(format, columns, value);
    }

    @Override
    public Iterable<Map<String, String>> decode(InputStream input, Charset charset, CsvFormatConfig config) throws CsvException {
        CSVFormat format = CsvFormats.from(config);
        List<String> columns = config.hasHeader() ? List.of() : config.columns();
        return decodeAsRecord(format, columns, input, charset);
    }
}
