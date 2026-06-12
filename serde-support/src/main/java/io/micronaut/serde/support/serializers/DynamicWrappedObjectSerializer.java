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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.support.serializers.SerBean.SerProperty;

import java.io.IOException;

/**
 * A wrapped object serializer that resolves the wrapper key from the value.
 *
 * @param <T> The type
 */
@Internal
final class DynamicWrappedObjectSerializer<T> implements ObjectSerializer<T> {

    private final Serializer<T> serializer;
    private final SerProperty<T, Object> wrapperProperty;

    DynamicWrappedObjectSerializer(Serializer<T> serializer, SerProperty<T, Object> wrapperProperty) {
        this.serializer = serializer;
        this.wrapperProperty = wrapperProperty;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try (KeysAwareEncoder wrapperEncoder = KeysAwareEncoder.of(encoder.encodeObject(Argument.OBJECT_ARGUMENT))) {
            wrapperEncoder.encodeKey(Keys.create(wrapperProperty.getRequiredString(value)), 0);
            serializer.serialize(wrapperEncoder, context, type, value);
        }
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        KeysAwareEncoder keysAwareEncoder = KeysAwareEncoder.of(encoder);
        keysAwareEncoder.encodeKey(Keys.create(wrapperProperty.getRequiredString(value)), 0);
        serializer.serialize(keysAwareEncoder, context, type, value);
    }
}
