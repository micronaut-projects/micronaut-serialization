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
package io.micronaut.serde.json.stream;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.JsonNodeDecoder;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

public class JsonParserDecoder implements Decoder {
    private final JsonParser jsonParser;
    private JsonParser.Event currentEvent;
    private boolean inArray = false;

    public JsonParserDecoder(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
        this.currentEvent = jsonParser.next();
    }

    @Override
    public Decoder decodeArray() throws IOException {
        if (currentEvent == JsonParser.Event.START_ARRAY) {
            this.currentEvent = this.jsonParser.next();
            inArray = true;
        } else {
            throw createDeserializationException("Not an array");
        }
        return this;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return currentEvent != JsonParser.Event.END_ARRAY;
    }

    @Override
    public Decoder decodeObject() throws IOException {
        if (currentEvent == JsonParser.Event.START_OBJECT) {
            this.currentEvent = jsonParser.next();
        } else {
            throw createDeserializationException("Not an object");
        }
        return this;
    }

    @Override
    public String decodeKey() throws IOException {
        if (currentEvent == JsonParser.Event.END_OBJECT) {
            return null;
        }
        try {
            return jsonParser.getString();
        } finally {
            this.currentEvent = jsonParser.next();
        }
    }

    private void afterValue() {
        if (jsonParser.hasNext()) {
            this.currentEvent = jsonParser.next();
        } else {
            // EOF
            this.currentEvent = null;
        }
    }

    @Override
    public String decodeString() throws IOException {
        try {
            return jsonParser.getString();
        } finally {
            afterValue();
        }
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        try {
            final JsonValue value = jsonParser.getValue();
            switch (value.getValueType()) {
                case TRUE:
                    return true;
                case FALSE:
                case NULL:
                    return false;
                case STRING:
                    return Boolean.parseBoolean(value.toString());
                default:
                    throw createDeserializationException("Not a boolean value");
            }
        } finally {
            afterValue();
        }
    }

    @Override
    public byte decodeByte() throws IOException {
        try {
            final JsonValue value = jsonParser.getValue();
            switch (value.getValueType()) {
                case NUMBER:
                    return (byte) ((JsonNumber) value).intValue();
                case STRING:
                    return Byte.parseByte(value.toString());
                default:
                    throw createDeserializationException("Not a byte value");
            }
        } finally {
            afterValue();
        }
    }

    @Override
    public short decodeShort() throws IOException {
        final JsonValue value = jsonParser.getValue();
        switch (value.getValueType()) {
        case NUMBER:
            return (short) ((JsonNumber) value).intValue();
        case STRING:
            return Short.parseShort(value.toString());
        default:
            throw createDeserializationException("Not a byte value");
        }
    }

    @Override
    public char decodeChar() throws IOException {
        return 0;
    }

    @Override
    public int decodeInt() throws IOException {
        try {
            return jsonParser.getInt();
        } finally {
            afterValue();
        }
    }

    @Override
    public long decodeLong() throws IOException {
        try {
            return jsonParser.getLong();
        } finally {
            afterValue();
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        if (currentEvent == JsonParser.Event.VALUE_NUMBER) {
            final JsonNumber value = (JsonNumber) jsonParser.getValue();
            currentEvent = jsonParser.next();
            return value.bigDecimalValue().floatValue();
        }
        throw createDeserializationException("Not a float");
    }

    @Override
    public double decodeDouble() throws IOException {
        if (currentEvent == JsonParser.Event.VALUE_NUMBER) {
            final JsonNumber value = (JsonNumber) jsonParser.getValue();
            currentEvent = jsonParser.next();
            return value.doubleValue();
        }
        throw createDeserializationException("Not a double");
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        try {
            return jsonParser.getBigDecimal().toBigInteger();
        } finally {
            afterValue();
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        try {
            return jsonParser.getBigDecimal();
        } finally {
            afterValue();
        }
    }

    @Override
    public boolean decodeNull() throws IOException {
        if (currentEvent == JsonParser.Event.VALUE_NULL) {
            afterValue();
            return true;
        }
        return false;
    }

    @Override
    public Object decodeArbitrary() throws IOException {
        switch (currentEvent) {
            case START_OBJECT:
                return decodeArbitraryMap(decodeObject());
            case START_ARRAY:
                return decodeArbitraryList(decodeArray());
            case VALUE_STRING:
                return decodeString();
            case VALUE_NUMBER:
                // there's no API to tell what number type the input is
                return decodeBigDecimal();
            case VALUE_TRUE:
            case VALUE_FALSE:
                return decodeBoolean();
            case VALUE_NULL:
                decodeNull();
                return null;
            default:
                throw ((Decoder) this).createDeserializationException("Unexpected token " + currentEvent + ", expected value");
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

    @Override
    public Decoder decodeBuffer() throws IOException {
        JsonNode node = decodeNode();
        return JsonNodeDecoder.create(node);
    }

    private JsonNode decodeNode() throws IOException {
        switch (currentEvent) {
            case START_OBJECT:
                return decodeObjectNode((JsonParserDecoder) decodeObject());
            case START_ARRAY:
                return decodeArrayNode((JsonParserDecoder) decodeArray());
            case VALUE_STRING:
                return JsonNode.createStringNode(decodeString());
            case VALUE_NUMBER:
                // there's no API to tell what number type the input is
                return JsonNode.createNumberNode(decodeBigDecimal());
            case VALUE_TRUE:
            case VALUE_FALSE:
                return JsonNode.createBooleanNode(decodeBoolean());
            case VALUE_NULL:
                decodeNull();
                return JsonNode.nullNode();
            default:
                throw ((Decoder) this).createDeserializationException("Unexpected token " + currentEvent + ", expected value");
        }
    }

    private static JsonNode decodeObjectNode(JsonParserDecoder elementDecoder) throws IOException {
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

    private static JsonNode decodeArrayNode(JsonParserDecoder elementDecoder) throws IOException {
        List<JsonNode> result = new ArrayList<>();
        while (elementDecoder.hasNextArrayValue()) {
            result.add(elementDecoder.decodeNode());
        }
        elementDecoder.finishStructure();
        return JsonNode.createArrayNode(result);
    }

    @Override
    public void skipValue() throws IOException {
        jsonParser.skipObject();
    }

    @Override
    public void finishStructure() throws IOException {
        if (currentEvent == JsonParser.Event.END_ARRAY || currentEvent == JsonParser.Event.END_OBJECT) {
            afterValue();
        } else {
            throw createDeserializationException("Not a structure end");
        }
    }

    @Override
    public IOException createDeserializationException(String message) {
        return new SerdeException(message + " \n at " + jsonParser.getLocation());
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return false;
    }
}
