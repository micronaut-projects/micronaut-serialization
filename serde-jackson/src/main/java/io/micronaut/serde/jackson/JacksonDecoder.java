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
package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.KeysSupport;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.config.CoercionPolicy.Coercion;
import io.micronaut.serde.config.CoercionPolicy.Shape;
import io.micronaut.serde.config.CoercionPolicy.Target;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.NullValueSerdeException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.util.BinaryCodecUtil;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.sym.PropertyNameMatcher;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link Decoder} interface for Jackson.
 *
 * @author Denis Stepanov
 */
@Internal
public final class JacksonDecoder extends LimitingStream implements KeysAwareDecoder {
    /**
     * Default value for {@link JsonParser#nextIntValue(int)}. If this value is encountered, we
     * enter the slow parse path.
     */
    private static final int INT_CANARY = 0xff123456;
    /**
     * Default value for {@link JsonParser#nextLongValue(long)} (int)}. If this value is
     * encountered, we enter the slow parse path.
     */
    private static final long LONG_CANARY = 0xff1234567890abcdL;
    /**
     * The {@link Shape} of every {@link JsonToken}, as a bit, so that a coercion check is a single
     * mask test against the shapes the policy precalculated.
     */
    private static final int[] TOKEN_SHAPE_BITS = tokenShapeBits();
    private static final int JACKSON_KEYS_INDEX = KeysSupport.indexOf(new JacksonKeysProvider());
    private static final Object[] EMPTY_JACKSON_KEYS = new JacksonKeysProvider().create(List.of(), false);
    private static final SerializableString[] EMPTY_SERIALIZABLE_KEYS =
        (SerializableString[]) EMPTY_JACKSON_KEYS[JacksonKeysProvider.SERIALIZABLE_KEYS_INDEX];
    private static final PropertyNameMatcher EMPTY_PROPERTY_NAME_MATCHER =
        (PropertyNameMatcher) EMPTY_JACKSON_KEYS[JacksonKeysProvider.PROPERTY_NAME_MATCHER_INDEX];

    @Internal
    private final JsonParser parser;
    private final CoercionPolicy coercionPolicy;
    private final int integerShapes;
    private final int decimalShapes;
    private final int booleanShapes;
    private final int stringShapes;
    private final int charShapes;

    @Nullable
    private JsonToken peekedToken;
    @Nullable
    private Keys currentKeys;
    private SerializableString[] currentSerializableKeys = EMPTY_SERIALIZABLE_KEYS;
    private PropertyNameMatcher currentPropertyNameMatcher = EMPTY_PROPERTY_NAME_MATCHER;
    private boolean sequentialKeyMatching;
    private int sequentialKeyIndex;
    private boolean currentlyUnwrappingArray;

    private JacksonDecoder(JsonParser parser, RemainingLimits remainingLimits, CoercionPolicy coercionPolicy) throws IOException {
        super(remainingLimits);
        this.parser = parser;
        this.coercionPolicy = coercionPolicy;
        this.integerShapes = coercionPolicy.allowedShapes(Target.INTEGER);
        this.decimalShapes = coercionPolicy.allowedShapes(Target.DECIMAL);
        this.booleanShapes = coercionPolicy.allowedShapes(Target.BOOLEAN);
        this.stringShapes = coercionPolicy.allowedShapes(Target.STRING);
        this.charShapes = coercionPolicy.allowedShapes(Target.CHAR);
        if (!parser.hasCurrentToken()) {
            peekedToken = parser.nextToken();
            if (!parser.hasCurrentToken()) {
                throw new EOFException("No JSON input to parse");
            }
        } else {
            peekedToken = parser.currentToken();
        }
    }

    public static Decoder create(JsonParser parser, RemainingLimits remainingLimits) throws IOException {
        return new JacksonDecoder(parser, remainingLimits, CoercionPolicy.LENIENT);
    }

    /**
     * Create a decoder that only performs the coercions the given policy allows.
     *
     * @param parser          The Jackson parser
     * @param remainingLimits The remaining stream limits
     * @param coercionPolicy  The coercions this decoder may perform
     * @return The decoder
     * @throws IOException If the parser cannot be initialized
     */
    public static Decoder create(JsonParser parser, RemainingLimits remainingLimits, CoercionPolicy coercionPolicy) throws IOException {
        return new JacksonDecoder(parser, remainingLimits, coercionPolicy);
    }

    @Override
    public CoercionPolicy getCoercionPolicy() {
        return coercionPolicy;
    }

    @Override
    public int decodeKey(Keys keys) {
        JsonToken token = peekedToken;
        if (token != null) {
            if (token == JsonToken.END_OBJECT) {
                return MATCH_END_OBJECT;
            }
            if (token == JsonToken.PROPERTY_NAME) {
                int keyIndex = matchCurrentKey(keys);
                if (keyIndex >= 0) {
                    peekedToken = null;
                    return keyIndex;
                }
                // MATCH_UNKNOWN_NAME is only used after the matcher rejects this
                // property name for the supplied Keys.
                return MATCH_UNKNOWN_NAME;
            }
            return MATCH_UNKNOWN_NAME;
        }
        int keyIndex = nextKeyIndex(keys);
        if (keyIndex >= 0) {
            return keyIndex;
        }
        peekedToken = parser.currentToken();
        if (keyIndex == PropertyNameMatcher.MATCH_END_OBJECT) {
            return MATCH_END_OBJECT;
        }
        // The parser matched neither the supplied Keys nor the end marker.
        // The caller can read this unknown name with decodeKey().
        return MATCH_UNKNOWN_NAME;
    }

    private int nextKeyIndex(Keys keys) {
        jacksonKeys(keys);
        if (sequentialKeyMatching) {
            SerializableString[] serializableKeys = currentSerializableKeys;
            int keyIndex = sequentialKeyIndex;
            if (keyIndex < serializableKeys.length) {
                if (parser.nextName(serializableKeys[keyIndex])) {
                    sequentialKeyIndex = keyIndex + 1;
                    return keyIndex;
                }
                sequentialKeyMatching = false;
                JsonToken token = parser.currentToken();
                if (token == JsonToken.END_OBJECT) {
                    return PropertyNameMatcher.MATCH_END_OBJECT;
                }
                if (token == JsonToken.PROPERTY_NAME) {
                    return parser.currentNameMatch(currentPropertyNameMatcher);
                }
                return PropertyNameMatcher.MATCH_ODD_TOKEN;
            }
            sequentialKeyMatching = false;
        }
        return parser.nextNameMatch(currentPropertyNameMatcher);
    }

    private int matchCurrentKey(Keys keys) {
        jacksonKeys(keys);
        if (sequentialKeyMatching) {
            SerializableString[] serializableKeys = currentSerializableKeys;
            int keyIndex = sequentialKeyIndex;
            if (keyIndex < serializableKeys.length && serializableKeys[keyIndex].getValue().equals(parser.currentName())) {
                sequentialKeyIndex = keyIndex + 1;
                return keyIndex;
            }
            sequentialKeyMatching = false;
        }
        return parser.currentNameMatch(currentPropertyNameMatcher);
    }

    private void jacksonKeys(Keys keys) {
        if (keys != currentKeys) {
            Object[] jacksonKeys = KeysSupport.get(keys, JACKSON_KEYS_INDEX);
            currentKeys = keys;
            currentSerializableKeys = (SerializableString[]) jacksonKeys[JacksonKeysProvider.SERIALIZABLE_KEYS_INDEX];
            currentPropertyNameMatcher = (PropertyNameMatcher) jacksonKeys[JacksonKeysProvider.PROPERTY_NAME_MATCHER_INDEX];
            sequentialKeyMatching = !keys.caseInsensitive();
            sequentialKeyIndex = 0;
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message + " \n at " + parser.currentLocation(), null, invalidValue);
        } else {
            return new SerdeException(message + " \n at " + parser.currentLocation());
        }
    }

    /**
     * @param expected The token type that was expected in place of {@link JsonParser#currentToken()}.
     * @return The exception that should be thrown to signify an unexpected token.
     */
    private IOException unexpectedToken(JsonToken expected, JsonToken actual) {
        return createDeserializationException("Unexpected token " + actual + ", expected " + expected, null);
    }

    private NullValueSerdeException unexpectedNullToken(JsonToken expected, JsonToken actual) {
        return NullValueSerdeException.unexpectedToken(expected, actual);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.END_ARRAY && t != JsonToken.END_OBJECT) {
            if (!consumeLeftElements) {
                throw new IllegalStateException("Not all elements have been consumed yet");
            }
            do {
                t = nextToken();
                if (t == JsonToken.START_ARRAY || t == JsonToken.START_OBJECT) {
                    parser.skipChildren();
                }
            } while (t != JsonToken.END_OBJECT && t != JsonToken.END_ARRAY && t != null);
        }
        decreaseDepth();
    }

    @Override
    public void finishStructure() throws IOException {
        JsonToken token = nextToken();
        if (token != JsonToken.END_ARRAY && token != JsonToken.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
        decreaseDepth();
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return peekToken() != JsonToken.END_ARRAY;
    }

    @Nullable
    @Override
    public String decodeKey() throws IOException {
        if (peekedToken != null) {
            if (peekedToken == JsonToken.END_OBJECT) {
                return null;
            }
            String fieldName = parser.currentName();
            if (fieldName != null) {
                peekedToken = null;
            }
            return fieldName;
        } else {
            String fieldName = parser.nextName();
            if (fieldName == null) {
                peekedToken = parser.currentToken();
            }
            return fieldName;
        }
    }

    @Override
    public JacksonDecoder decodeArray(Argument<?> type) throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_ARRAY) {
            throw unexpectedToken(JsonToken.START_ARRAY, t);
        }
        increaseDepth();
        return this;
    }

    @Override
    public JacksonDecoder decodeArray() throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_ARRAY) {
            throw unexpectedToken(JsonToken.START_ARRAY, t);
        }
        increaseDepth();
        return this;
    }

    @Override
    public JacksonDecoder decodeObject(Argument<?> type) throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_OBJECT) {
            throw unexpectedToken(JsonToken.START_OBJECT, t);
        }
        increaseDepth();
        resetSequentialKeyMatching();
        return this;
    }

    @Override
    public JacksonDecoder decodeObject() throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_OBJECT) {
            throw unexpectedToken(JsonToken.START_OBJECT, t);
        }
        increaseDepth();
        resetSequentialKeyMatching();
        return this;
    }

    private void resetSequentialKeyMatching() {
        sequentialKeyIndex = 0;
        if (currentKeys != null) {
            sequentialKeyMatching = !currentKeys.caseInsensitive();
        }
    }

    @Override
    public String decodeString() throws IOException {
        String s = decodeStringNullable();
        if (s == null) {
            throw unexpectedNullToken(JsonToken.VALUE_STRING, parser.currentToken());
        }
        return s;
    }

    @Nullable
    @Override
    public String decodeStringNullable() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            // fast path: avoid nextToken
            String value = parser.nextStringValue();
            if (value != null) {
                return value;
            }
            t = parser.currentToken();
        } else {
            t = nextToken();
            if (t == JsonToken.VALUE_STRING) {
                return parser.getText();
            }
        }
        checkStringSource(t);
        switch (t) {
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    String unwrapped = decodeString();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_STRING, t);
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_STRING, t);
            default -> {
                return parser.getValueAsString();
            }
        }
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        if (peekedToken == null) {
            Boolean value = parser.nextBooleanValue();
            if (value != null) {
                return value;
            }
        }
        return decodeBooleanValue(peekedToken == null ? parser.currentToken() : nextToken());
    }

    @Nullable
    @Override
    public Boolean decodeBooleanNullable() throws IOException {
        if (peekedToken == null) {
            // fast path: avoid nextToken
            Boolean value = parser.nextBooleanValue();
            if (value != null) {
                return value;
            }
        }
        return decodeBooleanSlow();
    }

    @Nullable
    private Boolean decodeBooleanSlow() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            t = parser.currentToken();
        } else {
            t = nextToken();
        }
        checkBooleanSource(t);
        switch (t) {
            case VALUE_TRUE -> {
                return true;
            }
            case VALUE_FALSE -> {
                return false;
            }
            case VALUE_NUMBER_FLOAT -> {
                return parser.getFloatValue() != 0.0;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    boolean unwrapped = decodeBoolean();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_TRUE, t);
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_TRUE, t);
            default -> {
                return parser.getValueAsBoolean();
            }
        }
    }

    private boolean decodeBooleanValue(JsonToken t) throws IOException {
        checkBooleanSource(t);
        return switch (t) {
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NUMBER_FLOAT -> parser.getFloatValue() != 0.0;
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    boolean unwrapped = decodeBoolean();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_TRUE, t);
            }
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_TRUE, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_TRUE, t);
            default -> parser.getValueAsBoolean();
        };
    }

    @Override
    public byte decodeByte() throws IOException {
        return decodeByteValue(nextToken());
    }

    @Nullable
    @Override
    public Byte decodeByteNullable() throws IOException {
        JsonToken t = nextToken();
        checkIntegerSource(t);
        switch (t) {
            case VALUE_TRUE -> {
                return 1;
            }
            case VALUE_FALSE -> {
                return 0;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    byte unwrapped = decodeByte();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getByteValue();
            }
        }
    }

    private byte decodeByteValue(JsonToken t) throws IOException {
        checkIntegerSource(t);
        return switch (t) {
            case VALUE_TRUE -> 1;
            case VALUE_FALSE -> 0;
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    byte unwrapped = decodeByte();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_INT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                yield parser.getByteValue();
            }
        };
    }

    @Override
    public short decodeShort() throws IOException {
        return decodeShortValue(nextToken());
    }

    @Nullable
    @Override
    public Short decodeShortNullable() throws IOException {
        JsonToken t = nextToken();
        checkIntegerSource(t);
        switch (t) {
            case VALUE_TRUE -> {
                return 1;
            }
            case VALUE_FALSE -> {
                return 0;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    short unwrapped = decodeShort();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getShortValue();
            }
        }
    }

    private short decodeShortValue(JsonToken t) throws IOException {
        checkIntegerSource(t);
        return switch (t) {
            case VALUE_TRUE -> 1;
            case VALUE_FALSE -> 0;
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    short unwrapped = decodeShort();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_INT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                yield parser.getShortValue();
            }
        };
    }

    @Override
    public char decodeChar() throws IOException {
        return decodeCharValue(nextToken());
    }

    @Nullable
    @Override
    public Character decodeCharNullable() throws IOException {
        JsonToken t = nextToken();
        checkCharSource(t);
        switch (t) {
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    char unwrapped = decodeChar();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                if (string.length() != 1) {
                    throw createDeserializationException("When decoding char value, must give a single character", string);
                }
                return string.charAt(0);
            }
            case VALUE_NUMBER_INT -> {
                return (char) parser.getIntValue();
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                String text = parser.getText();
                if (text.length() == 0) {
                    throw createDeserializationException("No characters found", text);
                }
                return text.charAt(0);
            }
        }
    }

    private char decodeCharValue(JsonToken t) throws IOException {
        checkCharSource(t);
        return switch (t) {
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    char unwrapped = decodeChar();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                if (string.length() != 1) {
                    throw createDeserializationException("When decoding char value, must give a single character", string);
                }
                yield string.charAt(0);
            }
            case VALUE_NUMBER_INT -> (char) parser.getIntValue();
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_INT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                String text = parser.getText();
                if (text.length() == 0) {
                    throw createDeserializationException("No characters found", text);
                }
                yield text.charAt(0);
            }
        };
    }

    @Override
    public int decodeInt() throws IOException {
        if (peekedToken == null) {
            int value = parser.nextIntValue(INT_CANARY);
            if (value != INT_CANARY) {
                return value;
            }
        }
        return decodeIntValue(peekedToken == null ? parser.currentToken() : nextToken());
    }

    @Nullable
    @Override
    public Integer decodeIntNullable() throws IOException {
        if (peekedToken == null) {
            // fast path: avoid nextToken
            int value = parser.nextIntValue(INT_CANARY);
            if (value != INT_CANARY) {
                return value;
            }
        }
        return decodeIntSlow();
    }

    @Nullable
    private Integer decodeIntSlow() throws IOException {
        JsonToken t = peekedToken == null ? parser.currentToken() : nextToken();
        checkIntegerSource(t);
        switch (t) {
            case VALUE_NUMBER_INT -> {
                return parser.getIntValue();
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    int unwrapped = decodeInt();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_FALSE -> {
                return 0;
            }
            case VALUE_TRUE -> {
                return 1;
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getValueAsInt();
            }
        }
    }

    private int decodeIntValue(JsonToken t) throws IOException {
        checkIntegerSource(t);
        return switch (t) {
            case VALUE_NUMBER_INT -> parser.getIntValue();
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    yield Integer.parseInt(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    int unwrapped = decodeInt();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_FALSE -> 0;
            case VALUE_TRUE -> 1;
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_INT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                yield parser.getValueAsInt();
            }
        };
    }

    @Override
    public long decodeLong() throws IOException {
        if (peekedToken == null) {
            long value = parser.nextLongValue(LONG_CANARY);
            if (value != LONG_CANARY) {
                return value;
            }
        }
        return decodeLongValue(peekedToken == null ? parser.currentToken() : nextToken());
    }

    @Nullable
    @Override
    public Long decodeLongNullable() throws IOException {
        if (peekedToken == null) {
            long value = parser.nextLongValue(LONG_CANARY);
            if (value != LONG_CANARY) {
                return value;
            }
        }
        return decodeLongSlow();
    }

    @Nullable
    private Long decodeLongSlow() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            t = parser.currentToken();
        } else {
            t = nextToken();
        }
        checkIntegerSource(t);
        switch (t) {
            case VALUE_NUMBER_INT -> {
                return parser.getLongValue();
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                long value;
                try {
                    value = Long.parseLong(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
                return value;
            }
            case VALUE_FALSE -> {
                return 0L;
            }
            case VALUE_TRUE -> {
                return 1L;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    long unwrapped = decodeLong();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getValueAsLong();
            }
        }
    }

    private long decodeLongValue(JsonToken t) throws IOException {
        checkIntegerSource(t);
        return switch (t) {
            case VALUE_NUMBER_INT -> parser.getLongValue();
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    yield Long.parseLong(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
            }
            case VALUE_FALSE -> 0L;
            case VALUE_TRUE -> 1L;
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    long unwrapped = decodeLong();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_INT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                yield parser.getValueAsLong();
            }
        };
    }

    @Override
    public float decodeFloat() throws IOException {
        return decodeFloatValue(nextToken());
    }

    @Nullable
    @Override
    public Float decodeFloatNullable() throws IOException {
        JsonToken t = nextToken();
        checkDecimalSource(t);
        switch (t) {
            case VALUE_STRING -> {
                String string = parser.getText();
                float value;
                try {
                    value = Float.parseFloat(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to float", string);
                }
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    float unwrapped = decodeFloat();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            }
            case VALUE_FALSE -> {
                return 0F;
            }
            case VALUE_TRUE -> {
                return 1F;
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> {
                return parser.getFloatValue();
            }
        }
    }

    private float decodeFloatValue(JsonToken t) throws IOException {
        checkDecimalSource(t);
        return switch (t) {
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    yield Float.parseFloat(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to float", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    float unwrapped = decodeFloat();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            }
            case VALUE_FALSE -> 0F;
            case VALUE_TRUE -> 1F;
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> parser.getFloatValue();
        };
    }

    @Override
    public double decodeDouble() throws IOException {
        JsonToken t = nextToken();
        if (t == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
        return decodeDoubleValue(t);
    }

    @Nullable
    @Override
    public Double decodeDoubleNullable() throws IOException {
        JsonToken t = nextToken();
        if (t == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
        return decodeDoubleSlow(t);
    }

    @Nullable
    private Double decodeDoubleSlow(JsonToken t) throws IOException {
        checkDecimalSource(t);
        switch (t) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                return parser.getDoubleValue();
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    return Double.parseDouble(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to double", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    double unwrapped = decodeDouble();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_FALSE -> {
                return 0D;
            }
            case VALUE_TRUE -> {
                return 1D;
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> {
                return parser.getValueAsDouble();
            }
        }
    }

    private double decodeDoubleValue(JsonToken t) throws IOException {
        checkDecimalSource(t);
        return switch (t) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getDoubleValue();
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    yield Double.parseDouble(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to double", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    double unwrapped = decodeDouble();
                    if (endUnwrapArray()) {
                        yield unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_FALSE -> 0D;
            case VALUE_TRUE -> 1D;
            case VALUE_NULL -> throw unexpectedNullToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> parser.getValueAsDouble();
        };
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        BigInteger v = decodeBigIntegerNullable();
        if (v == null) {
            throw unexpectedNullToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public BigInteger decodeBigIntegerNullable() throws IOException {
        JsonToken t = nextToken();
        checkIntegerSource(t);
        switch (t) {
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    return new BigInteger(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    BigInteger unwrapped = decodeBigInteger();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            }
            case VALUE_FALSE -> {
                return BigInteger.ZERO;
            }
            case VALUE_TRUE -> {
                return BigInteger.ONE;
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getBigIntegerValue();
            }
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        BigDecimal v = decodeBigDecimalNullable();
        if (v == null) {
            throw unexpectedNullToken(JsonToken.VALUE_NUMBER_FLOAT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public BigDecimal decodeBigDecimalNullable() throws IOException {
        JsonToken t = nextToken();
        checkDecimalSource(t);
        switch (t) {
            case VALUE_STRING -> {
                String string = parser.getText();
                try {
                    return new BigDecimal(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to BigDecimal", string);
                }
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(t)) {
                    BigDecimal unwrapped = decodeBigDecimal();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            }
            case VALUE_FALSE -> {
                return BigDecimal.ZERO;
            }
            case VALUE_TRUE -> {
                return BigDecimal.ONE;
            }
            case VALUE_NULL -> {
                return null;
            }
            case START_OBJECT, END_OBJECT, END_ARRAY, PROPERTY_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> {
                return parser.getDecimalValue();
            }
        }
    }

    private Number doDecodeNumber() throws IOException {
        nextToken();
        return parser.getNumberValue();
    }

    @Override
    public Number decodeNumber() throws IOException {
        return switch (peekToken()) {
            case VALUE_STRING -> decodeBigDecimal();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> doDecodeNumber();
            default -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, nextToken());
        };
    }

    @Override
    public byte[] decodeBinary() throws IOException {
        return switch (peekToken()) {
            case VALUE_STRING -> {
                nextToken();
                yield parser.getBinaryValue();
            }
            case VALUE_EMBEDDED_OBJECT -> decodeEmbeddedBinary();
            case START_ARRAY -> BinaryCodecUtil.decodeFromArray(this);
            default -> throw unexpectedToken(JsonToken.START_ARRAY, nextToken());
        };
    }

    /**
     * Binary formats such as CBOR expose byte strings as embedded objects rather than tokens.
     */
    private byte[] decodeEmbeddedBinary() throws IOException {
        nextToken();
        Object embedded = parser.getEmbeddedObject();
        if (embedded instanceof byte[] bytes) {
            return bytes;
        }
        throw createDeserializationException(
            "Expected embedded byte array, got: " + (embedded == null ? "null" : embedded.getClass().getName()),
            embedded
        );
    }

    @Override
    public boolean decodeNull() throws IOException {
        if (peekToken() == JsonToken.VALUE_NULL) {
            nextToken();
            return true;
        } else {
            // we don't support unwrapping null values from arrays, because the api user wouldn't be able to distinguish
            // `[null]` and `null` anymore.
            return false;
        }
    }

    private boolean beginUnwrapArray(JsonToken currentToken) throws IOException {
        if (currentlyUnwrappingArray) {
            return false;
        }
        if (currentToken != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Not an array");
        }
        currentlyUnwrappingArray = true;
        return true;
    }

    private JsonToken nextToken() throws IOException {
        JsonToken peekedToken = this.peekedToken;
        if (peekedToken == null) {
            return parser.nextToken();
        } else {
            this.peekedToken = null;
            return peekedToken;
        }
    }

    private JsonToken peekToken() throws IOException {
        if (peekedToken == null) {
            peekedToken = parser.nextToken();
        }
        return peekedToken;
    }

    private boolean endUnwrapArray() throws IOException {
        currentlyUnwrappingArray = false;
        if (peekToken() == JsonToken.END_ARRAY) {
            nextToken();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Peeks the next token for integrations that need direct JSON token streaming.
     *
     * @return The next token
     * @throws IOException If an unrecoverable error occurs
     */
    @Internal
    public JsonToken peekTokenForStreaming() throws IOException {
        return peekToken();
    }

    /**
     * Consumes the next token for integrations that need direct JSON token streaming.
     *
     * @return The consumed token
     * @throws IOException If an unrecoverable error occurs
     */
    @Internal
    public JsonToken nextTokenForStreaming() throws IOException {
        JsonToken token = nextToken();
        if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
            increaseDepth();
        } else if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
            decreaseDepth();
        }
        return token;
    }

    /**
     * Returns the underlying parser for current-token scalar access.
     *
     * @return The underlying parser
     */
    @Internal
    public JsonParser parserForStreaming() {
        return parser;
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        JsonNode node = decodeNode();
        return JsonNodeDecoder.create(node, ourLimits(), coercionPolicy);
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        JsonToken t = peekToken();
        return switch (t) {
            case START_OBJECT -> decodeObjectNode(decodeObject());
            case START_ARRAY -> decodeArrayNode(decodeArray());
            case VALUE_STRING -> JsonNode.createStringNode(decodeString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                nextToken();
                yield getBestNumberNode();
            }
            case VALUE_TRUE, VALUE_FALSE -> JsonNode.createBooleanNode(decodeBoolean());
            case VALUE_NULL -> {
                decodeNull();
                yield JsonNode.nullNode();
            }
            // base64 is how JsonNodeEncoder/JsonNodeDecoder represent binary in a tree
            case VALUE_EMBEDDED_OBJECT ->
                JsonNode.createStringNode(Base64.getEncoder().encodeToString(decodeEmbeddedBinary()));
            default ->
                throw createDeserializationException("Unexpected token " + t + ", expected value", null);
        };
    }

    private JsonNode getBestNumberNode() throws IOException {
        Number number = parser.getNumberValue();
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
            return JsonNode.createNumberNode(parser.getDecimalValue());
        }
    }

    private static JsonNode decodeObjectNode(JacksonDecoder elementDecoder) throws IOException {
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

    private static JsonNode decodeArrayNode(JacksonDecoder elementDecoder) throws IOException {
        List<JsonNode> result = new ArrayList<>();
        while (elementDecoder.hasNextArrayValue()) {
            result.add(elementDecoder.decodeNode());
        }
        elementDecoder.finishStructure();
        return JsonNode.createArrayNode(result);
    }

    @Nullable
    @Override
    public Object decodeArbitrary() throws IOException {
        // iterative approach to avoid stack overflows
        RootBuilder root = new RootBuilder(this);
        ArbitraryBuilder currentStructure = root;
        while (currentStructure != null) {
            currentStructure = currentStructure.proceed();
        }
        return root.result;
    }

    @Override
    public void skipValue() throws IOException {
        nextToken();
        parser.skipChildren();
    }

    private static int[] tokenShapeBits() {
        JsonToken[] tokens = JsonToken.values();
        int[] bits = new int[tokens.length];
        for (JsonToken token : tokens) {
            Shape shape = switch (token) {
                case VALUE_STRING -> Shape.STRING;
                case VALUE_NUMBER_INT -> Shape.INTEGER_NUMBER;
                case VALUE_NUMBER_FLOAT -> Shape.FLOAT_NUMBER;
                case VALUE_TRUE, VALUE_FALSE -> Shape.BOOLEAN;
                case START_ARRAY -> Shape.ARRAY;
                default -> Shape.OTHER;
            };
            bits[token.ordinal()] = shape.bit();
        }
        return bits;
    }

    private void checkIntegerSource(JsonToken t) throws IOException {
        if ((integerShapes & TOKEN_SHAPE_BITS[t.ordinal()]) == 0) {
            throw coercionFailure(Target.INTEGER, t);
        }
    }

    private void checkDecimalSource(JsonToken t) throws IOException {
        if ((decimalShapes & TOKEN_SHAPE_BITS[t.ordinal()]) == 0) {
            throw coercionFailure(Target.DECIMAL, t);
        }
    }

    private void checkBooleanSource(JsonToken t) throws IOException {
        if ((booleanShapes & TOKEN_SHAPE_BITS[t.ordinal()]) == 0) {
            throw coercionFailure(Target.BOOLEAN, t);
        }
    }

    private void checkStringSource(JsonToken t) throws IOException {
        if ((stringShapes & TOKEN_SHAPE_BITS[t.ordinal()]) == 0) {
            throw coercionFailure(Target.STRING, t);
        }
    }

    private void checkCharSource(JsonToken t) throws IOException {
        if ((charShapes & TOKEN_SHAPE_BITS[t.ordinal()]) == 0) {
            throw coercionFailure(Target.CHAR, t);
        }
    }

    private IOException coercionFailure(Target target, JsonToken t) throws IOException {
        Shape shape = switch (t) {
            case VALUE_STRING -> Shape.STRING;
            case VALUE_NUMBER_INT -> Shape.INTEGER_NUMBER;
            case VALUE_NUMBER_FLOAT -> Shape.FLOAT_NUMBER;
            case VALUE_TRUE, VALUE_FALSE -> Shape.BOOLEAN;
            case START_ARRAY -> Shape.ARRAY;
            default -> Shape.OTHER;
        };
        Coercion coercion = CoercionPolicy.coercion(target, shape);
        if (coercion == null) {
            // should not happen, OTHER is always allowed
            throw unexpectedToken(JsonToken.VALUE_STRING, t);
        }
        return createDeserializationException(coercion.message(), shape == Shape.ARRAY ? null : parser.getValueAsString());
    }

    private abstract static class ArbitraryBuilder {
        @Nullable
        final ArbitraryBuilder parent;
        final JacksonDecoder elementDecoder;

        ArbitraryBuilder(@Nullable ArbitraryBuilder parent, JacksonDecoder elementDecoder) {
            this.parent = parent;
            this.elementDecoder = elementDecoder;
        }

        // this is basically MapBuilder API, we emulate it with mock keys for RootBuilder and ListBuilder

        // also calls finishStructure
        abstract @Nullable String decodeKey() throws IOException;

        abstract void put(String key, @Nullable Object value);

        /**
         * Consume some input. Returns the decoder responsible for further processing: Either this decoder, a new child
         * decoder, or the parent of this decoder (possibly null).
         */
        @Nullable
        ArbitraryBuilder proceed() throws IOException {
            String key = decodeKey();
            if (key != null) {
                //noinspection ConstantConditions
                JsonToken t = elementDecoder.peekToken();
                switch (t) {
                    case START_OBJECT -> {
                        MapBuilder map = new MapBuilder(this, elementDecoder.decodeObject());
                        put(key, map.items);
                        return map;
                    }
                    case START_ARRAY -> {
                        ListBuilder list = new ListBuilder(this, elementDecoder.decodeArray());
                        put(key, list.items);
                        return list;
                    }
                    case VALUE_STRING -> {
                        put(key, elementDecoder.decodeString());
                        return this;
                    }
                    case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                        put(key, elementDecoder.doDecodeNumber());
                        return this;
                    }
                    case VALUE_TRUE, VALUE_FALSE -> {
                        put(key, elementDecoder.decodeBoolean());
                        return this;
                    }
                    case VALUE_NULL -> {
                        elementDecoder.decodeNull();
                        put(key, null);
                        return this;
                    }
                    case VALUE_EMBEDDED_OBJECT -> {
                        put(key, elementDecoder.decodeEmbeddedBinary());
                        return this;
                    }
                    default ->
                        throw elementDecoder.createDeserializationException("Unexpected token " + t + ", expected value", null);
                }
            } else {
                return parent;
            }
        }
    }

    private static final class RootBuilder extends ArbitraryBuilder {
        boolean done = false;
        @Nullable
        Object result;

        RootBuilder(JacksonDecoder decoder) {
            super(null, decoder);
        }

        @Override
        void put(String key, @Nullable Object value) {
            result = value;
            done = true;
        }

        @Override
        @Nullable
        String decodeKey() {
            return !done ? "" : null;
        }
    }

    private static final class ListBuilder extends ArbitraryBuilder {
        private final List<@Nullable Object> items = new ArrayList<>();

        ListBuilder(ArbitraryBuilder parent, JacksonDecoder decoder) {
            super(parent, decoder);
        }

        @Override
        void put(String key, @Nullable Object value) {
            items.add(value);
        }

        @Override
        @Nullable
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
        private final Map<String, @Nullable Object> items = new LinkedHashMap<>();

        MapBuilder(ArbitraryBuilder parent, JacksonDecoder elementDecoder) {
            super(parent, elementDecoder);
        }

        @Override
        void put(String key, @Nullable Object value) {
            items.put(key, value);
        }

        @Override
        @Nullable
        String decodeKey() throws IOException {
            String key = elementDecoder.decodeKey();
            if (key == null) {
                elementDecoder.finishStructure();
            }
            return key;
        }
    }

}
