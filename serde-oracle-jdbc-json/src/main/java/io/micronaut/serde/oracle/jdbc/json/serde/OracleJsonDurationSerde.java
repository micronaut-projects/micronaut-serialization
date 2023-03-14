/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.oracle.jdbc.json.serde;

import java.io.IOException;
import java.time.Duration;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * The custom serde for {@link Duration} for Oracle JSON. Needed because default serde in Micronaut expects number (nanos)
 * to deserialize from, but we are getting it as String from Oracle JSON parser.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonDurationSerde implements NullableSerde<Duration> {

    @Override
    @NonNull
    public Duration deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Duration> type) throws IOException {
        String duration = decoder.decodeString();
        return Duration.parse(duration);
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context,
                          @NonNull Argument<? extends Duration> type, Duration value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            encoder.encodeString(value.toString());
        }
    }
}
