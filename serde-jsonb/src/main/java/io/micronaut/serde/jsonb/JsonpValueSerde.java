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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

/**
 * JSON-P value serde used by the JSON-B provider for generated serializers.
 *
 * @param <T> The deserialize type
 */
@Internal
@Singleton
final class JsonpValueSerde<T extends JsonValue> implements Serializer<JsonValue>, Deserializer<T> {
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
            default ->
                throw new IOException("Unsupported JSON-P value type: " + value.getValueType());
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
    public T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        return (T) JsonbJsonpBridge.toJsonpValue(decoder.decodeNode(), type.getType());
    }

    @Override
    public @Nullable T deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
        if (decoder.decodeNull()) {
            return (T) JsonValue.NULL;
        }
        return deserialize(decoder, context, type);
    }

}
