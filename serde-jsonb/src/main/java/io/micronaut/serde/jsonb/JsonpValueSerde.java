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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;
import jakarta.inject.Singleton;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * JSON-P value serde used by the JSON-B provider for generated serializers.
 */
@Internal
@Singleton
final class JsonpValueSerde implements SerdeRegistrar<JsonValue> {
    private static final Argument<JsonValue> JSON_VALUE = Argument.of(JsonValue.class);

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends JsonValue> type, JsonValue value) throws IOException {
        serializeValue(encoder, value);
    }

    private static void serializeValue(Encoder encoder, JsonValue value) throws IOException {
        switch (value.getValueType()) {
            case ARRAY -> {
                Encoder arrayEncoder = encoder.encodeArray(JSON_VALUE);
                for (JsonValue item : value.asJsonArray()) {
                    serializeValue(arrayEncoder, item);
                }
                arrayEncoder.finishStructure();
            }
            case OBJECT -> {
                Encoder objectEncoder = encoder.encodeObject(JSON_VALUE);
                for (Map.Entry<String, JsonValue> entry : value.asJsonObject().entrySet()) {
                    objectEncoder.encodeKey(entry.getKey());
                    serializeValue(objectEncoder, entry.getValue());
                }
                objectEncoder.finishStructure();
            }
            case STRING -> encoder.encodeString(((JsonString) value).getString());
            case NUMBER -> encodeNumber(encoder, (JsonNumber) value);
            case TRUE -> encoder.encodeBoolean(true);
            case FALSE -> encoder.encodeBoolean(false);
            case NULL -> encoder.encodeNull();
        }
    }

    private static void encodeNumber(Encoder encoder, JsonNumber number) throws IOException {
        if (number.isIntegral()) {
            BigInteger integer = number.bigIntegerValue();
            if (integer.bitLength() < Integer.SIZE) {
                encoder.encodeInt(integer.intValue());
            } else if (integer.bitLength() < Long.SIZE) {
                encoder.encodeLong(integer.longValue());
            } else {
                encoder.encodeBigInteger(integer);
            }
        } else {
            encoder.encodeBigDecimal(number.bigDecimalValue());
        }
    }

    @Override
    public JsonValue deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super JsonValue> type) throws IOException {
        return toJsonpValue(decoder.decodeNode(), type.getType());
    }

    @Override
    public @Nullable JsonValue deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super JsonValue> type) throws IOException {
        if (decoder.decodeNull()) {
            return JsonValue.NULL;
        }
        return deserialize(decoder, context, type);
    }

    private static JsonValue toJsonpValue(JsonNode node, Class<?> targetType) {
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

    @Override
    public Argument<JsonValue> getType() {
        return JSON_VALUE;
    }

    @Override
    public Iterable<Argument<?>> getTypes() {
        return List.of(
            Argument.of(JsonValue.class),
            Argument.of(JsonStructure.class),
            Argument.of(JsonObject.class),
            Argument.of(JsonArray.class),
            Argument.of(JsonString.class),
            Argument.of(JsonNumber.class)
        );
    }
}
