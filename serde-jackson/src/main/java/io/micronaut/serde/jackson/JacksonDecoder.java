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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link Decoder} interface for Jackson.
 *
 * @author Denis Stepanov
 */
@Internal
public final class JacksonDecoder implements Decoder {
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

    @Internal
    private final JsonParser parser;

    @Nullable
    private JsonToken peekedToken;
    private boolean currentlyUnwrappingArray;
    private int depth = 0;

    private JacksonDecoder(JsonParser parser) throws IOException {
        this.parser = parser;
        if (!parser.hasCurrentToken()) {
            peekedToken = parser.nextToken();
            if (!parser.hasCurrentToken()) {
                throw new EOFException("No JSON input to parse");
            }
        } else {
            peekedToken = parser.currentToken();
        }
    }

    public static Decoder create(JsonParser parser) throws IOException {
        return new JacksonDecoder(parser);
    }

    @Override
    public IOException createDeserializationException(String message, Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message + " \n at " + parser.getCurrentLocation(), null, invalidValue);
        } else {
            return new SerdeException(message + " \n at " + parser.getCurrentLocation());
        }
    }

    /**
     * @param expected The token type that was expected in place of {@link JsonParser#currentToken()}.
     * @return The exception that should be thrown to signify an unexpected token.
     */
    private IOException unexpectedToken(JsonToken expected, JsonToken actual) {
        return createDeserializationException("Unexpected token " + actual + ", expected " + expected, null);
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
        depth--;
    }

    @Override
    public void finishStructure() throws IOException {
        JsonToken token = nextToken();
        if (token != JsonToken.END_ARRAY && token != JsonToken.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
        depth--;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return peekToken() != JsonToken.END_ARRAY;
    }

    @Nullable
    @Override
    public String decodeKey() throws IOException {
        if (peekedToken != null) {
            String fieldName = parser.currentName();
            if (fieldName != null) {
                peekedToken = null;
            }
            return fieldName;
        } else {
            String fieldName = parser.nextFieldName();
            if (fieldName == null) {
                peekedToken = parser.currentToken();
            }
            return fieldName;
        }
    }

    @NonNull
    @Override
    public JacksonDecoder decodeArray(Argument<?> type) throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_ARRAY) {
            throw unexpectedToken(JsonToken.START_ARRAY, t);
        }
        depth++;
        return this;
    }

    @Override
    public JacksonDecoder decodeArray() throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_ARRAY) {
            throw unexpectedToken(JsonToken.START_ARRAY, t);
        }
        depth++;
        return this;
    }

    @NonNull
    @Override
    public JacksonDecoder decodeObject(Argument<?> type) throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_OBJECT) {
            throw unexpectedToken(JsonToken.START_OBJECT, t);
        }
        depth++;
        return this;
    }

    @Override
    public JacksonDecoder decodeObject() throws IOException {
        JsonToken t = nextToken();
        if (t != JsonToken.START_OBJECT) {
            throw unexpectedToken(JsonToken.START_OBJECT, t);
        }
        depth++;
        return this;
    }

    @NonNull
    @Override
    public String decodeString() throws IOException {
        String s = decodeStringNullable();
        if (s == null) {
            throw unexpectedToken(JsonToken.VALUE_STRING, parser.currentToken());
        }
        return s;
    }

    @Nullable
    @Override
    public String decodeStringNullable() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            // fast path: avoid nextToken
            String value = parser.nextTextValue();
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
        if (t == JsonToken.START_ARRAY) {
            if (beginUnwrapArray(t)) {
                String unwrapped = decodeString();
                if (endUnwrapArray()) {
                    return unwrapped;
                } else {
                    throw createDeserializationException("Expected one string, but got array of multiple values", null);
                }
            }
            throw unexpectedToken(JsonToken.VALUE_STRING, t);
        } else {
            return parser.getValueAsString();
        }
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        Boolean v = decodeBooleanNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_TRUE, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Boolean decodeBooleanNullable() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            // fast path: avoid nextToken
            Boolean value = parser.nextBooleanValue();
            if (value != null) {
                return value;
            }
            t = parser.currentToken();
        } else {
            t = nextToken();
        }
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_TRUE, t);
            default -> {
                return parser.getValueAsBoolean();
            }
        }
    }

    @Override
    public byte decodeByte() throws IOException {
        Byte v = decodeByteNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Byte decodeByteNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getByteValue();
            }
        }
    }

    @Override
    public short decodeShort() throws IOException {
        Short v = decodeShortNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Short decodeShortNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getShortValue();
            }
        }
    }

    @Override
    public char decodeChar() throws IOException {
        Character v = decodeCharNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Character decodeCharNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                String text = parser.getText();
                if (text.length() == 0) {
                    throw createDeserializationException("No characters found", text);
                }
                return text.charAt(0);
            }
        }
    }

    @Override
    public int decodeInt() throws IOException {
        Integer v = decodeIntNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Integer decodeIntNullable() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            // fast path: avoid nextToken
            int value = parser.nextIntValue(INT_CANARY);
            if (value != INT_CANARY) {
                return value;
            }
            t = parser.currentToken();
        } else {
            t = nextToken();
        }
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getValueAsInt();
            }
        }
    }

    @Override
    public long decodeLong() throws IOException {
        Long v = decodeLongNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Long decodeLongNullable() throws IOException {
        JsonToken t;
        if (peekedToken == null) {
            long value = parser.nextLongValue(LONG_CANARY);
            if (value != LONG_CANARY) {
                return value;
            }
            t = parser.currentToken();
        } else {
            t = nextToken();
        }
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getValueAsLong();
            }
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        Float v = decodeFloatNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Float decodeFloatNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> {
                return parser.getFloatValue();
            }
        }
    }

    @Override
    public double decodeDouble() throws IOException {
        Double v = decodeDoubleNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public Double decodeDoubleNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> {
                return parser.getValueAsDouble();
            }
        }
    }

    @NonNull
    @Override
    public BigInteger decodeBigInteger() throws IOException {
        BigInteger v = decodeBigIntegerNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public BigInteger decodeBigIntegerNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_INT, t);
            default -> {
                return parser.getBigIntegerValue();
            }
        }
    }

    @NonNull
    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        BigDecimal v = decodeBigDecimalNullable();
        if (v == null) {
            throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, parser.currentToken());
        }
        return v;
    }

    @Nullable
    @Override
    public BigDecimal decodeBigDecimalNullable() throws IOException {
        JsonToken t = nextToken();
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
            case START_OBJECT, END_OBJECT, END_ARRAY, FIELD_NAME -> throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT, t);
            default -> {
                return parser.getDecimalValue();
            }
        }
    }

    private Number decodeNumber() throws IOException {
        nextToken();
        return parser.getNumberValue();
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

    @Override
    public Decoder decodeBuffer() throws IOException {
        JsonNode node = decodeNode();
        return JsonNodeDecoder.create(node);
    }

    @NonNull
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

    private abstract static class ArbitraryBuilder {
        final ArbitraryBuilder parent;
        final JacksonDecoder elementDecoder;

        ArbitraryBuilder(ArbitraryBuilder parent, JacksonDecoder elementDecoder) {
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
                        put(key, elementDecoder.decodeNumber());
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
        Object result;

        RootBuilder(JacksonDecoder decoder) {
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
        private final List<Object> items = new ArrayList<>();

        ListBuilder(ArbitraryBuilder parent, JacksonDecoder decoder) {
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
        private final Map<String, Object> items = new LinkedHashMap<>();

        MapBuilder(ArbitraryBuilder parent, JacksonDecoder elementDecoder) {
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
