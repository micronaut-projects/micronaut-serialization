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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.util.CustomizableSerializer;

/**
 * A serializer for any iterable.
 * @param <T> The generic type
 */
@Internal
final class IterableSerializer<T> implements CustomizableSerializer<Iterable<T>>, SerializerRegistrar<Iterable<T>> {
    @Override
    public Serializer<Iterable<T>> createSpecific(EncoderContext context, Argument<? extends Iterable<T>> type)
            throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length > 0) {
            @SuppressWarnings("unchecked") final Argument<T> generic = (Argument<T>) generics[0];
            if (generic.getType() == String.class) {
                return (Serializer) StringIterableSerializer.INSTANCE;
            }
            Serializer<? super T> componentSerializer = context.findSerializer(generic)
                    .createSpecific(context, generic);
            return new CustomizedIterableSerializer<>(generic, componentSerializer);
        }
        return new RuntimeValueIterableSerializer<>();
    }

    @Override
    public Argument<Iterable<T>> getType() {
        return (Argument) Argument.of(Iterable.class, Argument.ofTypeVariable(Object.class, "T"));
    }
}
