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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.HashSet;

/**
 * Deserializer of {@link HashSet}.
 *
 * @param <E> The item type
 * @author Denis Stepanov
 */
final class HashSetDeserializer<E> extends CollectionDeserializer<E, HashSet<E>> {

    HashSetDeserializer(boolean decoderAllowsNull, Deserializer<? extends E> valueDeser, Argument<E> collectionItemArgument) {
        super(decoderAllowsNull, valueDeser, collectionItemArgument);
    }

    @Override
    public HashSet<E> deserialize(Decoder decoder, DecoderContext context, Argument<? super HashSet<E>> type) throws IOException {
        HashSet<E> collection = new HashSet<>();
        doDeserialize(decoder, context, collection);
        return collection;
    }

    @Override
    public HashSet<E> getDefaultValue(DecoderContext context, Argument<? super HashSet<E>> type) {
        return new HashSet<>();
    }
}
