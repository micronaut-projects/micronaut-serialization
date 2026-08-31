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
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.jackson.JacksonEncoder;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Bounded codec bridge used by JSON-B reflection fallback paths.
 * <p>
 * This class is intentionally a thin adapter over Micronaut Serialization
 * codecs. It owns limit propagation and JSON-B encoder/decoder wrapping, but
 * it must not grow type-specific conversion rules that duplicate registered
 * serializers and deserializers.
 */
final class JsonbFallbackCodec {
    private static final String DEFAULT_BINARY_DATA_STRATEGY = jakarta.json.bind.config.BinaryDataStrategy.BYTE;

    private final SerdeRegistry registry;
    private final LimitingStream.RemainingLimits limits;
    private final String binaryDataStrategy;

    JsonbFallbackCodec(ObjectMapper mapper,
                       @Nullable SerdeConfiguration serdeConfiguration) {
        this(mapper, serdeConfiguration, DEFAULT_BINARY_DATA_STRATEGY);
    }

    JsonbFallbackCodec(ObjectMapper mapper,
                       @Nullable SerdeConfiguration serdeConfiguration,
                       String binaryDataStrategy) {
        this.registry = mapper.getSerdeRegistry();
        this.limits = serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
        this.binaryDataStrategy = binaryDataStrategy;
    }

    /**
     * Reads one bounded tree from the Jackson parser used by reflection fallback
     * preflight and JSON-B customization callbacks.
     *
     * @param parser The parser positioned at the value to read
     * @return The bounded JSON tree
     * @throws IOException If parser or limit handling fails
     */
    @SuppressWarnings("java:S2095")
    JsonNode readTree(JsonParser parser) throws IOException {
        return JacksonDecoder.create(parser, limits).decodeNode();
    }

    LimitingStream.RemainingLimits limits() {
        return limits;
    }

    static CoercionPolicy coercionPolicy(Deserializer.DecoderContext decoderContext) {
        return decoderContext.getDeserializationConfiguration()
            .map(CoercionPolicy::fromConfiguration)
            .orElse(CoercionPolicy.LENIENT);
    }

    /**
     * Deserializes an already-buffered fallback tree through the normal Serde
     * registry. Use this instead of hand-converting JSON-B fallback values.
     *
     * @param node The bounded JSON tree
     * @param argument The target argument
     * @param <T> The target type
     * @return The deserialized value
     * @throws IOException If deserialization fails
     */
    @SuppressWarnings("java:S2095")
    <T> @Nullable T readValue(JsonNode node, Argument<T> argument) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = findDeserializer(decoderContext, argument);
        return deserializer.deserializeNullable(new JsonbDecoder(JsonNodeDecoder.create(node, limits, coercionPolicy(decoderContext)), binaryDataStrategy), decoderContext, argument);
    }

    /**
     * Deserializes a fallback tree for a reflection-discovered generic type.
     *
     * @param node The bounded JSON tree
     * @param type The target reflection type
     * @return The deserialized value
     * @throws IOException If deserialization fails
     */
    @Nullable Object readValue(JsonNode node, Type type) throws IOException {
        return readValue(node, Argument.of(type));
    }

    /**
     * Serializes a value through the Serde registry into the active JSON-B
     * generator. Null is handled here because nullable serializers may not be
     * available for runtime-only fallback arguments.
     *
     * @param generator The target JSON generator
     * @param argument The source argument
     * @param value The value to serialize
     * @param <T> The source type
     * @throws IOException If serialization fails
     */
    @SuppressWarnings("java:S2095")
    <T> void writeValue(JsonGenerator generator, Argument<T> argument, @Nullable T value) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer<? super T> serializer = findSerializer(encoderContext, argument);
        serializer.serialize(new JsonbEncoder(JacksonEncoder.create(generator, limits), binaryDataStrategy), encoderContext, argument, value);
    }

    /**
     * Writes a prebuilt tree to the JSON-B generator without re-entering Serde
     * lookup. This is used for JSON-B custom serializers that have already
     * emitted a bounded tree.
     *
     * @param generator The target JSON generator
     * @param node The tree to write
     */
    @SuppressWarnings("java:S3776")
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

    /**
     * Serializes a value to a bounded tree for JSON-B adapter and serializer
     * callbacks that need JSON-P style intermediate values.
     *
     * @param argument The source argument
     * @param value The value to serialize
     * @param <T> The source type
     * @return The completed JSON tree
     * @throws IOException If serialization fails
     */
    @SuppressWarnings("java:S2095")
    <T> JsonNode writeValueToTree(Argument<T> argument, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits);
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer<? super T> serializer = findSerializer(encoderContext, argument);
        serializer.serialize(new JsonbEncoder(encoder, binaryDataStrategy), encoderContext, argument, value);
        return encoder.getCompletedValue();
    }

    private <T> Serializer<? super T> findSerializer(Serializer.EncoderContext encoderContext, Argument<T> argument) throws SerdeException {
        return encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
    }

    private <T> Deserializer<? extends T> findDeserializer(Deserializer.DecoderContext decoderContext, Argument<T> argument) throws SerdeException {
        return decoderContext.findDeserializer(argument).createSpecific(decoderContext, argument);
    }
}
