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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.csv.AbstractCsvStreamDecoder;
import io.micronaut.serde.csv.CsvDecoder;
import io.micronaut.serde.csv.CsvObjectMapper;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.MappingIterator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class JacksonCsvDecoder extends AbstractCsvStreamDecoder {

    @Nullable
    private final InputStream inputStream;
    private final tools.jackson.dataformat.csv.CsvMapper csvMapper;
    private final Charset charset;
    @Nullable
    private volatile MappingIterator<Map<String, @Nullable String>> iterator = null;
    @Nullable
    private CsvDecoder currentRow;

    public JacksonCsvDecoder(InputStream inputStream) {
        this(inputStream, CsvObjectMapper.DEFAULT_CHARSET);
    }

    public JacksonCsvDecoder(InputStream inputStream, Charset charset) {
        this(inputStream, new tools.jackson.dataformat.csv.CsvMapper(), charset);
    }

    public JacksonCsvDecoder(InputStream inputStream, tools.jackson.dataformat.csv.CsvMapper csvMapper) {
        this(inputStream, csvMapper, CsvObjectMapper.DEFAULT_CHARSET);
    }

    public JacksonCsvDecoder(InputStream inputStream, tools.jackson.dataformat.csv.CsvMapper csvMapper, Charset charset) {
        this.inputStream = inputStream;
        this.csvMapper = csvMapper;
        this.charset = charset;
    }

    public JacksonCsvDecoder(Map<String, @Nullable String> row) {
        super(row);
        this.inputStream = null;
        this.csvMapper = new tools.jackson.dataformat.csv.CsvMapper();
        this.charset = CsvObjectMapper.DEFAULT_CHARSET;
    }

    public static JacksonCsvDecoder forRow(Map<String, @Nullable String> row) {
        return new JacksonCsvDecoder(row);
    }

    private void ensureParser(CsvFormatConfig config) {
        if (iterator != null || inputStream == null) {
            return;
        }
        synchronized (this) {
            if (iterator != null) {
                return;
            }
            try {
                tools.jackson.dataformat.csv.CsvSchema.Builder builder = JacksonCsvFormats.builder(config, config.hasHeader());
                List<String> columns = config.columns();
                if (!columns.isEmpty()) {
                    builder.addColumns(columns, tools.jackson.dataformat.csv.CsvSchema.ColumnType.STRING);
                }
                tools.jackson.databind.ObjectReader reader = csvMapper.readerForMapOf(String.class).with(builder.build());
                if (config.skipEmptyLines()) {
                    reader = reader.with(tools.jackson.dataformat.csv.CsvReadFeature.SKIP_EMPTY_LINES);
                }
                if (config.allowComments()) {
                    reader = reader.with(tools.jackson.dataformat.csv.CsvReadFeature.ALLOW_COMMENTS);
                }
                if (config.trim()) {
                    reader = reader.with(tools.jackson.dataformat.csv.CsvReadFeature.TRIM_SPACES);
                }
                iterator = reader.readValues(new InputStreamReader(inputStream, charset));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse CSV", e);
            }
        }
    }

    @Override
    public boolean hasNextRow() {
        if (inputStream == null) {
            return false;
        }
        ensureParser(CsvFormatConfig.DEFAULT);
        return iterator != null && iterator.hasNext();
    }

    @Override
    public boolean hasNextRow(Argument<?> type) throws IOException {
        if (inputStream == null) {
            return false;
        }
        ensureParser(CsvIntrospection.formatFor(type.getType()));
        return iterator != null && iterator.hasNext();
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        if (inputStream == null && currentRow != null) {
            return currentRow.decodeObject(type);
        }
        CsvFormatConfig config = CsvIntrospection.formatFor(type.getType());
        ensureParser(config);
        CsvDecoder rowDecoder;
        if (iterator == null || !iterator.hasNext()) {
            rowDecoder = CsvDecoder.forRow(new LinkedHashMap<>());
        } else {
            Map<String, @Nullable String> record = iterator.next();
            List<String> columns = config.columns().isEmpty()
                ? CsvIntrospection.columnsFor(type.getType())
                : config.columns();
            Map<String, @Nullable String> row;
            if (columns.isEmpty()) {
                row = record;
            } else {
                row = new LinkedHashMap<>(columns.size());
                for (String column : columns) {
                    if (record.containsKey(column)) {
                        row.put(column, record.get(column));
                    }
                }
            }
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
        if (iterator != null) {
            iterator.close();
        }
    }
}
