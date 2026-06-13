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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;

import java.io.IOException;

/**
 * A wrapped object serializer.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 2.3
 */
@Internal
final class WrappedObjectSerializer<T> implements ObjectSerializer<T> {

    private final Serializer<T> serializer;
    private final Argument<?> wrapperArgument;
    private final Keys wrapperKeys;

    WrappedObjectSerializer(Serializer<T> serializer, String wrapperProperty, Argument<?> wrapperArgument) {
        this.serializer = serializer;
        this.wrapperArgument = wrapperArgument;
        this.wrapperKeys = Keys.create(wrapperProperty);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try (KeysAwareEncoder wrapperEncoder = KeysAwareEncoder.of(encoder.encodeObject(wrapperArgument))) {
            wrapperEncoder.encodeKey(wrapperKeys, 0);
            serializer.serialize(wrapperEncoder, context, type, value);
        }
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        KeysAwareEncoder keysAwareEncoder = KeysAwareEncoder.of(encoder);
        keysAwareEncoder.encodeKey(wrapperKeys, 0);
        serializer.serialize(keysAwareEncoder, context, type, value);
    }
}
