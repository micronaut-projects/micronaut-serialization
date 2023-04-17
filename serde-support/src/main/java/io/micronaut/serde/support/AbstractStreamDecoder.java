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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.support.util.JsonNodeDecoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Abstract base class for stream-based {@link io.micronaut.serde.Decoder}s.
 */
@Internal
public abstract class AbstractStreamDecoder implements Decoder {

    private boolean currentlyUnwrappingArray = false;

    /**
     * The token type.
     */
    protected enum TokenType {
        /**
         * Start of an array.
         */
        START_ARRAY,
        /**
         * End of an array.
         */
        END_ARRAY,
        /**
         * Start of an object.
         */
        START_OBJECT,
        /**
         * End of an object.
         */
        END_OBJECT,
        /**
         * A key.
         */
        KEY,
        /**
         * A number.
         */
        NUMBER,
        /**
         * A string.
         */
        STRING,
        /**
         * A boolean.
         */
        BOOLEAN,
        /**
         * A {@code null} value.
         */
        NULL,
        /**
         * Any other token.
         */
        OTHER
    }

    /**
     * @return The current token.
     */
    protected abstract @Nullable TokenType currentToken();

    /**
     * Move to the next token.
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract void nextToken() throws IOException;

    /**
     * @param expected The token type that was expected in place of {@link #currentToken()}.
     * @return The exception that should be thrown to signify an unexpected token.
     */
    protected IOException unexpectedToken(TokenType expected) {
        return createDeserializationException("Unexpected token " + currentToken() + ", expected " + expected, null);
    }

    /**
     * Should be called before attempting to decode a value. Has basic sanity checks, such as confirming we're not
     * currently in a {@link TokenType#KEY} and that there's no child decoder currently running.
     * @param currentToken The current token
     */
    protected void preDecodeValue(TokenType currentToken) {
        if (currentToken == TokenType.KEY) {
            throw new IllegalStateException("Haven't parsed field name yet");
        }
    }

    private boolean beginUnwrapArray(TokenType currentToken) throws IOException {
        if (currentlyUnwrappingArray) {
            return false;
        }
        if (currentToken != TokenType.START_ARRAY) {
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
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        TokenType currentToken = currentToken();
        if (consumeLeftElements) {
            consumeLeftElements(currentToken);
        } else if (currentToken != TokenType.END_ARRAY && currentToken != TokenType.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
    }

    /**
     * Consumes left elements.
     * @param currentToken The current token
     * @throws IOException
     */
    protected void consumeLeftElements(TokenType currentToken) throws IOException {
        while (true) {
            final String key = decodeKey();
            if (key == null) {
                break;
            }
            skipValue();
        }
    }

    @Override
    public boolean hasNextArrayValue() {
        return currentToken() != TokenType.END_ARRAY;
    }

    /**
     * Get the current object field name. Only called for {@link TokenType#KEY}.
     * @return The current field key
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract String getCurrentKey() throws IOException;

    @Nullable
    @Override
    public String decodeKey() throws IOException {
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

    @NonNull
    @Override
    public final Decoder decodeArray(Argument<?> type) throws IOException {
        return decodeArray0(currentToken());
    }

    /**
     * Decodes the array.
     * @param currentToken The current token
     * @return The decoder
     * @throws IOException The exception
     */
    protected AbstractStreamDecoder decodeArray0(TokenType currentToken) throws IOException {
        preDecodeValue(currentToken);
        if (currentToken != TokenType.START_ARRAY) {
            throw unexpectedToken(TokenType.START_ARRAY);
        }
        nextToken();
        return this;
    }

    @NonNull
    @Override
    public final Decoder decodeObject(Argument<?> type) throws IOException {
        return decodeObject0(currentToken());
    }

    /**
     * Decodes the object.
     * @param currentToken The current token
     * @return The decoder
     * @throws IOException The exception
     */
    protected AbstractStreamDecoder decodeObject0(TokenType currentToken) throws IOException {
        preDecodeValue(currentToken);
        if (currentToken != TokenType.START_OBJECT) {
            throw unexpectedToken(TokenType.START_OBJECT);
        }
        nextToken();
        return this;
    }

    /**
     * Decode any non-null scalar value (number, string or boolean) to its string representation.
     * @param currentToken The current token
     * @return The current value, coerced to a string
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract String coerceScalarToString(TokenType currentToken) throws IOException;

    @NonNull
    @Override
    public String decodeString() throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        switch (currentToken) {
            case STRING:
                String text = getString();
                nextToken();
                return text;
            case NUMBER:
            case BOOLEAN:
            case OTHER:
                String value = coerceScalarToString(currentToken);
                nextToken();
                return value;
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    String unwrapped = decodeString();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                // fall through
            default:
                throw unexpectedToken(TokenType.STRING);
        }
    }

    /**
     * Decode the current {@link TokenType#STRING} value. Called for no other token type.
     * @return The String value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract String getString() throws IOException;

    /**
     * Decode the current {@link TokenType#BOOLEAN} value. Called for no other token type.
     * @return The boolean value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract boolean getBoolean() throws IOException;

    @Override
    public final boolean decodeBoolean() throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        switch (currentToken) {
            case BOOLEAN:
                boolean bool = getBoolean();
                nextToken();
                return bool;
            case NUMBER:
                double number = getDouble();
                nextToken();
                return number != 0;
            case STRING:
                String string = coerceScalarToString(currentToken);
                nextToken();
                return string.equals("true");
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    boolean unwrapped = decodeBoolean();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    }
                }
                // fall through
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
        return decodeInteger(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public final long decodeLong() throws IOException {
        return decodeLong(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private long decodeLong(long min, long max) throws IOException {
        return decodeLong(min, max, false);
    }

    private int decodeInteger(long min, long max) throws IOException {
        return decodeInteger(min, max, false);
    }

    /**
     * Decode the current {@link TokenType#NUMBER} value as a long value. Called for no other token type.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract long getLong() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a long value. Called for no other token type.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected int getInteger() throws IOException {
        return (int) getLong();
    }

    /**
     * Decode the current {@link TokenType#NUMBER} value as a double value. Called for no other token type.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract double getDouble() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a {@link BigInteger} value. Called for no other token type.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract BigInteger getBigInteger() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a {@link BigDecimal} value. Called for no other token type.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract BigDecimal getBigDecimal() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a {@link Number} value. Called for no other token type.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract Number getBestNumber() throws IOException;

    /**
     * Decode the current {@link TokenType#NUMBER} value as a numeric {@link JsonNode}. Called for no other token type.
     * Default implementation tries to construct a node from {@link #getBestNumber()}.
     * @return The number value
     * @throws java.io.IOException if an unrecoverable error occurs
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

    private int decodeInteger(long min, long max, boolean stringsAsChars) throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        switch (currentToken) {
            case STRING:
                String string = coerceScalarToString(currentToken);
                if (stringsAsChars) {
                    if (string.length() != 1) {
                        throw createDeserializationException("When decoding char value, must give a single character", string);
                    }
                    char c = string.charAt(0);
                    nextToken();
                    return c;
                } else {
                    int value;
                    try {
                        value = Integer.parseInt(string);
                    } catch (NumberFormatException e) {
                        throw createDeserializationException("Unable to coerce string to integer", string);
                    }
                    nextToken();
                    return value;
                }
            case NUMBER:
                // todo: better coercion rules
                int number = getInteger();
                nextToken();
                return number;
            case BOOLEAN:
                boolean bool = getBoolean();
                nextToken();
                return bool ? 1 : 0;
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    int unwrapped = decodeInteger(min, max);
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one integer, but got array of multiple values", null);
                    }
                }
                // fall through
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
    }

    private long decodeLong(long min, long max, boolean stringsAsChars) throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        switch (currentToken) {
            case STRING:
                String string = coerceScalarToString(currentToken);
                if (stringsAsChars) {
                    if (string.length() != 1) {
                        throw createDeserializationException("When decoding char value, must give a single character", string);
                    }
                    char c = string.charAt(0);
                    nextToken();
                    return c;
                } else {
                    long value;
                    try {
                        value = Long.parseLong(string);
                    } catch (NumberFormatException e) {
                        throw createDeserializationException("Unable to coerce string to integer", string);
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
                if (beginUnwrapArray(currentToken)) {
                    long unwrapped = decodeLong(min, max);
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one integer, but got array of multiple values", null);
                    }
                }
                // fall through
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
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        switch (currentToken) {
            case NUMBER:
                double value = getDouble();
                nextToken();
                return value;
            case STRING:
                String string = coerceScalarToString(currentToken);
                double number;
                try {
                    number = Double.parseDouble(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to double",  string);
                }
                nextToken();
                return number;
            case BOOLEAN:
                boolean bool = getBoolean();
                nextToken();
                return bool ? 1 : 0;
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    double unwrapped = decodeDouble();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one float, but got array of multiple values", null);
                    }
                }
                // fall through
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
    }

    @NonNull
    @Override
    public final BigInteger decodeBigInteger() throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        BigInteger value;
        switch (currentToken) {
            case NUMBER:
                value = getBigInteger();
                break;
            case STRING:
                try {
                    value = new BigInteger(coerceScalarToString(currentToken));
                } catch (NumberFormatException e) {
                    // match behavior of getValueAsDouble
                    value = BigInteger.ZERO;
                }
                break;
            case BOOLEAN:
                value = getBoolean() ? BigInteger.ONE : BigInteger.ZERO;
                break;
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    BigInteger unwrapped = decodeBigInteger();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one float, but got array of multiple values", null);
                    }
                }
                // fall through
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
        nextToken();
        return value;
    }

    @NonNull
    @Override
    public final BigDecimal decodeBigDecimal() throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        BigDecimal value;
        switch (currentToken) {
            case NUMBER:
                value = getBigDecimal();
                break;
            case STRING:
                try {
                    value = new BigDecimal(coerceScalarToString(currentToken));
                } catch (NumberFormatException e) {
                    // match behavior of getValueAsDouble
                    value = BigDecimal.ZERO;
                }
                break;
            case BOOLEAN:
                value = getBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
                break;
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    BigDecimal unwrapped = decodeBigDecimal();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one float, but got array of multiple values", null);
                    }
                }
                // fall through
            default:
                throw unexpectedToken(TokenType.NUMBER);
        }
        nextToken();
        return value;
    }

    /**
     * Decode a number type, applying all necessary coercions.
     *
     * @param currentToken   The current token
     * @param fromNumberToken Called if {@link #currentToken()} is a {@link TokenType#NUMBER}.
     * @param fromString      Called for the textual value if {@link #currentToken()} is a {@link TokenType#STRING}. Should throw {@link NumberFormatException} on parse failure.
     * @param zero            The zero value.
     * @param one             The one value.
     * @param <T>             The number type.
     * @return The parsed number.
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected final <T> T decodeNumber(TokenType currentToken,
                                       ValueDecoder<T> fromNumberToken,
                                       Function<String, T> fromString,
                                       T zero, T one) throws IOException {
        preDecodeValue(currentToken);
        T value;
        switch (currentToken) {
            case NUMBER:
                value = fromNumberToken.decode(this);
                break;
            case STRING:
                try {
                    value = fromString.apply(coerceScalarToString(currentToken));
                } catch (NumberFormatException e) {
                    // match behavior of getValueAsDouble
                    value = zero;
                }
                break;
            case BOOLEAN:
                value = getBoolean() ? one : zero;
                break;
            case START_ARRAY:
                if (beginUnwrapArray(currentToken)) {
                    T unwrapped = decodeNumber(currentToken, fromNumberToken, fromString, zero, one);
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one float, but got array of multiple values", null);
                    }
                }
                // fall through
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
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected final <T> T decodeCustom(ValueDecoder<T> readFunction) throws IOException {
        return decodeCustom(readFunction, true);
    }

    /**
     * Decode a custom type.
     *
     * @param readFunction Function to call for reading the value. The {@link AbstractStreamDecoder} parameter to the function will just be {@code this}, but this allows subclasses to avoid capturing {@code this} to avoid an allocation.
     * @param callNext     Pass "true" if next token should be read after invocation
     * @param <T> Value type
     * @return The parsed value.
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected final <T> T decodeCustom(ValueDecoder<T> readFunction, boolean callNext) throws IOException {
        preDecodeValue(currentToken());
        T value = readFunction.decode(this);
        if (callNext) {
            nextToken();
        }
        return value;
    }

    @Override
    public final boolean decodeNull() throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
        if (currentToken == TokenType.NULL) {
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

    @NonNull
    @Override
    public JsonNode decodeNode() throws IOException {
        TokenType currentToken = currentToken();
        switch (currentToken) {
            case START_OBJECT:
                return decodeObjectNode((AbstractStreamDecoder) decodeObject());
            case START_ARRAY:
                return decodeArrayNode((AbstractStreamDecoder) decodeArray());
            case STRING:
                return JsonNode.createStringNode(decodeString());
            case NUMBER:
                preDecodeValue(currentToken);
                JsonNode bestNumberNode = getBestNumberNode();
                nextToken();
                return bestNumberNode;
            case BOOLEAN:
                return JsonNode.createBooleanNode(decodeBoolean());
            case NULL:
                decodeNull();
                return JsonNode.nullNode();
            default:
                throw createDeserializationException("Unexpected token " + currentToken + ", expected value", null);
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
        // iterative approach to avoid stack overflows
        RootBuilder root = new RootBuilder(this);
        ArbitraryBuilder currentStructure = root;
        while (currentStructure != null) {
            currentStructure = currentStructure.proceed();
        }
        return root.result;
    }

    /**
     * If we are at a {@link TokenType#START_OBJECT} or {@link TokenType#START_ARRAY}, skip to the matching
     * {@link TokenType#END_OBJECT} or {@link TokenType#END_ARRAY}. Else, do nothing.
     *
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected abstract void skipChildren() throws IOException;

    @Override
    public final void skipValue() throws IOException {
        TokenType currentToken = currentToken();
        preDecodeValue(currentToken);
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
         * @throws java.io.IOException if an unrecoverable error occurs
         */
        R decode(AbstractStreamDecoder target) throws IOException;
    }

    private sealed abstract static class ArbitraryBuilder {
        final ArbitraryBuilder parent;
        final AbstractStreamDecoder elementDecoder;

        ArbitraryBuilder(ArbitraryBuilder parent, AbstractStreamDecoder elementDecoder) {
            this.parent = parent;
            this.elementDecoder = elementDecoder;
        }

        // this is basically MapBuilder API, we emulate it with mock keys for RootBuilder and ListBuilder

        // also calls finishStructure
        abstract String decodeKey() throws IOException;

        abstract void put(String key, Object value);

        /**
         * Consume some input. Returns the decoder responsible for further processing: Either this decoder, a new child
         * decoder, or the parent of this decoder (possibly null).
         */
        ArbitraryBuilder proceed() throws IOException {
            String key = decodeKey();
            if (key != null) {
                //noinspection ConstantConditions
                TokenType currentToken = elementDecoder.currentToken();
                switch (currentToken) {
                    case START_OBJECT:
                        MapBuilder map = new MapBuilder(this, elementDecoder.decodeObject0(currentToken));
                        put(key, map.items);
                        return map;
                    case START_ARRAY:
                        ListBuilder list = new ListBuilder(this, elementDecoder.decodeArray0(currentToken));
                        put(key, list.items);
                        return list;
                    case STRING:
                        put(key, elementDecoder.decodeString());
                        return this;
                    case NUMBER:
                        elementDecoder.preDecodeValue(currentToken);
                        put(key, elementDecoder.getBestNumber());
                        elementDecoder.nextToken();
                        return this;
                    case BOOLEAN:
                        put(key, elementDecoder.decodeBoolean());
                        return this;
                    case NULL:
                        elementDecoder.decodeNull();
                        put(key, null);
                        return this;
                    default:
                        throw elementDecoder.createDeserializationException("Unexpected token " + currentToken + ", expected value", null);
                }
            } else {
                return parent;
            }
        }
    }

    private static final class RootBuilder extends ArbitraryBuilder {
        boolean done = false;
        Object result;

        RootBuilder(AbstractStreamDecoder decoder) {
            super(null, decoder);
        }

        @Override
        void put(String key, Object value) {
            result = value;
            done = true;
        }

        @Override
        String decodeKey() {
            return !done ? "" : null;
        }
    }

    private static final class ListBuilder extends ArbitraryBuilder {
        final List<Object> items = new ArrayList<>();

        ListBuilder(ArbitraryBuilder parent, AbstractStreamDecoder decoder) {
            super(parent, decoder);
        }

        @Override
        void put(String key, Object value) {
            items.add(value);
        }

        @Override
        String decodeKey() throws IOException {
            if (elementDecoder.hasNextArrayValue()) {
                return "";
            } else {
                elementDecoder.finishStructure();
                return null;
            }
        }
    }

    private static final class MapBuilder extends ArbitraryBuilder {
        final Map<String, Object> items = new LinkedHashMap<>();

        MapBuilder(ArbitraryBuilder parent, AbstractStreamDecoder elementDecoder) {
            super(parent, elementDecoder);
        }

        @Override
        void put(String key, Object value) {
            items.put(key, value);
        }

        @Override
        String decodeKey() throws IOException {
            String key = elementDecoder.decodeKey();
            if (key == null) {
                elementDecoder.finishStructure();
            }
            return key;
        }
    }
}
