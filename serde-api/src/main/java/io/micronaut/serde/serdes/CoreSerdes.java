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
package io.micronaut.serde.serdes;

import java.io.IOException;
import java.time.Duration;
import java.time.Period;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Factory class for core serdes.
 */
@Factory
public class CoreSerdes {
    /**
     * Serde used for object arrays.
     * @return The serde
     */
    @Singleton
    @NonNull
    protected Serde<Object[]> objectArraySerde() {
        return new ObjectArraySerde();
    }

    /**
     * Serde for duration.
     * @return Duration serde
     */
    @Singleton
    @NonNull
    protected NullableSerde<Duration> durationSerde() {
        return new NullableSerde<Duration>() {
            @Override
            public void serialize(Encoder encoder, EncoderContext context, Duration value, Argument<? extends Duration> type)
                    throws IOException {
                encoder.encodeLong(value.toNanos());
            }

            @Override
            public Duration deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Duration> type)
                    throws IOException {
                return Duration.ofNanos(decoder.decodeLong());
            }
        };
    }

    /**
     * Serde for period.
     * @return Period serde
     */
    @Singleton
    @NonNull
    protected NullableSerde<Period> periodSerde() {
        return new NullableSerde<Period>() {
            @Override
            public void serialize(Encoder encoder, EncoderContext context, Period value, Argument<? extends Period> type)
                    throws IOException {
                encoder.encodeString(value.toString());
            }

            @Override
            public Period deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Period> type)
                    throws IOException {
                return Period.parse(decoder.decodeString());
            }
        };
    }
}
