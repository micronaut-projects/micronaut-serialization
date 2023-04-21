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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.TreeSet;

/**
 * Deserializer of {@link TreeSet}.
 *
 * @param <E> The item type
 * @author Denis Stepanov
 */
final class TreeSetDeserializer<E> extends CollectionDeserializer<E, TreeSet<E>> {

    TreeSetDeserializer(Deserializer<? extends E> valueDeser, Argument<E> collectionItemArgument) {
        super(valueDeser, collectionItemArgument);
    }

    @Override
    public TreeSet<E> deserialize(Decoder decoder, DecoderContext context, Argument<? super TreeSet<E>> type) throws IOException {
        TreeSet<E> collection = new TreeSet<>();
        doDeserialize(decoder, context, collection);
        return collection;
    }

    @Override
    public TreeSet<E> deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super TreeSet<E>> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public TreeSet<E> getDefaultValue(DecoderContext context, Argument<? super TreeSet<E>> type) {
        return new TreeSet<>();
    }
}
