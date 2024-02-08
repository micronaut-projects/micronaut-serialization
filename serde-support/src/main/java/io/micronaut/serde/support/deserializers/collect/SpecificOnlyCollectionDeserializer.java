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
package io.micronaut.serde.support.deserializers.collect;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.DeserializerRegistrar;
import io.micronaut.serde.util.CustomizableDeserializer;

import java.util.Collection;

@Internal
abstract class SpecificOnlyCollectionDeserializer<E, C extends Collection<E>> implements CustomizableDeserializer<C>, DeserializerRegistrar<C> {

    private final Class<? extends Collection> type;

    SpecificOnlyCollectionDeserializer(Class<? extends Collection> type) {
        this.type = type;
    }

    @Override
    public Deserializer<C> createSpecific(DecoderContext context, Argument<? super C> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot deserialize raw list");
        }
        @SuppressWarnings("unchecked") final Argument<E> collectionItemArgument = (Argument<E>) generics[0];
        final Deserializer<? extends E> valueDeser = context.findDeserializer(collectionItemArgument)
            .createSpecific(context, collectionItemArgument);

        return createSpecific(type, collectionItemArgument, valueDeser);
    }

    protected abstract Deserializer<C> createSpecific(Argument<? super C> collectionArgument,
                                                      Argument<E> collectionItemArgument,
                                                      Deserializer<? extends E> valueDeser);

    @Override
    public Argument<C> getType() {
        return (Argument) Argument.of(type, Argument.ofTypeVariable(Object.class, "E"));
    }

}
