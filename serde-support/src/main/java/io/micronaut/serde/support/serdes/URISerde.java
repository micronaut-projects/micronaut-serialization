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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.net.URI;

@Internal
final class URISerde implements SerdeRegistrar<URI> {

    @Override
    public Argument<URI> getType() {
        return Argument.of(URI.class);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends URI> type, URI value)
        throws IOException {
        encoder.encodeString(value.toString());
    }

    @Override
    public URI deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super URI> type)
        throws IOException {
        return URI.create(decoder.decodeString());
    }

    @Override
    public URI deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super URI> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }
}
