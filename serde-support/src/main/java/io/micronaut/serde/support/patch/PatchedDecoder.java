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
package io.micronaut.serde.support.patch;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Exposes a validated token tape to the ordinary serde deserializers. */
@Internal
final class PatchedDecoder extends AbstractStreamDecoder {
    private final TokenReader reader;
    private final ReplayStore.Scope scope;
    private final @Nullable ReplayStore ownedStore;

    /**
     * Creates a decoder over a validated patch result.
     * @param reader Token cursor
     * @param limits Remaining limits
     * @param coercionPolicy Mapper coercion policy
     * @param scope Execution resource owner
     * @param ownedStore Buffer owned by this decoder, if any
     */
    PatchedDecoder(TokenReader reader, RemainingLimits limits, CoercionPolicy coercionPolicy,
                   ReplayStore.Scope scope, @Nullable ReplayStore ownedStore) {
        super(limits, coercionPolicy);
        this.reader = reader;
        this.scope = scope;
        this.ownedStore = ownedStore;
    }

    @Override
    protected @Nullable TokenType currentToken() {
        PatchToken token = reader.current();
        if (token == null) {
            return null;
        }
        return switch (token) {
            case START_OBJECT -> TokenType.START_OBJECT;
            case END_OBJECT -> TokenType.END_OBJECT;
            case START_ARRAY -> TokenType.START_ARRAY;
            case END_ARRAY -> TokenType.END_ARRAY;
            case KEY -> TokenType.KEY;
            case STRING -> TokenType.STRING;
            case NUMBER -> TokenType.NUMBER;
            case TRUE, FALSE -> TokenType.BOOLEAN;
            case NULL -> TokenType.NULL;
        };
    }

    @Override
    protected void nextToken() throws IOException {
        reader.next();
        releaseIfExhausted();
    }

    private void releaseIfExhausted() throws IOException {
        if (reader.current() == null && ownedStore != null) {
            ownedStore.close();
        }
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        ReplayStore buffer = new ReplayStore(scope);
        TokenIO.copyValue(reader, buffer);
        releaseIfExhausted();
        return new PatchedDecoder(buffer.reader(), ourLimits(), getCoercionPolicy(), scope, buffer);
    }

    @Override
    protected void consumeLeftElements(TokenType currentToken) throws IOException {
        while (reader.current() != PatchToken.END_OBJECT && reader.current() != PatchToken.END_ARRAY) {
            if (reader.current() == PatchToken.KEY) {
                nextToken();
            }
            skipValue();
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        nextToken();
    }

    @Override
    protected String getCurrentKey() {
        return reader.text();
    }

    @Override
    protected String getString() {
        return reader.text();
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) {
        return currentToken == TokenType.BOOLEAN ? Boolean.toString(getBoolean()) : reader.text();
    }

    @Override
    protected boolean getBoolean() {
        return reader.current() == PatchToken.TRUE;
    }

    @Override
    protected long getLong() throws IOException {
        BigDecimal value = getBigDecimal();
        if (value.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0 || value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw createDeserializationException("Integer out of range", value);
        }
        return value.longValue();
    }

    @Override
    protected int getInteger() throws IOException {
        BigDecimal value = getBigDecimal();
        if (value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0 || value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw createDeserializationException("Integer out of range", value);
        }
        return value.intValue();
    }

    @Override
    protected double getDouble() {
        return Double.parseDouble(reader.text());
    }

    @Override
    protected BigInteger getBigInteger() {
        return getBigDecimal().toBigInteger();
    }

    @Override
    protected BigDecimal getBigDecimal() {
        return new BigDecimal(reader.text());
    }

    @Override
    protected boolean isCurrentNumberFloat() {
        String text = reader.text();
        return text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0;
    }

    @Override
    protected Number getBestNumber() {
        if (isCurrentNumberFloat()) {
            return getBigDecimal();
        }
        BigInteger integer = new BigInteger(reader.text());
        if (integer.bitLength() < 32) {
            return integer.intValue();
        }
        if (integer.bitLength() < 64) {
            return integer.longValue();
        }
        return integer;
    }

    @Override
    protected void skipChildren() throws IOException {
        if (reader.current() == PatchToken.START_ARRAY || reader.current() == PatchToken.START_OBJECT) {
            int depth = 1;
            while (depth != 0) {
                reader.next();
                PatchToken token = reader.current();
                if (token == null) {
                    throw new IOException("Unexpected end of token stream");
                }
                if (token == PatchToken.START_ARRAY || token == PatchToken.START_OBJECT) {
                    depth++;
                } else if (token == PatchToken.END_ARRAY || token == PatchToken.END_OBJECT) {
                    depth--;
                }
            }
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return new SerdeException(message);
    }
}
