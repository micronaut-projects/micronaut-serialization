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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.ZoneOffset;

/**
 * Serde mapping for {@link ZoneOffset}.
 */
@Internal
final class ZoneOffsetSerde implements SerdeRegistrar<ZoneOffset> {
    private static final Argument<ZoneOffset> ARGUMENT = Argument.of(ZoneOffset.class);

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends ZoneOffset> type, ZoneOffset value) throws IOException {
        encoder.encodeString(value.toString());
    }

    @Override
    public ZoneOffset deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super ZoneOffset> type) throws IOException {
        return ZoneOffset.of(decoder.decodeString());
    }

    @Override
    public @Nullable ZoneOffset deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super ZoneOffset> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<ZoneOffset> getType() {
        return ARGUMENT;
    }
}
