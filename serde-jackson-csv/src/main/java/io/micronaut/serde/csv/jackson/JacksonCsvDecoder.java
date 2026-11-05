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
import io.micronaut.serde.csv.CsvDecoder;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class JacksonCsvDecoder extends CsvDecoder {

    @Nullable
    private final InputStream inputStream;
    private final tools.jackson.dataformat.csv.CsvMapper csvMapper;
    @SuppressWarnings("NullAway")
    private tools.jackson.databind.MappingIterator<Map<String, @Nullable String>> iterator = null;
    @SuppressWarnings("NullAway")
    private CsvDecoder currentRow = null;

    public JacksonCsvDecoder(InputStream inputStream) {
        this(inputStream, new tools.jackson.dataformat.csv.CsvMapper());
    }

    public JacksonCsvDecoder(InputStream inputStream, tools.jackson.dataformat.csv.CsvMapper csvMapper) {
        super(new LinkedHashMap<>());
        this.inputStream = inputStream;
        this.csvMapper = csvMapper;
    }

    public JacksonCsvDecoder(Map<String, @Nullable String> row) {
        super(row);
        this.inputStream = null;
        this.csvMapper = new tools.jackson.dataformat.csv.CsvMapper();
        this.currentRow = CsvDecoder.forRow(row);
    }

    public static JacksonCsvDecoder forRow(Map<String, @Nullable String> row) {
        return new JacksonCsvDecoder(row);
    }

    private void ensureParser(CsvFormatConfig config) {
        if (iterator != null || inputStream == null) {
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
            iterator = reader.readValues(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV", e);
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
    public boolean hasNextRow(Argument<?> type) {
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
            Map<String, @Nullable String> propertyRow = CsvRecordMappings.toPropertyMap(config, row);
            rowDecoder = CsvDecoder.forRow(propertyRow);
        }
        if (inputStream == null) {
            currentRow = rowDecoder;
        }
        return rowDecoder.decodeObject(type);
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        return currentRow == null ? super.decodeKey() : currentRow.decodeKey();
    }

    @Override
    public String decodeString() throws IOException {
        return currentRow == null ? super.decodeString() : currentRow.decodeString();
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return currentRow == null ? super.decodeBoolean() : currentRow.decodeBoolean();
    }

    @Override
    public byte decodeByte() throws IOException {
        return currentRow == null ? super.decodeByte() : currentRow.decodeByte();
    }

    @Override
    public short decodeShort() throws IOException {
        return currentRow == null ? super.decodeShort() : currentRow.decodeShort();
    }

    @Override
    public char decodeChar() throws IOException {
        return currentRow == null ? super.decodeChar() : currentRow.decodeChar();
    }

    @Override
    public int decodeInt() throws IOException {
        return currentRow == null ? super.decodeInt() : currentRow.decodeInt();
    }

    @Override
    public long decodeLong() throws IOException {
        return currentRow == null ? super.decodeLong() : currentRow.decodeLong();
    }

    @Override
    public float decodeFloat() throws IOException {
        return currentRow == null ? super.decodeFloat() : currentRow.decodeFloat();
    }

    @Override
    public double decodeDouble() throws IOException {
        return currentRow == null ? super.decodeDouble() : currentRow.decodeDouble();
    }

    @Override
    public java.math.BigInteger decodeBigInteger() throws IOException {
        return currentRow == null ? super.decodeBigInteger() : currentRow.decodeBigInteger();
    }

    @Override
    public java.math.BigDecimal decodeBigDecimal() throws IOException {
        return currentRow == null ? super.decodeBigDecimal() : currentRow.decodeBigDecimal();
    }

    @Override
    public boolean decodeNull() throws IOException {
        return currentRow == null ? super.decodeNull() : currentRow.decodeNull();
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        return currentRow == null ? super.decodeArray(type) : currentRow.decodeArray(type);
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return currentRow == null ? super.hasNextArrayValue() : currentRow.hasNextArrayValue();
    }

    @Override
    public void skipValue() throws IOException {
        if (currentRow != null) {
            currentRow.skipValue();
        } else {
            super.skipValue();
        }
    }

    @Override
    public void finishStructure() throws IOException {
        if (currentRow != null) {
            currentRow.finishStructure();
        } else {
            super.finishStructure();
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (currentRow != null) {
            currentRow.finishStructure(consumeLeftElements);
        } else {
            super.finishStructure(consumeLeftElements);
        }
    }

    @Override
    public void close() throws IOException {
        if (currentRow != null) {
            currentRow.close();
        }
        if (iterator != null) {
            iterator.close();
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return currentRow == null ? super.createDeserializationException(message, invalidValue) : currentRow.createDeserializationException(message, invalidValue);
    }

    @Override
    @Nullable
    public Object decodeArbitrary() throws IOException {
        return currentRow == null ? super.decodeArbitrary() : currentRow.decodeArbitrary();
    }

    @Override
    public io.micronaut.json.tree.JsonNode decodeNode() throws IOException {
        return currentRow == null ? super.decodeNode() : currentRow.decodeNode();
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        CsvDecoder d = currentRow;
        if (d == null) {
            return super.decodeBuffer();
        }
        return d.decodeBuffer();
    }
}
