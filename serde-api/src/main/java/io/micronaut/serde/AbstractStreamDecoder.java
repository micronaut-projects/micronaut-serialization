/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.util.JsonNodeDecoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Abstract base class for stream-based {@link Decoder}s.
 */
@Internal
public abstract class AbstractStreamDecoder implements Decoder {
    @Nullable
    private final AbstractStreamDecoder parent;

    private AbstractStreamDecoder child = null;

    private boolean currentlyUnwrappingArray = false;

    @NonNull
    private final Class<?> view;

    /**
     * Child constructor. Should inherit the parser from the parent.
     * @param parent The parent decoder.
     */
    protected AbstractStreamDecoder(@NonNull AbstractStreamDecoder parent) {
        this.parent = parent;
        this.view = parent.view;
    }

    /**
     * Root constructor.
     * @param view The selected view class.
     */
    protected AbstractStreamDecoder(@NonNull Class<?> view) {
        this.parent = null;
        this.view = view;
    }

    protected enum TokenType {
        START_ARRAY, END_ARRAY,
        START_OBJECT, END_OBJECT,
        KEY,
        NUMBER, STRING, BOOLEAN, NULL,
        OTHER
    }

    protected abstract TokenType currentToken();

    protected abstract void nextToken() throws IOException;

    /**
     * @param expected The token type that was expected in place of {@link #currentToken()}.
     * @return The exception that should be thrown to signify an unexpected token.
     */
    protected IOException unexpectedToken(TokenType expected) {
        return createDeserializationException("Unexpected token " + currentToken() + ", expected " + expected);
    }

    private void checkChild() {
        if (child != null) {
            throw new IllegalStateException("There is still an unfinished child parser");
        }
        if (parent != null && parent.child != this) {
            throw new IllegalStateException("This child parser has already completed");
        }
    }

    /**
     * Should be called before attempting to decode a value. Has basic sanity checks, such as confirming we're not
     * currently in a {@link TokenType#KEY} and that there's no child decoder currently running.
     */
    private void preDecodeValue() {
        checkChild();
        if (currentToken() == TokenType.KEY) {
            throw new IllegalStateException("Haven't parsed field name yet");
        }
    }

    private boolean beginUnwrapArray() throws IOException {
        if (currentlyUnwrappingArray) {
            return false;
        }
        if (currentToken() != TokenType.START_ARRAY) {
            throw new IllegalStateException("Not an array");
        }
        currentlyUnwrappingArray = true;
        nextToken();
        return true;
    }

    private boolean endUnwrapArray() throws IOException {
        currentlyUnwrappingArray = false;
        if (currentToken() == TokenType.END_ARRAY) {
            nextToken();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final void finishStructure() throws IOException {
        checkChild();
        TokenType currentToken = currentToken();
        if (currentToken != TokenType.END_ARRAY && currentToken != TokenType.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
        if (parent != null) {
            parent.child = null;
            parent.backFromChild(this);
        }
    }

    /**
     * Called when the old child has finished processing and returns control of the stream to the parent. Current token
     * is assumed to be {@link TokenType#END_ARRAY} or {@link TokenType#END_OBJECT}. However we {@link #currentToken()}
     * may hold an outdated value until {@link #nextToken()} is called, which this method does.
     *
     * @param child The now-invalid child decoder.
     */
    protected void backFromChild(AbstractStreamDecoder child) throws IOException {
        nextToken();
    }

    @Override
    public final boolean hasNextArrayValue() {
        checkChild();
        return currentToken() != TokenType.END_ARRAY;
    }

    /**
     * Get the current object field name. Only called for {@link TokenType#KEY}.
     * @return The current field key
     */
    protected abstract String getCurrentKey() throws IOException;

    @Nullable
    @Override
    public final String decodeKey() throws IOException {
        checkChild();
        TokenType currentToken = currentToken();
        if (currentToken == TokenType.END_OBJECT) {
            // stay on the end token, will be handled in finishStructure
            return null;
        }
        if (currentToken != TokenType.KEY) {
            throw new IllegalStateException("Not at a field name");
        }
        String fieldName = getCurrentKey();
        nextToken();
        return fieldName;
    }

    /**
     * Create a new child decoder using {@link AbstractStreamDecoder#AbstractStreamDecoder(AbstractStreamDecoder)}.
     * @return The new decoder
     */
    protected abstract AbstractStreamDecoder createChildDecoder();

    @NonNull
    @Override
    public final Decoder decodeArray(Argument<?> type) throws IOException {
        preDecodeValue();
        if (currentToken() != TokenType.START_ARRAY) {
            throw unexpectedToken(TokenType.START_ARRAY);
        }
        nextToken();
        child = createChildDecoder();
        return child;
    }

    @NonNull
    @Override
    public final Decoder decodeObject(Argument<?> type) throws IOException {
        preDecodeValue();
        if (currentToken() != TokenType.START_OBJECT) {
            throw unexpectedToken(TokenType.START_OBJECT);
        }
        nextToken();
        child = createChildDecoder();
        return child;
    }

    /**
     * Decode any non-null scalar value (number, string or boolean) to its string representation.
     * @return The current value, coerced to a string
     */
    protected abstract String coerceScalarToString() throws IOException;

    @NonNull
    @Override
    public final String decodeString() throws IOException {
        preDecodeValue();
        switch (currentToken()) {
            case NUMBER:
            case STRING:
            case BOOLEAN:
            case OTHER:
                String value = coerceScalarToString();
                nextToken();
                return value;
            case START_ARRAY:
                if (beginUnwrapArray()) {
                    String unwrapped = decodeString();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values");
                    }
                }
            default:
                throw unexpectedToken(TokenType.STRING);
        }
    }

    /**
     * Decode the current {@link TokenType#BOOLEAN} value. Called for no other token type.
     * @return The boolean value
     */
    protected abstract boolean getBoolean() throws IOException;

    @Override
    public final boolean decodeBoolean() throws IOException {
        preDecodeValue();
        switch (currentToken()) {
            case BOOLEAN:
                boolean bool = getBoolean();
                nextToken();
                return bool;
            case NUMBER:
                double number = getDouble();
                nextToken();
                return number != 0;
            case STRING:
                String string = coerceScalarToString();
                nextToken();
                return string.equals("true");
            case START_ARRAY:
                if (beginUnwrapArray()) {
                    boolean unwrapped = decodeBoolean();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    }
                }
            default:
                throw unexpectedToken(TokenType.BOOLEAN);
        }
    }

    @Override
    public final byte decodeByte() throws IOException {
        return (byte) decodeInteger(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    @Override
    public final short decodeShort() throws IOException {
        return (short) decodeInteger(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public final char decodeChar() throws IOException {
        return (char) decodeInteger(Character.MIN_VALUE, Character.MAX_VALUE, true);
    }

    @Override
    public final int decodeInt() throws IOException {
        return (int) decodeInteger(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public final long decodeLong() throws IOException {
        return decodeInteger(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private long decodeInteger(long min, long max) throws IOException {
        return decodeInteger(min, max, false);
    }

    /**
     * Decode the current {@link TokenType#NUMBER} value as a long value. Called for no other token type.
     * @return The number value
     */
    protected abstract long getLong() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a double value. Called for no other token type.
     * @return The number value
     */
    protected abstract double getDouble() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a {@link BigInteger} value. Called for no other token type.
     * @return The number value
     */
    protected abstract BigInteger getBigInteger() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a {@link BigDecimal} value. Called for no other token type.
     * @return The number value
     */
    protected abstract BigDecimal getBigDecimal() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a {@link Number} value. Called for no other token type.
     * @return The number value
     */
    protected abstract Number getBestNumber() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a numeric {@link JsonNode}. Called for no other token type.
     * Default implementation tries to construct a node from {@link #getBestNumber()}.
     * @return The number value
     */
    protected JsonNode getBestNumberNode() throws IOException {
        Number number = getBestNumber();
        if (number instanceof Byte || number instanceof Short || number instanceof Integer) {
            return JsonNode.createNumberNode(number.intValue());
        } else if (number instanceof Long) {
            return JsonNode.createNumberNode(number.longValue());
        } else if (number instanceof Float) {
            return JsonNode.createNumberNode(number.floatValue());
        } else if (number instanceof Double) {
            return JsonNode.createNumberNode(number.doubleValue());
        } else if (number instanceof BigInteger) {
            return JsonNode.createNumberNode((BigInteger) number);
        } else if (number instanceof BigDecimal) {
            return JsonNode.createNumberNode((BigDecimal) number);
        } else {
            // fallback, unknown number type
            return JsonNode.createNumberNode(getBigDecimal());
        }
    }

    private long decodeInteger(long min, long max, boolean stringsAsChars) throws IOException {
        preDecodeValue();
        switch (currentToken()) {
            case STRING:
                String string = coerceScalarToString();
                if (stringsAsChars) {
                    if (string.length() != 1) {
                        throw createDeserializationException("When decoding char value, must give a single character");
                    }
                    char c = string.charAt(0);
                    nextToken();
                    return c;
                } else {
                    long value;
                    try {
                        value = Long.parseLong(string);
                    } catch (NumberFormatException e) {
                        throw createDeserializationException("Unable to coerce string to integer");
                    }
                    nextToken();
                    return value;
                }
            case NUMBER:
                // todo: better coercion rules
                long number = getLong();
                nextToken();
                return number;
            case BOOLEAN:
                boolean bool = getBoolean();
                nextToken();
                return bool ? 1 : 0;
            case START_ARRAY:
                if (beginUnwrapArray()) {
                    long unwrapped = decodeInteger(min, max);
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one integer, but got array of multiple values");
                    }
                }
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
    }

    @Override
    public final float decodeFloat() throws IOException {
        return (float) decodeDouble();
    }

    @Override
    public final double decodeDouble() throws IOException {
        // could use decodeNumber but this is more efficient
        preDecodeValue();
        switch (currentToken()) {
            case NUMBER:
                double value = getDouble();
                nextToken();
                return value;
            case STRING:
                String string = coerceScalarToString();
                double number;
                try {
                    number = Double.parseDouble(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to double");
                }
                nextToken();
                return number;
            case BOOLEAN:
                boolean bool = getBoolean();
                nextToken();
                return bool ? 1 : 0;
            case START_ARRAY:
                if (beginUnwrapArray()) {
                    double unwrapped = decodeDouble();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one float, but got array of multiple values");
                    }
                }
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
    }

    @NonNull
    @Override
    public final BigInteger decodeBigInteger() throws IOException {
        return decodeNumber(AbstractStreamDecoder::getBigInteger, BigInteger::new, BigInteger.ZERO, BigInteger.ONE);
    }

    @NonNull
    @Override
    public final BigDecimal decodeBigDecimal() throws IOException {
        return decodeNumber(AbstractStreamDecoder::getBigDecimal, BigDecimal::new, BigDecimal.ZERO, BigDecimal.ONE);
    }

    /**
     * Decode a number type, applying all necessary coercions.
     *
     * @param fromNumberToken Called if {@link #currentToken()} is a {@link TokenType#NUMBER}.
     * @param fromString      Called for the textual value if {@link #currentToken()} is a {@link TokenType#STRING}. Should throw {@link NumberFormatException} on parse failure.
     * @param zero            The zero value.
     * @param one             The one value.
     * @param <T>             The number type.
     * @return The parsed number.
     */
    protected final <T> T decodeNumber(
            ValueDecoder<T> fromNumberToken,
            Function<String, T> fromString,
            T zero, T one
    ) throws IOException {
        preDecodeValue();
        T value;
        switch (currentToken()) {
            case NUMBER:
                value = fromNumberToken.decode(this);
                break;
            case STRING:
                try {
                    value = fromString.apply(coerceScalarToString());
                } catch (NumberFormatException e) {
                    // match behavior of getValueAsDouble
                    value = zero;
                }
                break;
            case BOOLEAN:
                value = getBoolean() ? one : zero;
                break;
            case START_ARRAY:
                if (beginUnwrapArray()) {
                    T unwrapped = decodeNumber(fromNumberToken, fromString, zero, one);
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one float, but got array of multiple values");
                    }
                }
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
        nextToken();
        return value;
    }

    /**
     * Decode a custom type.
     *
     * @param readFunction Function to call for reading the value. The {@link AbstractStreamDecoder} parameter to the function will just be {@code this}, but this allows subclasses to avoid capturing {@code this} to avoid an allocation.
     * @param <T> Value type
     * @return The parsed value.
     */
    protected final <T> T decodeCustom(ValueDecoder<T> readFunction) throws IOException {
        preDecodeValue();
        T value = readFunction.decode(this);
        nextToken();
        return value;
    }

    @Override
    public final boolean decodeNull() throws IOException {
        preDecodeValue();
        if (currentToken() == TokenType.NULL) {
            nextToken();
            return true;
        } else {
            // we don't support unwrapping null values from arrays, because the api user wouldn't be able to distinguish
            // `[null]` and `null` anymore.
            return false;
        }
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        JsonNode node = decodeNode();
        return JsonNodeDecoder.create(node);
    }

    private JsonNode decodeNode() throws IOException {
        switch (currentToken()) {
            case START_OBJECT:
                return decodeObjectNode((AbstractStreamDecoder) decodeObject());
            case START_ARRAY:
                return decodeArrayNode((AbstractStreamDecoder) decodeArray());
            case STRING:
                return JsonNode.createStringNode(decodeString());
            case NUMBER:
                preDecodeValue();
                JsonNode bestNumberNode = getBestNumberNode();
                nextToken();
                return bestNumberNode;
            case BOOLEAN:
                return JsonNode.createBooleanNode(decodeBoolean());
            case NULL:
                decodeNull();
                return JsonNode.nullNode();
            default:
                throw createDeserializationException("Unexpected token " + currentToken() + ", expected value");
        }
    }

    private static JsonNode decodeObjectNode(AbstractStreamDecoder elementDecoder) throws IOException {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        while (true) {
            String key = elementDecoder.decodeKey();
            if (key == null) {
                break;
            }
            result.put(key, elementDecoder.decodeNode());
        }
        elementDecoder.finishStructure();
        return JsonNode.createObjectNode(result);
    }

    private static JsonNode decodeArrayNode(AbstractStreamDecoder elementDecoder) throws IOException {
        List<JsonNode> result = new ArrayList<>();
        while (elementDecoder.hasNextArrayValue()) {
            result.add(elementDecoder.decodeNode());
        }
        elementDecoder.finishStructure();
        return JsonNode.createArrayNode(result);
    }

    @Nullable
    @Override
    public final Object decodeArbitrary() throws IOException {
        switch (currentToken()) {
            case START_OBJECT:
                return decodeArbitraryMap(decodeObject());
            case START_ARRAY:
                return decodeArbitraryList(decodeArray());
            case STRING:
                return decodeString();
            case NUMBER:
                preDecodeValue();
                Number bestNumber = getBestNumber();
                nextToken();
                return bestNumber;
            case BOOLEAN:
                return decodeBoolean();
            case NULL:
                decodeNull();
                return null;
            default:
                throw createDeserializationException("Unexpected token " + currentToken() + ", expected value");
        }
    }

    private static Map<String, Object> decodeArbitraryMap(Decoder elementDecoder) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        while (true) {
            String key = elementDecoder.decodeKey();
            if (key == null) {
                break;
            }
            result.put(key, elementDecoder.decodeArbitrary());
        }
        elementDecoder.finishStructure();
        return result;
    }

    private static List<Object> decodeArbitraryList(Decoder elementDecoder) throws IOException {
        List<Object> result = new ArrayList<>();
        while (elementDecoder.hasNextArrayValue()) {
            result.add(elementDecoder.decodeArbitrary());
        }
        elementDecoder.finishStructure();
        return result;
    }

    /**
     * If we are at a {@link TokenType#START_OBJECT} or {@link TokenType#START_ARRAY}, skip to the matching
     * {@link TokenType#END_OBJECT} or {@link TokenType#END_ARRAY}. Else, do nothing.
     */
    protected abstract void skipChildren() throws IOException;

    @Override
    public final void skipValue() throws IOException {
        preDecodeValue();
        skipChildren();
        nextToken();
    }

    /**
     * Decoder function for a single value.
     * @param <R> Value type
     */
    @Internal
    public interface ValueDecoder<R> {
        /**
         * Decode this value.
         *
         * @param target Reference to {@code this}, allows subclasses to avoid capturing {@code this} to avoid an allocation.
         * @return The decoded value
         */
        R decode(AbstractStreamDecoder target) throws IOException;
    }
}
