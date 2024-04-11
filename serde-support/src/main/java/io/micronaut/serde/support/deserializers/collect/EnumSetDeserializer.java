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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.DeserializerRegistrar;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Deserializer for enum sets.
 *
 * @param <E> The enum type
 */
@Internal
final class EnumSetDeserializer<E extends Enum<E>> implements DeserializerRegistrar<EnumSet<E>> {

    @Override
    public EnumSet<E> deserialize(Decoder decoder, DecoderContext context, Argument<? super EnumSet<E>> type)
        throws IOException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot deserialize raw EnumSet");
        }
        @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
        final Decoder arrayDecoder = decoder.decodeArray();
        Class<E> enumType = generic.getType();
        EnumSet<E> enumSet = EnumSet.noneOf(enumType);
        Deserializer<? extends E> enumDeser = context.findDeserializer(enumType).createSpecific(context, generic);
        while (arrayDecoder.hasNextArrayValue()) {
            enumSet.add(
                enumDeser.deserialize(arrayDecoder, context, generic)
            );
        }
        arrayDecoder.finishStructure();
        return enumSet;
    }

    @Override
    public Argument<EnumSet<E>> getType() {
        return (Argument) Argument.of(EnumSet.class, Argument.ofTypeVariable(Enum.class, "E"));
    }
}
