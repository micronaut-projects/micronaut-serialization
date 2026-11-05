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
import io.micronaut.serde.csv.CsvEncoder;
import org.jspecify.annotations.Nullable;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class JacksonCsvEncoder extends CsvEncoder {

    private final tools.jackson.dataformat.csv.CsvMapper csvMapper;
    private final OutputStream outputStream;
    @Nullable
    private List<String> header;
    private boolean headerWritten;

    public JacksonCsvEncoder(OutputStream outputStream) {
        this(outputStream, new tools.jackson.dataformat.csv.CsvMapper());
    }

    public JacksonCsvEncoder(OutputStream outputStream, tools.jackson.dataformat.csv.CsvMapper csvMapper) {
        this.csvMapper = csvMapper;
        this.outputStream = outputStream;
    }

    @Override
    protected void flushRow() throws IOException {
        List<String> names = columnNames();
        List<@Nullable String> values = columnValues();
        List<String> columns = header == null || header.isEmpty() ? names : header;
        boolean writeHeader = !headerWritten && header != null && !header.isEmpty();
        CsvSchema.Builder builder = CsvSchema.builder()
            .setUseHeader(writeHeader)
            .setColumnSeparator(delimiter())
            .setQuoteChar(quoteChar())
            .setEscapeChar(escapeChar())
            .setLineSeparator(lineSeparator())
            .setNullValue(nullValue());
        for (String column : columns) {
            builder.addColumn(column);
        }
        CsvSchema schema = builder.build();
        Map<String, @Nullable String> record = new LinkedHashMap<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            record.put(names.get(i), i < values.size() ? values.get(i) : null);
        }
        String csv = csvMapper.writer(schema)
            .with(tools.jackson.dataformat.csv.CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
            .writeValueAsString(record);
        outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
        headerWritten = true;
    }

    @Override
    public void writeHeader(List<String> header) {
        this.header = List.copyOf(header);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
