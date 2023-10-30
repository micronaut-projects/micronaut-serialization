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
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * Serializer for the `@JsonValue` annotation.
 *
 * @param <T> The type
 * @author Denis Stepanov
 */
@Internal
final class JsonValueSerializer<T> implements ObjectSerializer<T> {

    private final SerBean.SerProperty<T, Object> jsonValue;

    JsonValueSerializer(SerBean.SerProperty<T, Object> jsonValue) {
        this.jsonValue = jsonValue;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value)
        throws IOException {
        jsonValue.serializer.serialize(
            encoder,
            context,
            jsonValue.argument,
            jsonValue.get(value)
        );
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        throw new SerdeException("Serializer for a value type: " + type + " doesn't support serializing into an existing object");
    }

    @Override
    public boolean isEmpty(EncoderContext context, T value) {
        return jsonValue.serializer.isEmpty(context, jsonValue.get(value));
    }

    @Override
    public boolean isAbsent(EncoderContext context, T value) {
        return jsonValue.serializer.isAbsent(context, jsonValue.get(value));
    }

}
