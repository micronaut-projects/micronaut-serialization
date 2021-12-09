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

import java.io.IOException;
import java.util.Optional;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

/**
 * Serializer for optional.
 * @param <T> The generic type
 */
@Singleton
class OptionalSerializer<T> implements Serializer<Optional<T>> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Serializer<Optional<T>> createSpecific(Argument<? extends Optional<T>> type, EncoderContext encoderContext)
            throws SerdeException {
        final Argument[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Serializing raw optionals is for type: " + type);
        }
        final Argument<T> generic = getGeneric(type);
        final Serializer<? super T> componentSerializer = getComponentSerializer(encoderContext, generic);
        return new OptionalSerializer() {
            @Override
            protected Serializer getComponentSerializer(EncoderContext context, Argument generic) {
                return componentSerializer;
            }

            @Override
            protected Argument getGeneric(Argument type) {
                return generic;
            }

            @Override
            public boolean isEmpty(Object value) {
                Optional o = (Optional) value;
                if (value != null && o.isPresent()) {
                    return componentSerializer.isEmpty((T) o.get());
                } else {
                    return super.isEmpty(o);
                }
            }
        };
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Optional<T> value,
                          Argument<? extends Optional<T>> type) throws IOException {
        final Argument<T> generic = getGeneric(type);
        final Serializer<? super T> componentSerializer = getComponentSerializer(context, generic);
        final T o = value.orElse(null);
        if (o != null) {
            componentSerializer.serialize(
                    encoder,
                    context,
                    o,
                    generic
            );
        } else {
            encoder.encodeNull();
        }
    }

    /**
     * The component serializer.
     * @param context The context
     * @param generic The generic
     * @return This serializer
     * @throws SerdeException
     */
    protected Serializer<? super T> getComponentSerializer(EncoderContext context, Argument<T> generic)
            throws SerdeException {
        return context.findSerializer(generic);
    }

    /**
     * Gets the generic type for the optional.
     * @param type The type
     * @return The generic
     * @throws SerdeException If there is no generic
     */
    protected Argument<T> getGeneric(Argument<? extends Optional<T>> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Serializing raw optionals is not supported for type: " + type);
        }
        //noinspection unchecked
        return (Argument<T>) generics[0];
    }

    @Override
    public boolean isEmpty(Optional<T> value) {
        return value == null || !value.isPresent();
    }

    @Override
    public boolean isAbsent(Optional<T> value) {
        return value == null || !value.isPresent();
    }
}
