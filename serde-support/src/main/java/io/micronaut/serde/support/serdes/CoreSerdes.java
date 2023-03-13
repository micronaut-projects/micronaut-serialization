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
package io.micronaut.serde.support.serdes;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Period;
import java.util.Map;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Factory class for core serdes.
 */
@Factory
@BootstrapContextCompatible
public class CoreSerdes {

    public static final NullableSerde<Duration> DURATION_SERDE = new DurationSerde();
    public static final NullableSerde<Period> PERIOD_SERDE = new PeriodSerde();
    public static final CharSequenceSerde CHAR_SEQUENCE_SERDE = new CharSequenceSerde();

    /**
     * Serde used for object arrays.
     * @return The serde
     */
    @Singleton
    @NonNull
    @BootstrapContextCompatible
    protected Serde<Object[]> objectArraySerde() {
        return new ObjectArraySerde();
    }

    /**
     * Serde for duration.
     * @return Duration serde
     */
    @Singleton
    @NonNull
    @BootstrapContextCompatible
    protected NullableSerde<Duration> durationSerde() {
        return DURATION_SERDE;
    }

    /**
     * Serde for period.
     * @return Period serde
     */
    @Singleton
    @NonNull
    @BootstrapContextCompatible
    protected NullableSerde<Period> periodSerde() {
        return PERIOD_SERDE;
    }

    /**
     * Serde for CharSequence.
     * @return CharSequence serde
     */
    @Singleton
    @NonNull
    @BootstrapContextCompatible
    @Order(100) // lower priority than string
    protected NullableSerde<CharSequence> charSequenceSerde() {
        return CHAR_SEQUENCE_SERDE;
    }

    /**
     * Serde for period.
     * @return Period serde
     */
    @Singleton
    @NonNull
    @BootstrapContextCompatible
    protected NullableSerde<JsonNode> jsonNodeSerde() {
        return new NullableSerde<JsonNode>() {
            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<? extends JsonNode> type, JsonNode value)
                    throws IOException {
                serialize0(encoder, value);
            }

            private void serialize0(Encoder encoder, JsonNode value) throws IOException {
                if (value == null) {
                    value = JsonNode.nullNode();
                }
                if (value.isNull()) {
                    encoder.encodeNull();
                } else if (value.isBoolean()) {
                    encoder.encodeBoolean(value.getBooleanValue());
                } else if (value.isString()) {
                    encoder.encodeString(value.getStringValue());
                } else if (value.isNumber()) {
                    Number numberValue = value.getNumberValue();
                    if (numberValue instanceof Integer || numberValue instanceof Byte || numberValue instanceof Short || numberValue instanceof Long) {
                        encoder.encodeLong(numberValue.longValue());
                    } else if (numberValue instanceof BigInteger bi) {
                        encoder.encodeBigInteger(bi);
                    } else if (numberValue instanceof BigDecimal bd) {
                        encoder.encodeBigDecimal(bd);
                    } else {
                        // double, float, other number types
                        encoder.encodeDouble(numberValue.doubleValue());
                    }
                } else if (value.isObject()) {
                    Encoder objectEncoder = encoder.encodeObject(Argument.of(JsonNode.class));
                    for (Map.Entry<String, JsonNode> entry : value.entries()) {
                        objectEncoder.encodeKey(entry.getKey());
                        serialize0(encoder, entry.getValue());
                    }
                    objectEncoder.finishStructure();
                } else if (value.isArray()) {
                    Encoder arrayEncoder = encoder.encodeArray(Argument.of(JsonNode.class));
                    for (JsonNode entry : value.values()) {
                        serialize0(encoder, entry);
                    }
                    arrayEncoder.finishStructure();
                } else {
                    throw new IllegalArgumentException("Unsupported JSON node");
                }
            }

            @Override
            public JsonNode deserialize(Decoder decoder, DecoderContext context, Argument<? super JsonNode> type) throws IOException {
                // null is decoded as JsonNull
                return deserializeNonNull(decoder, context, type);
            }

            @Override
            public JsonNode deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super JsonNode> type)
                    throws IOException {
                return decoder.decodeNode();
            }
        };
    }

    private static class DurationSerde implements NullableSerde<Duration> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Duration> type, Duration value)
            throws IOException {
            encoder.encodeLong(value.toNanos());
        }

        @Override
        public Duration deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Duration> type)
            throws IOException {
            return Duration.ofNanos(decoder.decodeLong());
        }
    }

    private static class PeriodSerde implements NullableSerde<Period> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Period> type, Period value)
            throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public Period deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Period> type)
            throws IOException {
            return Period.parse(decoder.decodeString());
        }
    }

    private static final class CharSequenceSerde
        implements NullableSerde<CharSequence> {
        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends CharSequence> type, CharSequence value) throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public CharSequence deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super CharSequence> type) throws IOException {
            return decoder.decodeString();
        }
    }
}
