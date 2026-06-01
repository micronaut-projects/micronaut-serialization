/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.time.Duration;

@Internal
final class DurationSerde implements SerdeRegistrar<Duration> {
    private final boolean writeAsString;

    DurationSerde() {
        this(false);
    }

    DurationSerde(SerdeConfiguration configuration) {
        this(configuration.isWriteDurationsAsStrings());
    }

    private DurationSerde(boolean writeAsString) {
        this.writeAsString = writeAsString;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Duration> type, Duration value)
        throws IOException {
        if (writeAsString) {
            encoder.encodeString(value.toString());
        } else {
            encoder.encodeLong(value.toNanos());
        }
    }

    @Override
    public Duration deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Duration> type)
        throws IOException {
        if (writeAsString) {
            return Duration.parse(decoder.decodeString());
        }
        return Duration.ofNanos(decoder.decodeLong());
    }

    @Override
    public Argument<Duration> getType() {
        return Argument.of(Duration.class);
    }
}
