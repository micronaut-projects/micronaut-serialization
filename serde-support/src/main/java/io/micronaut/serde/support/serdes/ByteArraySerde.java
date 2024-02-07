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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.util.BinaryCodecUtil;

import java.io.IOException;

/**
 * Serde for byte arrays. Nested class for binary compatibility.
 */
@Internal
final class ByteArraySerde implements SerdeRegistrar<byte[]> {
    private final boolean writeLegacyByteArrays;

    public ByteArraySerde(SerdeConfiguration serdeConfiguration) {
        this(serdeConfiguration.isWriteBinaryAsArray());
    }

    public ByteArraySerde(boolean writeLegacyByteArrays) {
        this.writeLegacyByteArrays = writeLegacyByteArrays;
    }

    @Override
    public @NonNull Serializer<byte[]> createSpecific(@NonNull EncoderContext context, @NonNull Argument<? extends byte[]> type) throws SerdeException {
        return context.getSerdeConfiguration().map(ByteArraySerde::new).orElse(this);
    }

    @Override
    public @NonNull Deserializer<byte[]> createSpecific(@NonNull DecoderContext context, @NonNull Argument<? super byte[]> type) throws SerdeException {
        return context.getSerdeConfiguration().map(ByteArraySerde::new).orElse(this);
    }

    @Override
    public byte[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super byte[]> type)
        throws IOException {
        return decoder.decodeBinary();
    }

    @Override
    public byte[] deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super byte[]> type) throws IOException {
        return decoder.decodeBinaryNullable();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends byte[]> type, byte[] value) throws IOException {
        if (writeLegacyByteArrays) {
            BinaryCodecUtil.encodeToArray(encoder, value);
        } else {
            encoder.encodeBinary(value);
        }
    }

    @Override
    public boolean isEmpty(EncoderContext context, byte[] value) {
        return value == null || value.length == 0;
    }

    @Override
    public Argument<byte[]> getType() {
        return Argument.of(byte[].class);
    }
}
