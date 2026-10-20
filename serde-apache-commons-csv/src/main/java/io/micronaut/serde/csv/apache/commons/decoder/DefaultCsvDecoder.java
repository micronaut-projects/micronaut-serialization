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
import io.micronaut.serde.csv.error.CsvParseException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
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

    private Iterable<Map<String, String>> decodeAsRecord(CSVFormat format, Reader reader) throws CsvParseException {
        List<Map<String, String>> result = new ArrayList<>();
        for (CSVRecord csvRecord : decode(format, reader)) {
            Map<String, String> map = csvRecord.toMap();
            if (map.isEmpty()) {
                throw new CsvParseException("CSV data map is empty. It may be caused when no header passed at first line.");
            }
            result.add(new LinkedHashMap<>(map));
        }
        return result;
    }

    private Iterable<Map<String, String>> decodeAsRecord(CSVFormat format, CsvFormatConfig config, Reader reader) throws CsvParseException {
        if (config.hasHeader()) {
            return decodeAsRecord(format, reader);
        }
        List<Map<String, String>> result = new ArrayList<>();
        List<String> columns = config.columns();
        for (CSVRecord csvRecord : decode(format, reader)) {
            List<String> values = csvRecord.toList();
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                map.put(columns.get(i), i < values.size() ? values.get(i) : null);
            }
            if (map.isEmpty()) {
                throw new CsvParseException("CSV data map is empty. It may be caused when no header passed at first line.");
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public Iterable<Map<String, String>> decode(String value, List<String> header) throws CsvException {
        CSVFormat formatWithHeader = FORMAT.builder()
            .setHeader(header.toArray(new String[0]))
            .setSkipHeaderRecord(false)
            .get();
        return decodeAsRecord(formatWithHeader, new StringReader(value));
    }

    public Iterable<Map<String, String>> decode(String value) throws CsvException {
        return decodeAsRecord(FORMAT, new StringReader(value));
    }

    public Iterable<Map<String, String>> decode(InputStream input, Charset charset) throws CsvException {
        return decodeAsRecord(FORMAT, new InputStreamReader(input, charset));
    }

    @Override
    public Iterable<Map<String, String>> decode(String value, CsvFormatConfig config) throws CsvException {
        return decodeAsRecord(CsvFormats.from(config), config, new StringReader(value));
    }

    @Override
    public Iterable<Map<String, String>> decode(InputStream input, Charset charset, CsvFormatConfig config) throws CsvException {
        return decodeAsRecord(CsvFormats.from(config), config, new InputStreamReader(input, charset));
    }
}
