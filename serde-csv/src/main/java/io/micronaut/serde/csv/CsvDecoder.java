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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

@Internal
@SuppressWarnings("checkstyle:DesignForExtension")
public abstract class CsvDecoder implements Decoder {

    private final Map<String, @Nullable String> row;
    @Nullable
    private Iterator<String> keyIterator;
    @Nullable
    private String currentKey;
    @Nullable
    private String currentValue;

    protected CsvDecoder(Map<String, @Nullable String> row) {
        this.row = row;
    }

    /**
     * Sets the current cell value for a single-value decoder. Package-private for use by
     * {@link CsvBeanDeserializer} when invoking custom deserializers.
     */
    final void setCurrentValue(@Nullable String value) {
        this.currentValue = value;
    }

    /**
     * Returns the logical row backing this decoder.
     *
     * @return The row keyed by property or column name
     */
    public Map<String, @Nullable String> row() {
        return row;
    }

    public static CsvDecoder forRow(Map<String, @Nullable String> row) {
        return new CsvDecoder(row) {
            @Override
            public void close() {
                // no-op
            }
        };
    }

    public boolean hasNextRow() {
        return false;
    }

    /**
     * Checks whether another CSV row is available for the given element type. Backends override
     * this to configure their native parser from the type's {@code CsvFormat}.
     *
     * @param type The element type
     * @return {@code true} when another row is available
     * @throws IOException If the parser cannot be initialized
     */
    public boolean hasNextRow(Argument<?> type) throws IOException {
        return hasNextRow();
    }

    @Nullable
    public CsvDecoder nextRow() {
        return null;
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        keyIterator = row.keySet().iterator();
        return this;
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        if (keyIterator == null || !keyIterator.hasNext()) {
            return null;
        }
        currentKey = keyIterator.next();
        currentValue = row.get(currentKey);
        return currentKey;
    }

    @Override
    public String decodeString() throws IOException {
        return currentValue == null ? "" : currentValue;
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return Boolean.parseBoolean(currentValue);
    }

    @Override
    public byte decodeByte() throws IOException {
        return Byte.parseByte(currentValue);
    }

    @Override
    public short decodeShort() throws IOException {
        return Short.parseShort(currentValue);
    }

    @Override
    public char decodeChar() throws IOException {
        return currentValue == null || currentValue.isEmpty() ? '\0' : currentValue.charAt(0);
    }

    @Override
    public int decodeInt() throws IOException {
        return Integer.parseInt(currentValue);
    }

    @Override
    public long decodeLong() throws IOException {
        return Long.parseLong(currentValue);
    }

    @Override
    public float decodeFloat() throws IOException {
        return Float.parseFloat(currentValue);
    }

    @Override
    public double decodeDouble() throws IOException {
        return Double.parseDouble(currentValue);
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        return new BigInteger(currentValue);
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        return new BigDecimal(currentValue);
    }

    @Override
    public boolean decodeNull() throws IOException {
        return currentValue == null;
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        throw new UnsupportedOperationException("Arrays are not supported as CSV cell values");
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return false;
    }

    @Override
    public void skipValue() throws IOException {
        // no-op
    }

    @Override
    public void finishStructure() throws IOException {
        // no-op
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        // no-op
    }

    @Override
    public void close() throws IOException {
        // no-op
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return new IOException(message);
    }

    @Override
    @Nullable
    public Object decodeArbitrary() throws IOException {
        return currentValue;
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        throw createDeserializationException("decodeNode() not supported in CSV decoder.", currentValue);
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        return this;
    }
}
