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
package io.micronaut.serde.support.deserializers.collect;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * Deserializer of {@link LinkedHashSet}.
 *
 * @param <E> The item type
 * @author Denis Stepanov
 */
@Internal
final class LinkedHashSetDeserializer<E> extends CollectionDeserializer<E, LinkedHashSet<E>> {

    LinkedHashSetDeserializer(Deserializer<? extends E> valueDeser, Argument<E> collectionItemArgument) {
        super(valueDeser, collectionItemArgument);
    }

    @Override
    public LinkedHashSet<E> deserialize(Decoder decoder, DecoderContext context, Argument<? super LinkedHashSet<E>> type) throws IOException {
        LinkedHashSet<E> collection = new LinkedHashSet<>();
        doDeserialize(decoder, context, collection);
        return collection;
    }

    @Override
    public LinkedHashSet<E> deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super LinkedHashSet<E>> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public LinkedHashSet<E> getDefaultValue(DecoderContext context, Argument<? super LinkedHashSet<E>> type) {
        return new LinkedHashSet<>();
    }
}
