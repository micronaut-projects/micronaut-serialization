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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableSerializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;

/**
 * Serializer for optional.
 *
 * @param <T> The generic type
 */
@Singleton
class OptionalSerializer<T> implements CustomizableSerializer<Optional<T>> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Serializer<Optional<T>> createSpecific(EncoderContext encoderContext, Argument<? extends Optional<T>> type)
            throws SerdeException {
        final Argument[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Serializing raw optionals is for type: " + type);
        }
        final Argument<?>[] generics1 = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics1)) {
            throw new SerdeException("Serializing raw optionals is not supported for type: " + type);
        }
        //noinspection unchecked
        final Argument<T> generic = (Argument<T>) generics1[0];
        final Serializer<? super T> componentSerializer = encoderContext.findSerializer(generic).createSpecific(encoderContext, generic);
        return new Serializer<Optional<T>>() {

            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Optional<T>> type, Optional<T> value) throws IOException {
                final Argument<?>[] generics1 = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics1)) {
                    throw new SerdeException("Serializing raw optionals is not supported for type: " + type);
                }
                //noinspection unchecked
                final Argument<T> generic = (Argument<T>) generics1[0];
                final T o = value.orElse(null);
                if (o != null) {
                    componentSerializer.serialize(
                            encoder,
                            context,
                            generic, o
                    );
                } else {
                    encoder.encodeNull();
                }
            }

            @Override
            public boolean isEmpty(EncoderContext context, Optional<T> value) {
                Optional o = value;
                if (value != null && o.isPresent()) {
                    return componentSerializer.isEmpty(context, (T) o.get());
                }
                return true;
            }

            @Override
            public boolean isAbsent(EncoderContext context, Optional<T> value) {
                return value == null || !value.isPresent();
            }
        };
    }

}
