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
package io.micronaut.serde.serdes;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Serde for handling enums.
 * @param <E> The enum type.
 * @since 1.0.0
 */
@Singleton
final class EnumSerde<E extends Enum<E>> implements NullableSerde<E> {

    @Override
    public E deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super E> type) throws IOException {
        @SuppressWarnings("rawtypes") final Class t = type.getType();
        return (E) Enum.valueOf(t, decoder.decodeString());
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, E value, Argument<? extends E> type) throws IOException {
        encoder.encodeString(value.name());
    }
}

/**
 * Deserializer for enum sets.
 * @param <E> The enum type
 */
@Singleton
final class EnumSetDeserializer<E extends Enum<E>> implements Deserializer<EnumSet<E>> {

    @Override
    public EnumSet<E> deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super EnumSet<E>> type)
            throws IOException {
        final Argument[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot deserialize raw list");
        }
        @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
        final Decoder arrayDecoder = decoder.decodeArray();
        HashSet<E> set = new HashSet<>();
        while (arrayDecoder.hasNextArrayValue()) {
            set.add(
                Enum.valueOf(generic.getType(), arrayDecoder.decodeString())
            );
        }
        arrayDecoder.finishStructure();
        return EnumSet.copyOf(set);
    }
}
