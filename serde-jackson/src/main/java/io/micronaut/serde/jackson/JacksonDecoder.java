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

    @Internal
    private final JsonParser parser;

    private JsonToken currentToken;
    private boolean currentlyUnwrappingArray;
    private int depth = 0;

    private JacksonDecoder(JsonParser parser) throws IOException {
        this.parser = parser;
        if (!parser.hasCurrentToken()) {
            currentToken = parser.nextToken();
            if (!parser.hasCurrentToken()) {
                throw new EOFException("No JSON input to parse");
            }
        } else {
            currentToken = parser.currentToken();
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
    private IOException unexpectedToken(JsonToken expected) {
        return createDeserializationException("Unexpected token " + currentToken + ", expected " + expected, null);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (consumeLeftElements) {
            while (currentToken != JsonToken.END_ARRAY && currentToken != JsonToken.END_OBJECT) {
                currentToken = parser.nextToken();
            }
        } else if (currentToken != JsonToken.END_ARRAY && currentToken != JsonToken.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
        if (--depth != 0) {
            nextToken();
        }
    }

    @Override
    public void finishStructure() throws IOException {
        if (currentToken != JsonToken.END_ARRAY && currentToken != JsonToken.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
        if (--depth != 0) {
            nextToken();
        }
    }

    @Override
    public boolean hasNextArrayValue() {
        return currentToken != JsonToken.END_ARRAY;
    }

    @Nullable
    @Override
    public String decodeKey() throws IOException {
        if (currentToken == JsonToken.END_OBJECT) {
            // stay on the end token, will be handled in finishStructure
            return null;
        }
        if (currentToken != JsonToken.FIELD_NAME) {
            throw new IllegalStateException("Not at a field name");
        }
        String fieldName = parser.getCurrentName();
        nextToken();
        return fieldName;
    }

    @NonNull
    @Override
    public JacksonDecoder decodeArray(Argument<?> type) throws IOException {
        if (currentToken != JsonToken.START_ARRAY) {
            throw unexpectedToken(JsonToken.START_ARRAY);
        }
        depth++;
        nextToken();
        return this;
    }

    @Override
    public JacksonDecoder decodeArray() throws IOException {
        if (currentToken != JsonToken.START_ARRAY) {
            throw unexpectedToken(JsonToken.START_ARRAY);
        }
        depth++;
        nextToken();
        return this;
    }

    @NonNull
    @Override
    public JacksonDecoder decodeObject(Argument<?> type) throws IOException {
        if (currentToken != JsonToken.START_OBJECT) {
            throw unexpectedToken(JsonToken.START_OBJECT);
        }
        depth++;
        nextToken();
        return this;
    }

    @Override
    public JacksonDecoder decodeObject() throws IOException {
        if (currentToken != JsonToken.START_OBJECT) {
            throw unexpectedToken(JsonToken.START_OBJECT);
        }
        depth++;
        nextToken();
        return this;
    }

    @NonNull
    @Override
    public String decodeString() throws IOException {
        switch (currentToken) {
            case VALUE_STRING -> {
                String value = parser.getText();
                nextToken();
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    String unwrapped = decodeString();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_STRING);
            }
            default -> {
                String def = parser.getValueAsString();
                nextToken();
                return def;
            }
        }
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        switch (currentToken) {
            case VALUE_TRUE -> {
                nextToken();
                return true;
            }
            case VALUE_FALSE -> {
                nextToken();
                return false;
            }
            case VALUE_NUMBER_FLOAT -> {
                float floatValue = parser.getFloatValue();
                nextToken();
                return floatValue != 0.0;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    boolean unwrapped = decodeBoolean();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_TRUE);
            }
            default -> {
                boolean value = parser.getValueAsBoolean();
                nextToken();
                return value;
            }
        }
    }

    @Override
    public byte decodeByte() throws IOException {
        switch (currentToken) {
            case VALUE_TRUE -> {
                nextToken();
                return 1;
            }
            case VALUE_FALSE -> {
                nextToken();
                return 0;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    byte unwrapped = decodeByte();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            default -> {
                byte value = parser.getByteValue();
                nextToken();
                return value;
            }
        }
    }

    @Override
    public short decodeShort() throws IOException {
        switch (currentToken) {
            case VALUE_TRUE -> {
                nextToken();
                return 1;
            }
            case VALUE_FALSE -> {
                nextToken();
                return 0;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    byte unwrapped = decodeByte();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            default -> {
                short value = parser.getShortValue();
                nextToken();
                return value;
            }
        }
    }

    @Override
    public char decodeChar() throws IOException {
        switch (currentToken) {
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    char unwrapped = decodeChar();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                if (string.length() != 1) {
                    throw createDeserializationException("When decoding char value, must give a single character", string);
                }
                char c = string.charAt(0);
                nextToken();
                return c;
            }
            case VALUE_NUMBER_INT -> {
                char value = (char) parser.getIntValue();
                nextToken();
                return value;
            }
            default -> {
                char[] value = parser.getText().toCharArray();
                if (value.length == 0) {
                    throw new IllegalStateException("Not characters!");
                }
                nextToken();
                return value[0];
            }
        }
    }

    @Override
    public int decodeInt() throws IOException {
        switch (currentToken) {
            case VALUE_NUMBER_INT ->  {
                int value = parser.getIntValue();
                nextToken();
                return value;
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                int value;
                try {
                    value = Integer.parseInt(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
                nextToken();
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    int unwrapped = decodeInt();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            case VALUE_FALSE -> {
                nextToken();
                return 0;
            }
            case VALUE_TRUE -> {
                nextToken();
                return 1;
            }
            default -> {
                int value = parser.getValueAsInt();
                nextToken();
                return value;
            }
        }
    }

    @Override
    public long decodeLong() throws IOException {
        switch (currentToken) {
            case VALUE_NUMBER_INT ->  {
                long value = parser.getLongValue();
                nextToken();
                return value;
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                long value;
                try {
                    value = Long.parseLong(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
                nextToken();
                return value;
            }
            case VALUE_FALSE -> {
                nextToken();
                return 0;
            }
            case VALUE_TRUE -> {
                nextToken();
                return 1;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    long unwrapped = decodeLong();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            default -> {
                long value = parser.getValueAsLong();
                nextToken();
                return value;
            }
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        switch (currentToken) {
            case VALUE_STRING -> {
                String string = parser.getText();
                float value;
                try {
                    value = Float.parseFloat(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
                nextToken();
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    float unwrapped = decodeFloat();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_FLOAT);
            }
            case VALUE_FALSE -> {
                nextToken();
                return 0;
            }
            case VALUE_TRUE -> {
                nextToken();
                return 1;
            }
            default -> {
                float value = parser.getFloatValue();
                nextToken();
                return value;
            }
        }
    }

    @Override
    public double decodeDouble() throws IOException {
        switch (currentToken) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT ->  {
                double value = parser.getDoubleValue();
                nextToken();
                return value;
            }
            case VALUE_STRING -> {
                String string = parser.getText();
                double value;
                try {
                    value = Double.parseDouble(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to double", string);
                }
                nextToken();
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    double unwrapped = decodeDouble();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            case VALUE_FALSE -> {
                nextToken();
                return 0;
            }
            case VALUE_TRUE -> {
                nextToken();
                return 1;
            }
            default -> {
                double value = parser.getValueAsDouble();
                nextToken();
                return value;
            }
        }
    }

    @NonNull
    @Override
    public BigInteger decodeBigInteger() throws IOException {
        switch (currentToken) {
            case VALUE_STRING -> {
                String string = parser.getText();
                BigInteger value;
                try {
                    value = new BigInteger(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to integer", string);
                }
                nextToken();
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    BigInteger unwrapped = decodeBigInteger();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            case VALUE_FALSE -> {
                nextToken();
                return BigInteger.ZERO;
            }
            case VALUE_TRUE -> {
                nextToken();
                return BigInteger.ONE;
            }
            default -> {
                BigInteger value = parser.getBigIntegerValue();
                nextToken();
                return value;
            }
        }
    }

    @NonNull
    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        switch (currentToken) {
            case VALUE_STRING -> {
                String string = parser.getText();
                BigDecimal value;
                try {
                    value = new BigDecimal(string);
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Unable to coerce string to BigDecimal", string);
                }
                nextToken();
                return value;
            }
            case START_ARRAY -> {
                if (beginUnwrapArray(currentToken)) {
                    BigDecimal unwrapped = decodeBigDecimal();
                    if (endUnwrapArray()) {
                        return unwrapped;
                    } else {
                        throw createDeserializationException("Expected one string, but got array of multiple values", null);
                    }
                }
                throw unexpectedToken(JsonToken.VALUE_NUMBER_INT);
            }
            case VALUE_FALSE -> {
                nextToken();
                return BigDecimal.ZERO;
            }
            case VALUE_TRUE -> {
                nextToken();
                return BigDecimal.ONE;
            }
            default -> {
                BigDecimal value = parser.getDecimalValue();
                nextToken();
                return value;
            }
        }
    }

    private Number decodeNumber() throws IOException {
        Number number = parser.getNumberValue();
        nextToken();
        return number;
    }

    @Override
    public boolean decodeNull() throws IOException {
        if (currentToken == JsonToken.VALUE_NULL) {
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

        nextToken();
        return true;
    }

    private void nextToken() throws IOException {
        currentToken = parser.nextToken();
    }

    private boolean endUnwrapArray() throws IOException {
        currentlyUnwrappingArray = false;
        if (currentToken == JsonToken.END_ARRAY) {
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
        return switch (currentToken) {
            case START_OBJECT -> decodeObjectNode(decodeObject());
            case START_ARRAY -> decodeArrayNode(decodeArray());
            case VALUE_STRING -> JsonNode.createStringNode(decodeString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                JsonNode bestNumberNode = getBestNumberNode();
                nextToken();
                yield bestNumberNode;
            }
            case VALUE_TRUE, VALUE_FALSE -> JsonNode.createBooleanNode(decodeBoolean());
            case VALUE_NULL -> {
                decodeNull();
                yield JsonNode.nullNode();
            }
            default ->
                throw createDeserializationException("Unexpected token " + currentToken + ", expected value", null);
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
        parser.skipChildren();
        nextToken();
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
                JsonParser jsonParser = elementDecoder.parser;
                switch (elementDecoder.currentToken) {
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
                        throw elementDecoder.createDeserializationException("Unexpected token " + elementDecoder.currentToken + ", expected value", null);
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
