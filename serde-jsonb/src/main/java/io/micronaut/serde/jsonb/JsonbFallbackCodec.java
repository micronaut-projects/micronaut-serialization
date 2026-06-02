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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.jackson.JacksonEncoder;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.json.JsonValue;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded codec bridge used by JSON-B reflection fallback paths.
 */
final class JsonbFallbackCodec {
    private final SerdeRegistry registry;
    private final LimitingStream.RemainingLimits limits;
    private final String binaryDataStrategy;
    private final boolean strictIJsonDates;

    JsonbFallbackCodec(ObjectMapper mapper,
                       @Nullable SerdeConfiguration serdeConfiguration,
                       String binaryDataStrategy) {
        this.registry = mapper.getSerdeRegistry();
        this.limits = serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
        this.binaryDataStrategy = binaryDataStrategy;
        this.strictIJsonDates = serdeConfiguration != null && serdeConfiguration.isWriteDateTimesAsStrictIJson();
    }

    JsonNode readTree(JsonParser parser) throws IOException {
        return JacksonDecoder.create(parser, limits).decodeNode();
    }

    <T> @Nullable T readValue(JsonNode node, Argument<T> argument) throws IOException {
        if (JsonValue.class.isAssignableFrom(argument.getType())) {
            return readJsonpValue(node, argument);
        }
        if (Calendar.class.isAssignableFrom(argument.getType())) {
            return readCalendar(node, argument);
        }
        if (argument.getType() == OffsetTime.class) {
            @SuppressWarnings("unchecked")
            T value = (T) OffsetTime.parse(node.coerceStringValue());
            return value;
        }
        if (argument.getType() == ZoneOffset.class) {
            @SuppressWarnings("unchecked")
            T value = (T) ZoneOffset.of(node.coerceStringValue());
            return value;
        }
        if (PriorityQueue.class.isAssignableFrom(argument.getType())) {
            return readPriorityQueue(node, argument);
        }
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = findDeserializer(decoderContext, argument);
        return deserializer.deserializeNullable(new JsonbDecoder(JsonNodeDecoder.create(node, limits), binaryDataStrategy), decoderContext, argument);
    }

    @Nullable Object readValue(JsonNode node, Type type) throws IOException {
        return readValue(node, Argument.of(type));
    }

    <T> void writeValue(JsonGenerator generator, Argument<T> argument, @Nullable T value) throws IOException {
        switch (value) {
            case null -> {
                generator.writeNull();
                return;
            }
            case JsonValue jsonValue -> {
                writeTree(generator, JsonbJsonpBridge.toJsonNode(jsonValue));
                return;
            }
            case Calendar calendar -> {
                generator.writeString(JsonbCalendarSerde.format(calendar, strictIJsonDates));
                return;
            }
            default -> {
            }
        }
        if (value instanceof OffsetTime || value instanceof ZoneOffset) {
            generator.writeString(value.toString());
            return;
        }
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer<? super T> serializer = findSerializer(encoderContext, argument);
        serializer.serialize(new JsonbEncoder(JacksonEncoder.create(generator, limits), binaryDataStrategy), encoderContext, argument, value);
    }

    void writeTree(JsonGenerator generator, JsonNode node) {
        if (node.isNull()) {
            generator.writeNull();
        } else if (node.isString()) {
            generator.writeString(node.getStringValue());
        } else if (node.isNumber()) {
            Number number = node.getNumberValue();
            if (number instanceof BigDecimal decimal) {
                generator.writeNumber(decimal);
            } else if (number instanceof java.math.BigInteger integer) {
                generator.writeNumber(integer);
            } else if (number instanceof Float || number instanceof Double) {
                generator.writeNumber(number.doubleValue());
            } else {
                generator.writeNumber(number.longValue());
            }
        } else if (node.isBoolean()) {
            generator.writeBoolean(node.getBooleanValue());
        } else if (node.isArray()) {
            generator.writeStartArray();
            for (JsonNode value : node.values()) {
                writeTree(generator, value);
            }
            generator.writeEndArray();
        } else if (node.isObject()) {
            generator.writeStartObject();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                generator.writeName(entry.getKey());
                writeTree(generator, entry.getValue());
            }
            generator.writeEndObject();
        }
    }

    <T> JsonNode writeValueToTree(Argument<T> argument, @Nullable T value) throws IOException {
        switch (value) {
            case null -> {
                return JsonNode.nullNode();
            }
            case JsonValue jsonValue -> {
                return JsonbJsonpBridge.toJsonNode(jsonValue);
            }
            case Calendar calendar -> {
                return JsonNode.createStringNode(JsonbCalendarSerde.format(calendar, strictIJsonDates));
            }
            default -> {
            }
        }
        if (value instanceof OffsetTime || value instanceof ZoneOffset) {
            return JsonNode.createStringNode(value.toString());
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits);
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer<? super T> serializer = findSerializer(encoderContext, argument);
        serializer.serialize(new JsonbEncoder(encoder, binaryDataStrategy), encoderContext, argument, value);
        return encoder.getCompletedValue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T readJsonpValue(JsonNode node, Argument<T> argument) {
        return (T) JsonbJsonpBridge.toJsonpValue(node, argument.getType());
    }

    private <T> T readCalendar(JsonNode node, Argument<T> argument) {
        Calendar calendar = JsonbCalendarSerde.parse(node.coerceStringValue(), argument.getType());
        @SuppressWarnings("unchecked")
        T value = (T) calendar;
        return value;
    }

    private <T> T readPriorityQueue(JsonNode node, Argument<T> argument) throws IOException {
        PriorityQueue<Object> queue = new PriorityQueue<>();
        Argument<?> elementType = argument.getTypeParameters().length == 0 ? Argument.OBJECT_ARGUMENT : argument.getTypeParameters()[0];
        if (node.isArray()) {
            for (JsonNode item : node.values()) {
                queue.add(readValue(item, elementType));
            }
        }
        @SuppressWarnings("unchecked")
        T value = (T) queue;
        return value;
    }

    private <T> Serializer<? super T> findSerializer(Serializer.EncoderContext encoderContext, Argument<T> argument) throws SerdeException {
        return encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
    }

    private <T> Deserializer<? extends T> findDeserializer(Deserializer.DecoderContext decoderContext, Argument<T> argument) throws SerdeException {
        return decoderContext.findDeserializer(argument).createSpecific(decoderContext, argument);
    }

    static @Nullable Object normalizeUntypedValue(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                converted.put(entry.getKey(), normalizeUntypedValue(entry.getValue()));
            }
            return converted;
        }
        if (node.isArray()) {
            List<Object> converted = new ArrayList<>(node.size());
            for (JsonNode item : node.values()) {
                converted.add(normalizeUntypedValue(item));
            }
            return converted;
        }
        if (node.isNumber()) {
            Number number = node.getNumberValue();
            return number instanceof BigDecimal ? number : new BigDecimal(String.valueOf(number));
        }
        if (node.isString()) {
            return node.getStringValue();
        }
        if (node.isBoolean()) {
            return node.getBooleanValue();
        }
        return null;
    }
}
