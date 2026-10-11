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
package io.micronaut.serde.fory.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * Decoder implementation: a lazy token state machine on top of Fory's character-level {@link Utf8JsonReader}.
 * Scalar tokens are classified by peeking one character; the value itself is only parsed when a
 * getter is called, and skipped otherwise.
 */
@Internal
final class ForyDecoder extends AbstractStreamDecoder implements KeysAwareDecoder {
    private final Utf8JsonReader reader;
    private boolean[] objectFrames = new boolean[16];
    private int[] expectedKeys = new int[16];
    private int depth;
    private @Nullable TokenType current;
    private boolean consumed;
    private @Nullable String key;
    private boolean keyPending;
    private @Nullable Keys currentKeys;
    private ForyKeysProvider.@Nullable ForyKeys currentForyKeys;

    ForyDecoder(Utf8JsonReader reader) {
        super(DEFAULT_LIMITS);
        this.reader = reader;
        classifyValue();
    }

    private void classifyValue() {
        consumed = false;
        current = switch (reader.peekToken()) {
            case '{' -> TokenType.START_OBJECT;
            case '[' -> TokenType.START_ARRAY;
            case '"' -> TokenType.STRING;
            case 't', 'f' -> TokenType.BOOLEAN;
            case 'n' -> TokenType.NULL;
            default -> TokenType.NUMBER;
        };
    }

    private void push(boolean object) {
        if (++depth == objectFrames.length) {
            objectFrames = Arrays.copyOf(objectFrames, depth * 2);
            expectedKeys = Arrays.copyOf(expectedKeys, depth * 2);
        }
        objectFrames[depth] = object;
        expectedKeys[depth] = 0;
    }

    private void readKey() {
        // the name is read lazily: decodeKey(Keys) may match it as raw bytes without a String
        keyPending = true;
        current = TokenType.KEY;
    }

    private void materializeKey() {
        if (keyPending) {
            key = reader.readFieldName();
            reader.expectNextToken(':');
            keyPending = false;
        }
    }

    @Override
    public int decodeKey(Keys keys) throws IOException {
        TokenType token = current;
        if (token == TokenType.END_OBJECT) {
            return MATCH_END_OBJECT;
        }
        if (token != TokenType.KEY) {
            return MATCH_UNKNOWN_NAME;
        }
        if (keyPending) {
            ForyKeysProvider.ForyKeys foryKeys = currentForyKeys;
            if (keys != currentKeys || foryKeys == null) {
                foryKeys = ForyKeysProvider.get(keys);
                currentKeys = keys;
                currentForyKeys = foryKeys;
            }
            int expected = expectedKeys[depth];
            if (expected < foryKeys.nameLength.length && foryKeys.canMatch(expected)) {
                reader.peekToken();
                boolean matched = foryKeys.isOneWord(expected)
                    ? reader.tryReadNextFieldNameToken0(foryKeys.readWord[expected], foryKeys.readMask[expected], foryKeys.nameLength[expected])
                    : reader.tryReadNextFieldNameToken8(foryKeys.readWord[expected], foryKeys.readSuffix[expected], foryKeys.readMask[expected], foryKeys.nameLength[expected]);
                if (matched) {
                    keyPending = false;
                    expectedKeys[depth] = expected + 1;
                    classifyValue();
                    return expected;
                }
            }
        }
        int index = keys.indexOf(getCurrentKey());
        if (index < 0) {
            // the name stays available through decodeKey()
            return MATCH_UNKNOWN_NAME;
        }
        expectedKeys[depth] = index + 1;
        classifyValue();
        return index;
    }

    private void afterValue() {
        if (depth == 0) {
            current = null;
        } else if (objectFrames[depth]) {
            if (reader.consumeNextCommaOrEndObject()) {
                readKey();
            } else {
                depth--;
                current = TokenType.END_OBJECT;
            }
        } else if (reader.consumeNextCommaOrEndArray()) {
            classifyValue();
        } else {
            depth--;
            current = TokenType.END_ARRAY;
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        nextToken();
    }

    @Override
    protected @Nullable TokenType currentToken() {
        return current;
    }

    @Override
    protected void nextToken() {
        TokenType token = current;
        if (token == null) {
            return;
        }
        switch (token) {
            case START_OBJECT -> {
                reader.expectNextToken('{');
                push(true);
                if (reader.consumeNextToken('}')) {
                    depth--;
                    current = TokenType.END_OBJECT;
                } else {
                    readKey();
                }
            }
            case START_ARRAY -> {
                reader.expectNextToken('[');
                push(false);
                if (reader.consumeNextToken(']')) {
                    depth--;
                    current = TokenType.END_ARRAY;
                } else {
                    classifyValue();
                }
            }
            case KEY -> {
                materializeKey();
                classifyValue();
            }
            case END_OBJECT, END_ARRAY -> afterValue();
            default -> {
                if (!consumed) {
                    reader.skipValue();
                }
                afterValue();
            }
        }
    }

    @Override
    public String decodeString() throws IOException {
        if (current == TokenType.STRING) {
            // fast path: read the classified string token and advance without the generic token dispatch
            String value = reader.readString();
            afterValue();
            return value;
        }
        return super.decodeString();
    }

    @Override
    public @Nullable String decodeStringNullable() throws IOException {
        TokenType token = current;
        if (token == TokenType.STRING) {
            String value = reader.readString();
            afterValue();
            return value;
        }
        if (token == TokenType.NULL) {
            reader.skipValue();
            afterValue();
            return null;
        }
        return decodeNull() ? null : super.decodeString();
    }

    @Override
    protected String getCurrentKey() {
        materializeKey();
        return Objects.requireNonNull(key);
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) {
        consumed = true;
        return switch (currentToken) {
            case STRING -> reader.readString();
            case BOOLEAN -> Boolean.toString(reader.readBoolean());
            default -> reader.readNumberAsString();
        };
    }

    @Override
    protected String getString() {
        consumed = true;
        return reader.readString();
    }

    @Override
    protected boolean getBoolean() {
        consumed = true;
        return reader.readBoolean();
    }

    @Override
    protected long getLong() {
        consumed = true;
        return reader.readLong();
    }

    @Override
    protected int getInteger() {
        consumed = true;
        return reader.readInt();
    }

    @Override
    protected double getDouble() {
        consumed = true;
        return reader.readDouble();
    }

    @Override
    protected BigInteger getBigInteger() {
        consumed = true;
        return reader.readBigInteger();
    }

    @Override
    protected BigDecimal getBigDecimal() {
        consumed = true;
        return reader.readBigDecimal();
    }

    @Override
    protected Number getBestNumber() {
        consumed = true;
        return reader.readNumber();
    }

    @Override
    protected void skipChildren() {
        if (current == TokenType.START_OBJECT) {
            reader.skipValue();
            consumed = true;
            current = TokenType.END_OBJECT;
        } else if (current == TokenType.START_ARRAY) {
            reader.skipValue();
            consumed = true;
            current = TokenType.END_ARRAY;
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        String fullMessage = message + " at JSON position " + reader.position();
        if (invalidValue != null) {
            return new InvalidFormatException(fullMessage, null, invalidValue);
        }
        return new SerdeException(fullMessage);
    }

    @Override
    protected void consumeLeftElements(TokenType currentToken) throws IOException {
        // the structure may be left with a pending key or value, unlike the base implementation assumes
        while (current != TokenType.END_OBJECT && current != TokenType.END_ARRAY) {
            if (current == null) {
                throw createDeserializationException("Unexpected end of input", null);
            }
            if (current == TokenType.KEY) {
                nextToken();
            } else {
                skipValue();
            }
        }
    }
}
