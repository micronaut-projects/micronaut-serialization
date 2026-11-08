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
package io.micronaut.serde.csv.apache.commons;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.csv.AbstractCsvStreamDecoder;
import io.micronaut.serde.csv.CsvDecoder;
import io.micronaut.serde.csv.CsvStreamDecoder;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class CommonsCsvDecoder extends AbstractCsvStreamDecoder {

    @Nullable
    private final InputStream inputStream;
    @Nullable
    private CSVParser parser;
    @Nullable
    private CsvFormatConfig parserConfig;
    @Nullable
    private Iterator<CSVRecord> recordIterator;
    @Nullable
    private CsvDecoder currentRow;

    public CommonsCsvDecoder(InputStream inputStream) {
        try {
            this.inputStream = new java.io.ByteArrayInputStream(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV input", e);
        }
    }

    public CommonsCsvDecoder(Map<String, @Nullable String> row) {
        super(row);
        this.inputStream = null;
    }

    public static CommonsCsvDecoder forRow(Map<String, @Nullable String> row) {
        return new CommonsCsvDecoder(row);
    }

    private void ensureParser(CsvFormatConfig config) {
        if (inputStream == null) {
            return;
        }
        if (parser != null && parserConfig != null && parserConfig.equals(config)) {
            return;
        }
        if (parser != null) {
            try {
                parser.close();
            } catch (IOException ignored) {
            }
            parser = null;
            recordIterator = null;
        }
        try {
            if (inputStream instanceof java.io.ByteArrayInputStream) {
                ((java.io.ByteArrayInputStream) inputStream).reset();
            }
            CSVFormat format = CsvFormats.from(config);
            parser = format.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            recordIterator = parser.iterator();
            parserConfig = config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV", e);
        }
    }

    @Override
    public boolean hasNextRow() {
        if (inputStream == null) {
            return false;
        }
        ensureParser(CsvFormatConfig.DEFAULT);
        return recordIterator != null && recordIterator.hasNext();
    }

    @Override
    public boolean hasNextRow(Argument<?> type) {
        if (inputStream == null) {
            return false;
        }
        ensureParser(CsvIntrospection.formatFor(type.getType()));
        return recordIterator != null && recordIterator.hasNext();
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        if (inputStream == null && currentRow != null) {
            return currentRow.decodeObject(type);
        }
        CsvFormatConfig config = CsvIntrospection.formatFor(type.getType());
        ensureParser(config);
        CsvDecoder rowDecoder;
        if (recordIterator == null || !recordIterator.hasNext()) {
            rowDecoder = CsvDecoder.forRow(new LinkedHashMap<>());
        } else {
            CSVRecord record = recordIterator.next();
            List<String> columns = config.columns().isEmpty()
                ? CsvIntrospection.columnsFor(type.getType())
                : config.columns();
            Map<String, @Nullable String> row = CsvRecords.toMap(record, columns);
            Map<String, @Nullable String> propertyRow = CsvRecordMappings.toPropertyMap(config, row, CsvRecordMappings.mapping(config));
            rowDecoder = CsvDecoder.forRow(propertyRow);
        }
        if (inputStream == null) {
            currentRow = rowDecoder;
        }
        return rowDecoder.decodeObject(type);
    }






















    @Override
    public void close() throws IOException {
        super.close();
        if (parser != null) {
            try {
                parser.close();
            } catch (IOException ignored) {
            }
        }
    }
}