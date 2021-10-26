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
package io.micronaut.serde.serializers;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URL;

@Singleton
final class UrlSerializer implements Serializer<URL>, Deserializer<URL> {
    @Override
    public URL deserialize(Decoder decoder,
                           DecoderContext decoderContext,
                           Argument<? super URL> type)
            throws IOException {
        return new URL(decoder.decodeString());
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context, URL value,
                          Argument<? extends URL> type) throws IOException {
        encoder.encodeString(value.toString());
    }
}
