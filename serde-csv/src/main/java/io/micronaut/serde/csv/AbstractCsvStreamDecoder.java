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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract base for CSV stream decoders that parse a multi-row CSV stream and
 * delegate per-row scalar decoding to a child {@link CsvDecoder}.
 *
 * <p>Subclasses implement format-specific parsing (populating {@link #currentRow})
 * in {@link #decodeObject(Argument)}; all primitive {@link Decoder} methods are
 * delegated to the current row decoder by default.</p>
 *
 * @since 4.0.0
 */
public abstract class AbstractCsvStreamDecoder implements CsvStreamDecoder {

    @Nullable
    protected CsvDecoder currentRow;

    protected AbstractCsvStreamDecoder() {
    }

    protected AbstractCsvStreamDecoder(Map<String, @Nullable String> row) {
        this.currentRow = CsvDecoder.forRow(row);
    }

    @Override
    public String decodeKey() throws IOException {
        if (currentRow == null) {
            return "";
        }
        String key = currentRow.decodeKey();
        return key == null ? "" : key;
    }

    @Override
    public String decodeString() throws IOException {
        return currentRow == null ? "" : currentRow.decodeString();
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return currentRow == null ? false : currentRow.decodeBoolean();
    }

    @Override
    public byte decodeByte() throws IOException {
        return currentRow == null ? (byte) 0 : currentRow.decodeByte();
    }

    @Override
    public short decodeShort() throws IOException {
        return currentRow == null ? (short) 0 : currentRow.decodeShort();
    }

    @Override
    public char decodeChar() throws IOException {
        return currentRow == null ? '\0' : currentRow.decodeChar();
    }

    @Override
    public int decodeInt() throws IOException {
        return currentRow == null ? 0 : currentRow.decodeInt();
    }

    @Override
    public long decodeLong() throws IOException {
        return currentRow == null ? 0L : currentRow.decodeLong();
    }

    @Override
    public float decodeFloat() throws IOException {
        return currentRow == null ? 0f : currentRow.decodeFloat();
    }

    @Override
    public double decodeDouble() throws IOException {
        return currentRow == null ? 0.0 : currentRow.decodeDouble();
    }

    @Override
    public java.math.BigInteger decodeBigInteger() throws IOException {
        return currentRow == null ? java.math.BigInteger.ZERO : currentRow.decodeBigInteger();
    }

    @Override
    public java.math.BigDecimal decodeBigDecimal() throws IOException {
        return currentRow == null ? java.math.BigDecimal.ZERO : currentRow.decodeBigDecimal();
    }

    @Override
    public boolean decodeNull() throws IOException {
        return currentRow == null ? false : currentRow.decodeNull();
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        return currentRow == null ? this : currentRow.decodeArray(type);
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return false;
    }

    @Override
    public void skipValue() throws IOException {
        if (currentRow != null) {
            currentRow.skipValue();
        }
    }

    @Override
    public void finishStructure() throws IOException {
        if (currentRow != null) {
            currentRow.finishStructure();
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (currentRow != null) {
            currentRow.finishStructure(consumeLeftElements);
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return currentRow == null ? new IOException(message) : currentRow.createDeserializationException(message, invalidValue);
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        return currentRow == null ? this : currentRow.decodeBuffer();
    }

    @Override
    @Nullable
    public Object decodeArbitrary() throws IOException {
        return currentRow == null ? null : currentRow.decodeArbitrary();
    }

    @Override
    public io.micronaut.json.tree.JsonNode decodeNode() throws IOException {
        if (currentRow != null) {
            return currentRow.decodeNode();
        }
        throw createDeserializationException("decodeNode() not supported in CSV decoder.", null);
    }

    @Override
    public void close() throws IOException {
        if (currentRow != null) {
            currentRow.close();
        }
    }
}
