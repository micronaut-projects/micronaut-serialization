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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.util.CustomizableDeserializer;
import io.micronaut.serde.util.CustomizableSerializer;

import java.io.IOException;
import java.util.Optional;

/**
 * Serializer for optional.
 *
 * @param <T> The generic type
 */
@Internal
final class OptionalSerde<T> implements CustomizableSerializer<Optional<T>>, CustomizableDeserializer<Optional<T>>, SerdeRegistrar<Optional<T>> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Serializer<Optional<T>> createSpecific(EncoderContext encoderContext, Argument<? extends Optional<T>> type)
        throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Serializing raw optionals is not supported for type: " + type);
        }
        //noinspection unchecked
        final Argument<T> generic = (Argument<T>) generics[0];
        final Serializer<? super T> componentSerializer = encoderContext.findSerializer(generic).createSpecific(encoderContext, generic);
        return new Serializer<>() {

            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Optional<T>> type, Optional<T> value) throws IOException {
                final T o = value.orElse(null);
                if (o == null) {
                    encoder.encodeNull();
                } else {
                    componentSerializer.serialize(
                        encoder,
                        context,
                        generic,
                        o
                    );
                }
            }

            @Override
            public boolean isEmpty(EncoderContext context, Optional<T> value) {
                if (value != null && value.isPresent()) {
                    return componentSerializer.isEmpty(context, (T) ((Optional) value).get());
                }
                return true;
            }

            @Override
            public boolean isAbsent(EncoderContext context, Optional<T> value) {
                return value == null || value.isEmpty();
            }
        };
    }

    @Override
    public Deserializer<Optional<T>> createSpecific(DecoderContext context, Argument<? super Optional<T>> type) throws SerdeException {
        @SuppressWarnings("unchecked") final Argument<T> generic =
            (Argument<T>) type.getFirstTypeVariable().orElse(null);
        if (generic == null) {
            throw new SerdeException("Cannot deserialize raw optional");
        }
        final Deserializer<? extends T> deserializer = context.findDeserializer(generic)
            .createSpecific(context, generic);

        return new Deserializer<>() {

            @Override
            public Optional<T> deserialize(Decoder decoder, DecoderContext context, Argument<? super Optional<T>> type)
                throws IOException {
                if (decoder.decodeNull()) {
                    return Optional.empty();
                } else {
                    return Optional.ofNullable(
                        deserializer.deserialize(
                            decoder,
                            context,
                            generic
                        )
                    );
                }
            }

            @Override
            public Optional<T> deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Optional<T>> type) throws IOException {
                return deserialize(decoder, context, type);
            }

            @Override
            public Optional<T> getDefaultValue(DecoderContext context, Argument<? super Optional<T>> type) {
                return Optional.empty();
            }
        };
    }

    @Override
    public Argument<Optional<T>> getType() {
        return (Argument) Argument.of(Optional.class, Argument.ofTypeVariable(Object.class, "T")) ;
    }
}
