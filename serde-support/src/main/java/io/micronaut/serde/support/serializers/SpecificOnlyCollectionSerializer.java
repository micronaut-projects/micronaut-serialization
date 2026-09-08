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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.util.CustomizableSerializer;

import java.util.Collection;

import static io.micronaut.serde.support.util.SerdeArgumentConf.reconstructGenericWithParentMetadata;

/**
 * A base class for specific collection serializers that register with a type-variable argument.
 * This ensures that user-defined {@code Serializer<List<SomeSpecificType>>} beans do not
 * accidentally match when serializing other generic lists (e.g. {@code List<String>}).
 *
 * @param <E> The element type
 * @param <C> The collection type
 * @see <a href="https://github.com/micronaut-projects/micronaut-serialization/issues/1187">Issue #1187</a>
 */
@Internal
abstract class SpecificOnlyCollectionSerializer<E, C extends Collection<E>>
        implements CustomizableSerializer<C>, SerializerRegistrar<C> {

    private final Class<? extends Collection> type;

    SpecificOnlyCollectionSerializer(Class<? extends Collection> type) {
        this.type = type;
    }

    @Override
    public Serializer<C> createSpecific(EncoderContext context, Argument<? extends C> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length > 0) {
            @SuppressWarnings("unchecked") final Argument<E> generic = reconstructGenericWithParentMetadata(type, (Argument<E>) generics[0]);
            if (generic.getType() == String.class) {
                return (Serializer) StringIterableSerializer.INSTANCE;
            }
            Serializer<? super E> componentSerializer = context.findSerializer(generic).createSpecific(context, generic);
            return (Serializer) new CustomizedIterableSerializer<>(generic, componentSerializer);
        }
        return (Serializer) new RuntimeValueIterableSerializer<>();
    }

    @Override
    public Argument<C> getType() {
        return (Argument) Argument.of(type, Argument.ofTypeVariable(Object.class, "E"));
    }
}
