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
package io.micronaut.serde.csv;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
@SuppressWarnings("checkstyle:DesignForExtension")
public abstract class CsvEncoder implements Encoder, KeysAwareEncoder {

    private char delimiter = CsvFormatConfig.DEFAULT.delimiter();
    private String lineSeparator = CsvFormatConfig.DEFAULT.lineSeparator();
    private char quoteChar = CsvFormatConfig.DEFAULT.quote();
    private char escapeChar = CsvFormatConfig.DEFAULT.escape();
    private String nullValue = CsvFormatConfig.DEFAULT.nullValue();
    private final List<String> columnNames = new ArrayList<>();
    private final List<@Nullable String> columnValues = new ArrayList<>();
    private List<@Nullable String> lastRow = List.of();
    private String currentKey = "";
    private int objectDepth;
//    private final Map<String, CsvEncoder> nestedEncoders = new LinkedHashMap<>();

    protected CsvEncoder() {
    }

    /**
     * Applies a resolved CSV format to this encoder. Backends consume the individual
     * properties when they create their native printer.
     *
     * @param config The resolved format
     */
    public void configure(CsvFormatConfig config) {
        setDelimiter(config.delimiter());
        setLineSeparator(config.lineSeparator());
        setQuoteChar(config.quote());
        setEscapeChar(config.escape());
        setNullValue(config.nullValue());
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public char delimiter() {
        return delimiter;
    }

    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator.isEmpty()
            ? CsvFormatConfig.DEFAULT.lineSeparator() : lineSeparator;
    }

    public String lineSeparator() {
        return lineSeparator;
    }

    public void setQuoteChar(char quoteChar) {
        this.quoteChar = quoteChar;
    }

    public char quoteChar() {
        return quoteChar;
    }

    public void setEscapeChar(char escapeChar) {
        this.escapeChar = escapeChar;
    }

    public char escapeChar() {
        return escapeChar;
    }

    public void setNullValue(String nullValue) {
        this.nullValue = nullValue;
    }

    public String nullValue() {
        return nullValue;
    }

    public List<String> columnNames() {
        return columnNames;
    }

    /**
     * Writes a header row before any data rows. Backends implement this to emit the
     * header using their own CSV printer/parser.
     *
     * @param header The header columns
     * @throws IOException If the header cannot be written
     */
    public abstract void writeHeader(List<String> header) throws IOException;

    public List<@Nullable String> columnValues() {
        return columnValues;
    }

    /**
     * Returns the logical cell values of the last flushed row. Nulls are preserved; the backend
     * is responsible for rendering them using the configured null value.
     *
     * @return The last row's cell values
     */
    public List<@Nullable String> lastRow() {
        return lastRow;
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        objectDepth++;
        if (objectDepth == 1) {
            return this;
        }
        CsvEncoder child = createNestedEncoder(currentKey);
//        nestedEncoders.put(currentKey, child);
        return child;
    }

    @Override
    public void finishStructure() throws IOException {
        objectDepth--;
        if (objectDepth == 0) {
            lastRow = java.util.Collections.unmodifiableList(new ArrayList<>(columnValues));
            flushRow();
            columnNames.clear();
            columnValues.clear();
//            nestedEncoders.clear();
        }
    }

    @Override
    public void encodeKey(String key) throws IOException {
        currentKey = key;
    }

    @Override
    public void encodeKey(Keys keys, int index) throws IOException {
        // Use the CSV KeysProvider contributor index, computed at build time.
        // If the provider is registered (via META-INF/services/io.micronaut.serde.KeysProvider),
        // CSV_KEYS_INDEX will be its position in the service loader order;
        // otherwise it falls back to 0 (the current behavior).
        String key = (String) io.micronaut.serde.KeysSupport.get(keys, io.micronaut.serde.csv.keys.CsvKeysProvider.CSV_KEYS_INDEX)[index];
        encodeKey(key);
    }

    @Override
    public void encodeString(String value) throws IOException {
        addColumn(currentKey, value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeByte(byte value) {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeShort(short value) {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeChar(char value) {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeInt(int value) throws IOException {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeLong(long value) throws IOException {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeFloat(float value) {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        addColumn(currentKey, String.valueOf(value));
    }

    @Override
    public void encodeBigInteger(BigInteger value) {
        addColumn(currentKey, value.toString());
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) {
        addColumn(currentKey, value.toString());
    }

    @Override
    public void encodeNull() throws IOException {
        addColumn(currentKey, null);
    }

    @Override
    public Encoder encodeArray(Argument<?> type) {
        throw new UnsupportedOperationException("Arrays are not supported as CSV cell values");
    }

    protected abstract void flushRow() throws IOException;

    protected CsvEncoder createNestedEncoder(String prefix) {
        return new NestedCsvEncoder(prefix);
    }

    private void addColumn(String key, @Nullable String value) {
        columnNames.add(key);
        columnValues.add(value);
    }

    private static final class NestedCsvEncoder extends CsvEncoder {
        private final String prefix;

        NestedCsvEncoder(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void writeHeader(List<String> header) {
            // no-op: nested encoder contributes columns to parent
        }

        @Override
        protected void flushRow() {
            // no-op: nested encoder contributes columns to parent
        }

        @Override
        public void encodeKey(String key) throws IOException {
            super.encodeKey(prefix + "." + key);
        }
    }
}
