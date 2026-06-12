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
package io.micronaut.serde.jsonb;

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.jackson.JacksonDecoder;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonToken;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared JSON-P conversion utilities for JSON-B callback bridges.
 * <p>
 * The bridge converts between bounded {@link JsonNode} trees, JSON-P values,
 * and JSON-P parser/generator callback APIs. It must avoid JSON string
 * round-trips for deserializer callbacks when a streaming decoder is available.
 */
final class JsonbJsonpBridge {
    private JsonbJsonpBridge() {
    }

    /**
     * Converts a bounded Serde tree to the requested JSON-P value type.
     *
     * @param node The source JSON tree
     * @param targetType The requested JSON-P target type
     * @return The JSON-P value
     */
    static JsonValue toJsonpValue(JsonNode node, Class<?> targetType) {
        JsonProvider provider = JsonProvider.provider();
        if (node.isNull()) {
            return JsonValue.NULL;
        }
        if (targetType == JsonString.class) {
            return provider.createValue(node.coerceStringValue());
        }
        if (targetType == JsonNumber.class) {
            return jsonNumber(provider, node.getNumberValue());
        }
        if (targetType == JsonArray.class) {
            return jsonArray(provider, node);
        }
        if (targetType == JsonObject.class) {
            return jsonObject(provider, node);
        }
        if (targetType == JsonStructure.class) {
            return node.isArray() ? jsonArray(provider, node) : jsonObject(provider, node);
        }
        if (node.isObject()) {
            return jsonObject(provider, node);
        }
        if (node.isArray()) {
            return jsonArray(provider, node);
        }
        if (node.isString()) {
            return provider.createValue(node.getStringValue());
        }
        if (node.isNumber()) {
            return jsonNumber(provider, node.getNumberValue());
        }
        if (node.isBoolean()) {
            return node.getBooleanValue() ? JsonValue.TRUE : JsonValue.FALSE;
        }
        return JsonValue.NULL;
    }

    /**
     * Reads the next JSON-P parser event into a Serde tree. This is used by
     * JSON-B callback contexts where the callback owns the parser cursor.
     *
     * @param parser The JSON-P parser
     * @return The parsed JSON tree
     */
    static JsonNode parseNext(JsonParser parser) {
        return parse(parser, parser.next());
    }

    /**
     * Creates a JSON-P parser over an already-buffered tree.
     *
     * @param node The source tree
     * @return The JSON-P parser
     */
    static JsonParser parserForDeserializer(JsonNode node) {
        return new JsonNodeParser(node, node.isArray());
    }

    /**
     * Creates a JSON-P parser for a JSON-B deserializer callback. When the
     * decoder is backed by Jackson, parser events are streamed directly from the
     * decoder; otherwise the method falls back to the bounded tree path.
     *
     * @param decoder The source decoder
     * @return The JSON-P parser
     * @throws IOException If decoder access fails
     */
    static JsonParser parserForDeserializer(Decoder decoder) throws IOException {
        if (decoder instanceof JsonbDecoder jsonbDecoder && jsonbDecoder.delegate() instanceof JacksonDecoder jacksonDecoder) {
            return new JacksonDecoderParser(jacksonDecoder);
        }
        if (decoder instanceof JacksonDecoder jacksonDecoder) {
            return new JacksonDecoderParser(jacksonDecoder);
        }
        return parserForDeserializer(decoder.decodeNode());
    }

    /**
     * Writes a bounded tree to a JSON-P generator.
     *
     * @param generator The JSON-P generator
     * @param node The tree to write
     */
    @SuppressWarnings("java:S3776")
    static void writeJsonValue(JsonGenerator generator, JsonNode node) {
        if (node.isNull()) {
            generator.writeNull();
        } else if (node.isString()) {
            generator.write(node.getStringValue());
        } else if (node.isNumber()) {
            Number number = node.getNumberValue();
            if (number instanceof BigInteger integer) {
                generator.write(integer);
            } else if (number instanceof BigDecimal decimal) {
                generator.write(decimal);
            } else if (number instanceof Float || number instanceof Double) {
                generator.write(number.doubleValue());
            } else {
                generator.write(number.longValue());
            }
        } else if (node.isBoolean()) {
            generator.write(node.getBooleanValue());
        } else if (node.isArray()) {
            generator.writeStartArray();
            for (JsonNode value : node.values()) {
                writeJsonValue(generator, value);
            }
            generator.writeEnd();
        } else if (node.isObject()) {
            generator.writeStartObject();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                writeJsonValue(entry.getKey(), generator, entry.getValue());
            }
            generator.writeEnd();
        }
    }

    /**
     * Invokes a JSON-B serializer and captures its JSON-P generator output as a
     * bounded Serde tree.
     *
     * @param serializer The JSON-B serializer
     * @param value The value to serialize
     * @param codec The fallback codec used by recursive serializer context calls
     * @return The generated JSON tree
     * @throws IOException If serializer output cannot be captured
     */
    static JsonNode writeWithJsonbSerializer(JsonbSerializer<Object> serializer,
                                             Object value,
                                             JsonbFallbackCodec codec) throws IOException {
        JsonNodeGenerator generator = new JsonNodeGenerator(codec.limits());
        serializer.serialize(value, generator, new JsonbSerializationContext(codec));
        return generator.completedValue();
    }

    private static void writeJsonValue(String key, JsonGenerator generator, JsonNode node) {
        if (node.isNull()) {
            generator.writeNull(key);
        } else {
            generator.writeKey(key);
            writeJsonValue(generator, node);
        }
    }

    @SuppressWarnings("java:S3776")
    private static JsonNode parse(JsonParser parser, JsonParser.Event event) {
        return switch (event) {
            case START_OBJECT -> {
                Map<String, JsonNode> map = new LinkedHashMap<>();
                while (parser.hasNext()) {
                    JsonParser.Event next = parser.next();
                    if (next == JsonParser.Event.END_OBJECT) {
                        break;
                    }
                    if (next == JsonParser.Event.KEY_NAME) {
                        String key = parser.getString();
                        map.put(key, parseNext(parser));
                    }
                }
                yield JsonNode.createObjectNode(map);
            }
            case START_ARRAY -> {
                java.util.List<JsonNode> list = new java.util.ArrayList<>();
                while (parser.hasNext()) {
                    JsonParser.Event next = parser.next();
                    if (next == JsonParser.Event.END_ARRAY) {
                        break;
                    }
                    list.add(parse(parser, next));
                }
                yield JsonNode.createArrayNode(list);
            }
            case KEY_NAME -> {
                Map<String, JsonNode> map = new LinkedHashMap<>();
                JsonParser.Event current = event;
                while (current == JsonParser.Event.KEY_NAME) {
                    String key = parser.getString();
                    map.put(key, parseNext(parser));
                    if (!parser.hasNext()) {
                        break;
                    }
                    current = parser.next();
                    if (current == JsonParser.Event.END_OBJECT) {
                        break;
                    }
                }
                yield JsonNode.createObjectNode(map);
            }
            case VALUE_STRING -> JsonNode.createStringNode(parser.getString());
            case VALUE_NUMBER -> JsonNode.createNumberNode(parser.getBigDecimal());
            case VALUE_TRUE -> JsonNode.createBooleanNode(true);
            case VALUE_FALSE -> JsonNode.createBooleanNode(false);
            case VALUE_NULL -> JsonNode.nullNode();
            case END_ARRAY, END_OBJECT -> throw new JsonbException("Unexpected JSON parser event: " + event);
        };
    }

    private static JsonNumber jsonNumber(JsonProvider provider, Number number) {
        if (number instanceof BigDecimal decimal) {
            return provider.createValue(decimal);
        }
        if (number instanceof BigInteger integer) {
            return provider.createValue(integer);
        }
        if (number instanceof Float || number instanceof Double) {
            return provider.createValue(number.doubleValue());
        }
        return provider.createValue(number.longValue());
    }

    private static JsonObject jsonObject(JsonProvider provider, JsonNode node) {
        JsonObjectBuilder builder = provider.createObjectBuilder();
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            builder.add(entry.getKey(), toJsonpValue(entry.getValue(), JsonValue.class));
        }
        return builder.build();
    }

    private static JsonArray jsonArray(JsonProvider provider, JsonNode node) {
        JsonArrayBuilder builder = provider.createArrayBuilder();
        for (JsonNode value : node.values()) {
            builder.add(toJsonpValue(value, JsonValue.class));
        }
        return builder.build();
    }

    private static final class JsonbSerializationContext implements SerializationContext {
        private final JsonbFallbackCodec codec;

        private JsonbSerializationContext(JsonbFallbackCodec codec) {
            this.codec = codec;
        }

        @Override
        public <T> void serialize(String key, T object, JsonGenerator generator) {
            generator.writeKey(key);
            serialize(object, generator);
        }

        @Override
        @SuppressWarnings("java:S2583")
        public <T> void serialize(T object, JsonGenerator generator) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Argument<T> argument = object == null ? (Argument) Argument.OBJECT_ARGUMENT : (Argument) Argument.of(object.getClass());
                writeJsonValue(generator, codec.writeValueToTree(argument, object));
            } catch (IOException e) {
                throw new JsonbException("Cannot serialize JSON-B context value", e);
            }
        }
    }

    @SuppressWarnings("resource")
    private static final class JsonNodeGenerator extends LimitingStream implements JsonGenerator {
        private final java.util.ArrayDeque<Container> containers = new java.util.ArrayDeque<>();
        private @Nullable JsonNode value;
        private @Nullable String currentKey;

        JsonNodeGenerator(RemainingLimits remainingLimits) {
            super(remainingLimits);
        }

        JsonNode completedValue() {
            if (value == null) {
                throw new JsonbException("JSON-B serializer did not write a value");
            }
            return value;
        }

        @Override
        public JsonGenerator writeStartObject() {
            increaseDepthChecked();
            containers.push(new ObjectContainer());
            return this;
        }

        @Override
        public JsonGenerator writeStartObject(String name) {
            writeKey(name);
            return writeStartObject();
        }

        @Override
        public JsonGenerator writeKey(String name) {
            currentKey = Objects.requireNonNull(name, "name");
            return this;
        }

        @Override
        public JsonGenerator writeStartArray() {
            increaseDepthChecked();
            containers.push(new ArrayContainer());
            return this;
        }

        @Override
        public JsonGenerator writeStartArray(String name) {
            writeKey(name);
            return writeStartArray();
        }

        @Override
        public JsonGenerator write(String name, JsonValue value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, String value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, BigInteger value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, BigDecimal value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, int value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, long value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, double value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator write(String name, boolean value) {
            writeKey(name);
            return write(value);
        }

        @Override
        public JsonGenerator writeNull(String name) {
            writeKey(name);
            return writeNull();
        }

        @Override
        public JsonGenerator writeEnd() {
            if (containers.isEmpty()) {
                throw new JsonbException("Cannot end JSON structure");
            }
            addValue(containers.pop().node());
            decreaseDepth();
            return this;
        }

        @Override
        public JsonGenerator write(JsonValue value) {
            writeJsonpValue(value);
            return this;
        }

        @Override
        public JsonGenerator write(String value) {
            addValue(JsonNode.createStringNode(value));
            return this;
        }

        @Override
        public JsonGenerator write(BigDecimal value) {
            addValue(JsonNode.createNumberNode(value));
            return this;
        }

        @Override
        public JsonGenerator write(BigInteger value) {
            addValue(JsonNode.createNumberNode(value));
            return this;
        }

        @Override
        public JsonGenerator write(int value) {
            addValue(JsonNode.createNumberNode(value));
            return this;
        }

        @Override
        public JsonGenerator write(long value) {
            addValue(JsonNode.createNumberNode(value));
            return this;
        }

        @Override
        public JsonGenerator write(double value) {
            addValue(JsonNode.createNumberNode(value));
            return this;
        }

        @Override
        public JsonGenerator write(boolean value) {
            addValue(JsonNode.createBooleanNode(value));
            return this;
        }

        @Override
        public JsonGenerator writeNull() {
            addValue(JsonNode.nullNode());
            return this;
        }

        @Override
        public void close() {
            // Generator output is held in memory, so there is no backing resource to close.
        }

        @Override
        public void flush() {
            // Generator output is held in memory, so there is no backing resource to flush.
        }

        private void addValue(JsonNode node) {
            if (containers.isEmpty()) {
                if (value != null) {
                    throw new JsonbException("JSON-B serializer wrote multiple values");
                }
                value = node;
                return;
            }
            containers.peek().add(currentKey, node);
            currentKey = null;
        }

        private void writeJsonpValue(JsonValue jsonValue) {
            switch (jsonValue.getValueType()) {
                case ARRAY -> {
                    writeStartArray();
                    for (JsonValue item : jsonValue.asJsonArray()) {
                        write(item);
                    }
                    writeEnd();
                }
                case OBJECT -> {
                    writeStartObject();
                    JsonObject object = jsonValue.asJsonObject();
                    object.forEach(this::write);
                    writeEnd();
                }
                case STRING -> write(((JsonString) jsonValue).getString());
                case NUMBER -> {
                    JsonNumber number = (JsonNumber) jsonValue;
                    if (number.isIntegral()) {
                        write(number.bigIntegerValue());
                    } else {
                        write(number.bigDecimalValue());
                    }
                }
                case TRUE -> write(true);
                case FALSE -> write(false);
                case NULL -> writeNull();
                default -> throw new JsonbException("Unsupported JSON value type: " + jsonValue.getValueType());
            }
        }

        private void increaseDepthChecked() {
            try {
                increaseDepth();
            } catch (SerdeException e) {
                throw new JsonbException(e.getMessage(), e);
            }
        }
    }

    private sealed interface Container permits ObjectContainer, ArrayContainer {
        void add(@Nullable String key, JsonNode node);

        JsonNode node();
    }

    private static final class ObjectContainer implements Container {
        private final Map<String, JsonNode> values = new LinkedHashMap<>();

        @Override
        public void add(@Nullable String key, JsonNode node) {
            if (key == null) {
                throw new JsonbException("JSON object value is missing a key");
            }
            values.put(key, node);
        }

        @Override
        public JsonNode node() {
            return JsonNode.createObjectNode(values);
        }
    }

    private static final class ArrayContainer implements Container {
        private final java.util.List<JsonNode> values = new java.util.ArrayList<>();

        @Override
        public void add(@Nullable String key, JsonNode node) {
            values.add(node);
        }

        @Override
        public JsonNode node() {
            return JsonNode.createArrayNode(values);
        }
    }

    private record ParserEvent(JsonParser.Event event, @Nullable String key, @Nullable JsonNode node) {
    }

    private static final class JacksonDecoderParser implements JsonParser {
        private static final jakarta.json.stream.JsonLocation UNKNOWN_LOCATION = new UnknownJsonLocation();

        private final JacksonDecoder decoder;
        private final tools.jackson.core.JsonParser parser;
        private JsonParser.@Nullable Event currentEvent;
        private boolean seenRoot;
        private boolean finished;
        private int depth;

        private JacksonDecoderParser(JacksonDecoder decoder) throws IOException {
            this.decoder = decoder;
            this.parser = decoder.parserForStreaming();
            decoder.peekTokenForStreaming();
        }

        @Override
        public boolean hasNext() {
            try {
                return !finished && decoder.peekTokenForStreaming() != null;
            } catch (IOException e) {
                throw new JsonbException("Cannot read JSON-B parser token", e);
            }
        }

        @Override
        public JsonParser.Event next() {
            try {
                JsonToken token = decoder.nextTokenForStreaming();
                currentEvent = event(token);
                updateRootBoundary(token);
                return currentEvent;
            } catch (IOException e) {
                throw new JsonbException("Cannot read JSON-B parser token", e);
            }
        }

        @Override
        public JsonParser.Event currentEvent() {
            if (currentEvent == null) {
                throw new JsonbException("JSON parser is not positioned on an event");
            }
            return currentEvent;
        }

        @Override
        public String getString() {
            return parser.getString();
        }

        @Override
        public boolean isIntegralNumber() {
            return parser.currentToken() == JsonToken.VALUE_NUMBER_INT;
        }

        @Override
        public int getInt() {
            return parser.getIntValue();
        }

        @Override
        public long getLong() {
            return parser.getLongValue();
        }

        @Override
        public BigDecimal getBigDecimal() {
            return parser.getDecimalValue();
        }

        @Override
        public jakarta.json.stream.JsonLocation getLocation() {
            return UNKNOWN_LOCATION;
        }

        @Override
        public void close() {
            // The owning JacksonDecoder controls the parser lifecycle.
        }

        private void updateRootBoundary(JsonToken token) {
            if (!seenRoot) {
                seenRoot = true;
                if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                    depth = 1;
                } else {
                    finished = true;
                }
                return;
            }
            if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                depth++;
            } else if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
                depth--;
                if (depth == 0) {
                    finished = true;
                }
            }
        }

        private static JsonParser.Event event(@Nullable JsonToken token) {
            return switch (Objects.requireNonNull(token, "token")) {
                case START_OBJECT -> JsonParser.Event.START_OBJECT;
                case END_OBJECT -> JsonParser.Event.END_OBJECT;
                case START_ARRAY -> JsonParser.Event.START_ARRAY;
                case END_ARRAY -> JsonParser.Event.END_ARRAY;
                case PROPERTY_NAME -> JsonParser.Event.KEY_NAME;
                case VALUE_STRING -> JsonParser.Event.VALUE_STRING;
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> JsonParser.Event.VALUE_NUMBER;
                case VALUE_TRUE -> JsonParser.Event.VALUE_TRUE;
                case VALUE_FALSE -> JsonParser.Event.VALUE_FALSE;
                case VALUE_NULL -> JsonParser.Event.VALUE_NULL;
                default -> throw new JsonbException("Unexpected JSON token: " + token);
            };
        }
    }

    private static final class UnknownJsonLocation implements jakarta.json.stream.JsonLocation {
        @Override
        public long getLineNumber() {
            return -1;
        }

        @Override
        public long getColumnNumber() {
            return -1;
        }

        @Override
        public long getStreamOffset() {
            return -1;
        }
    }

    private static final class JsonNodeParser implements JsonParser {
        private static final jakarta.json.stream.JsonLocation UNKNOWN_LOCATION = new UnknownJsonLocation();

        private final List<ParserEvent> events = new ArrayList<>();
        private int index = -1;

        private JsonNodeParser(JsonNode node, boolean skipRootArrayStart) {
            append(node, skipRootArrayStart);
        }

        @Override
        public boolean hasNext() {
            return index + 1 < events.size();
        }

        @Override
        public JsonParser.Event next() {
            index++;
            return current().event();
        }

        @Override
        public JsonParser.Event currentEvent() {
            return current().event();
        }

        @Override
        public String getString() {
            ParserEvent event = current();
            if (event.event() == JsonParser.Event.KEY_NAME) {
                return Objects.requireNonNull(event.key());
            }
            JsonNode node = Objects.requireNonNull(event.node());
            return node.coerceStringValue();
        }

        @Override
        public boolean isIntegralNumber() {
            Number number = number();
            return number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long
                || number instanceof BigInteger;
        }

        @Override
        public int getInt() {
            return number().intValue();
        }

        @Override
        public long getLong() {
            return number().longValue();
        }

        @Override
        public BigDecimal getBigDecimal() {
            Number number = number();
            if (number instanceof BigDecimal decimal) {
                return decimal;
            }
            if (number instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            return new BigDecimal(String.valueOf(number));
        }

        @Override
        public jakarta.json.stream.JsonLocation getLocation() {
            return UNKNOWN_LOCATION;
        }

        @Override
        public JsonValue getValue() {
            JsonNode node = current().node();
            if (node == null) {
                throw new JsonbException("Current JSON parser event does not represent a value");
            }
            return toJsonpValue(node, JsonValue.class);
        }

        @Override
        public void close() {
            // JsonNodeParser iterates over in-memory events only.
        }

        private Number number() {
            JsonNode node = Objects.requireNonNull(current().node());
            return node.getNumberValue();
        }

        private ParserEvent current() {
            if (index < 0 || index >= events.size()) {
                throw new JsonbException("JSON parser is not positioned on an event");
            }
            return events.get(index);
        }

        private void append(JsonNode node, boolean skipArrayStart) {
            if (node.isObject()) {
                events.add(new ParserEvent(JsonParser.Event.START_OBJECT, null, node));
                for (Map.Entry<String, JsonNode> entry : node.entries()) {
                    events.add(new ParserEvent(JsonParser.Event.KEY_NAME, entry.getKey(), null));
                    append(entry.getValue(), false);
                }
                events.add(new ParserEvent(JsonParser.Event.END_OBJECT, null, node));
            } else if (node.isArray()) {
                if (!skipArrayStart) {
                    events.add(new ParserEvent(JsonParser.Event.START_ARRAY, null, node));
                }
                for (JsonNode value : node.values()) {
                    append(value, false);
                }
                events.add(new ParserEvent(JsonParser.Event.END_ARRAY, null, node));
            } else if (node.isString()) {
                events.add(new ParserEvent(JsonParser.Event.VALUE_STRING, null, node));
            } else if (node.isNumber()) {
                events.add(new ParserEvent(JsonParser.Event.VALUE_NUMBER, null, node));
            } else if (node.isBoolean()) {
                events.add(new ParserEvent(node.getBooleanValue() ? JsonParser.Event.VALUE_TRUE : JsonParser.Event.VALUE_FALSE, null, node));
            } else {
                events.add(new ParserEvent(JsonParser.Event.VALUE_NULL, null, node));
            }
        }
    }
}
