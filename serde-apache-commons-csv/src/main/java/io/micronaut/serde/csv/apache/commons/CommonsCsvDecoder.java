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
import io.micronaut.serde.csv.CsvDecoder;
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
public class CommonsCsvDecoder extends CsvDecoder {

    @Nullable
    private final InputStream inputStream;
    @Nullable
    private CSVParser parser;
    @Nullable
    private Iterator<CSVRecord> recordIterator;
    @Nullable
    private CsvDecoder currentRow;

    public CommonsCsvDecoder(InputStream inputStream) {
        super(new LinkedHashMap<>());
        this.inputStream = inputStream;
    }

    public CommonsCsvDecoder(Map<String, @Nullable String> row) {
        super(row);
        this.inputStream = null;
        this.currentRow = CsvDecoder.forRow(row);
    }

    public static CommonsCsvDecoder forRow(Map<String, @Nullable String> row) {
        return new CommonsCsvDecoder(row);
    }

    private void ensureParser(CsvFormatConfig config) {
        if (parser != null || inputStream == null) {
            return;
        }
        try {
            CSVFormat format = CsvFormats.from(config);
            parser = format.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            recordIterator = parser.iterator();
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

    /**
     * Decodes the next CSV row into an object.
     * <p>
     * Dynamic header reading for raw {@code Map} types is not yet implemented (upcoming).
     * See the CSV guide:
     * <a href="https://docs.micronaut.io/latest/guide/index.html#csv">CSV</a>.
     */
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
        } else {
            super.close();
        }
        if (parser != null) {
            try {
                parser.close();
            } catch (IOException e) {
                // ignore
            }
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
