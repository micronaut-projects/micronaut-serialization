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
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decodes CSV records from a logical row map into {@link Decoder} token events.
 */
@Internal
public abstract class CsvDecoder extends AbstractStreamDecoder implements CsvStreamDecoder {

    private final Map<String, @Nullable String> row;
    private final List<String> columns;
    private int index = -1;
    private AbstractStreamDecoder.@Nullable TokenType token = AbstractStreamDecoder.TokenType.START_OBJECT;
    @Nullable
    private String currentKey;
    @Nullable
    private String currentValue;

    protected CsvDecoder(Map<String, @Nullable String> row) {
        super(LimitingStream.DEFAULT_LIMITS);
        this.row = row;
        this.columns = new ArrayList<>(row.keySet());
    }

    /**
     * Returns the logical row backing this decoder.
     *
     * @return the logical row backing this decoder
     */
    public Map<String, @Nullable String> row() {
        return row;
    }

    @Override
    protected AbstractStreamDecoder.TokenType currentToken() {
        AbstractStreamDecoder.TokenType t = token;
        if (t == null) {
            throw new IllegalStateException("No current token");
        }
        return t;
    }

    @Override
    protected void nextToken() {
        AbstractStreamDecoder.TokenType t = token;
        if (t == null) {
            return;
        }
        switch (t) {
            case START_OBJECT -> {
                index = 0;
                advanceToKeyOrEnd();
            }
            case KEY -> {
                token = AbstractStreamDecoder.TokenType.STRING;
            }
            case STRING -> {
                index++;
                advanceToKeyOrEnd();
            }
            case END_OBJECT -> {
                token = null;
            }
            default -> {
                // no-op
            }
        }
    }

    private void advanceToKeyOrEnd() {
        if (index < columns.size()) {
            currentKey = columns.get(index);
            currentValue = row.get(currentKey);
            token = AbstractStreamDecoder.TokenType.KEY;
        } else {
            currentKey = null;
            currentValue = null;
            token = AbstractStreamDecoder.TokenType.END_OBJECT;
        }
    }

    @Override
    protected String getCurrentKey() {
        return Objects.requireNonNull(currentKey, "Not at a field name");
    }

    @Override
    protected String getString() {
        return currentValue == null ? "" : currentValue;
    }

    @Override
    protected boolean getBoolean() {
        return Boolean.parseBoolean(currentValue);
    }

    @Override
    protected long getLong() {
        return Long.parseLong(currentValue == null ? "" : currentValue);
    }

    @Override
    protected double getDouble() {
        return Double.parseDouble(currentValue == null ? "" : currentValue);
    }

    @Override
    protected BigInteger getBigInteger() {
        return new BigInteger(currentValue == null ? "" : currentValue);
    }

    @Override
    protected BigDecimal getBigDecimal() throws IOException {
        return new BigDecimal(currentValue == null ? "" : currentValue);
    }

    @Override
    protected Number getBestNumber() throws IOException {
        String v = currentValue == null ? "" : currentValue;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e2) {
                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException e3) {
                    return new BigDecimal(v);
                }
            }
        }
    }

    @Override
    protected String coerceScalarToString(AbstractStreamDecoder.TokenType currentToken) throws IOException {
        return currentValue == null ? "" : currentValue;
    }

    @Override
    protected void skipChildren() throws IOException {
        // no-op: CSV cells are flat strings
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        throw createDeserializationException("decodeNode() not supported in CSV decoder.", currentValue);
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        return this;
    }

    public static CsvDecoder forRow(Map<String, @Nullable String> row) {
        return new CsvDecoder(row) {
            @Override
            public void close() {
                // no-op
            }

            @Override
            public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
                return new IOException(message);
            }
        };
    }

    @Override
    public boolean hasNextRow() {
        return false;
    }

    @Override
    public boolean hasNextRow(Argument<?> type) {
        return hasNextRow();
    }
}
