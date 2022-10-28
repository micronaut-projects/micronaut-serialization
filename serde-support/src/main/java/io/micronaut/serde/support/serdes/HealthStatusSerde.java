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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.health.HealthStatus;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Serde for health status.
 *
 * @since 1.0.0
 */
@Singleton
@Requires(classes = HealthStatus.class)
public class HealthStatusSerde implements NullableSerde<HealthStatus> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends HealthStatus> type, HealthStatus value)
            throws IOException {
        encoder.encodeString(value.getName());
    }

    @Override
    public HealthStatus deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super HealthStatus> type)
            throws IOException {
        return new HealthStatus(decoder.decodeString());
    }
}
