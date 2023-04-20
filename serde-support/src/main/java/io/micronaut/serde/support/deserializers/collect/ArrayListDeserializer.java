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
import java.util.ArrayList;

/**
 * Deserializer of {@link ArrayList}.
 *
 * @param <E> The item type
 * @author Denis Stepanov
 */
final class ArrayListDeserializer<E> extends CollectionDeserializer<E, ArrayList<E>> {

    ArrayListDeserializer(boolean decoderAllowsNull, Deserializer<? extends E> valueDeser, Argument<E> collectionItemArgument) {
        super(decoderAllowsNull, valueDeser, collectionItemArgument);
    }

    @Override
    public ArrayList<E> deserialize(Decoder decoder, DecoderContext context, Argument<? super ArrayList<E>> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        ArrayList<E> collection = new ArrayList<>();
        doDeserialize(decoder, context, collection);
        return collection;
    }

    @Override
    public ArrayList<E> getDefaultValue(DecoderContext context, Argument<? super ArrayList<E>> type) {
        return new ArrayList<>();
    }

    @Override
    public boolean allowNull() {
        return true;
    }
}
