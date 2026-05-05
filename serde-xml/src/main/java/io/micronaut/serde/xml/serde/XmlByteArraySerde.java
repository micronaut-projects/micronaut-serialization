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
package io.micronaut.serde.xml.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

/**
 * XML serde registrar for byte arrays.
 */
@Internal
public final class XmlByteArraySerde implements SerdeRegistrar<byte[]> {
    private static final Argument<byte[]> TYPE = Argument.of(byte[].class);

    @Override
    public @NonNull Argument<byte[]> getType() {
        return TYPE;
    }

    @Override
    public byte[] deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super byte[]> type) throws IOException {
        return decoder.decodeBinary();
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends byte[]> type, @NonNull byte[] value) throws IOException {
        ((XmlGenerator) encoder).encodeBinary(value);
    }
}
