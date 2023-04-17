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
import java.util.Collection;

/**
 * Deserializer of {@link Collection}.
 *
 * @param <C> The collection type
 * @param <E> The item type
 * @author Denis Stepanov
 */
abstract class CollectionDeserializer<E, C extends Collection<E>> implements Deserializer<C> {

    private final boolean decoderAllowsNull;
    private final Deserializer<? extends E> valueDeser;
    private final Argument<E> collectionItemArgument;

    CollectionDeserializer(boolean decoderAllowsNull, Deserializer<? extends E> valueDeser, Argument<E> collectionItemArgument) {
        this.decoderAllowsNull = decoderAllowsNull;
        this.valueDeser = valueDeser;
        this.collectionItemArgument = collectionItemArgument;
    }

    protected final void doDeserialize(Decoder decoder,
                                       DecoderContext decoderContext,
                                       Collection<E> collection) throws IOException {
        final Decoder arrayDecoder = decoder.decodeArray();
        while (arrayDecoder.hasNextArrayValue()) {
            if (!decoderAllowsNull && arrayDecoder.decodeNull()) {
                collection.add(null);
                continue;
            }
            E deserialize = valueDeser.deserialize(
                arrayDecoder,
                decoderContext,
                collectionItemArgument
            );
            collection.add(deserialize);
        }
        arrayDecoder.finishStructure();
    }

}
