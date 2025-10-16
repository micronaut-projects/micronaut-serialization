/*
 * Copyright 2017-2025 original authors
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
import java.time.ZoneId;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

/**
 * ZoneId serde.
 *
 * @since 2.16
 */
@Internal
final class ZoneIdSerde implements SerdeRegistrar<ZoneId> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends ZoneId> type, ZoneId value) throws IOException {
        encoder.encodeString(value.getId());
    }

    @Override
    public ZoneId deserialize(Decoder decoder, DecoderContext context, Argument<? super ZoneId> type) throws IOException {
        return ZoneId.of(decoder.decodeString());
    }

    @Override
    public ZoneId deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super ZoneId> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<ZoneId> getType() {
        return Argument.of(ZoneId.class);
    }
}
