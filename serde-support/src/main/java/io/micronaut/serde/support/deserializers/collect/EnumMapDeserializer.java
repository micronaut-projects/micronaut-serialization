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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.LimitingStream.RemainingLimits;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.DeserializerRegistrar;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.util.CustomizableDeserializer;

import java.util.EnumMap;

/**
 * Deserializer for enum maps.
 *
 * @param <E> The enum type
 * @param <V> Value type
 */
@Internal
final class EnumMapDeserializer<E extends Enum<E>, V> implements CustomizableDeserializer<EnumMap<E, V>>, DeserializerRegistrar<EnumMap<E, V>> {
    @Override
    public Deserializer<EnumMap<E, V>> createSpecific(DecoderContext context, Argument<? super EnumMap<E, V>> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics) || generics.length != 2) {
            throw new SerdeException("Cannot deserialize raw EnumMap");
        }
        @SuppressWarnings("unchecked") final Argument<E> enumType = (Argument<E>) generics[0];
        @SuppressWarnings("unchecked") final Argument<V> valueType = (Argument<V>) generics[1];
        final Deserializer<? extends V> valueDeser = valueType.equalsType(Argument.OBJECT_ARGUMENT) ? null : context.findDeserializer(valueType)
            .createSpecific(context, valueType);
        final Deserializer<? extends E> enumDeser = context.findDeserializer(enumType).createSpecific(context, enumType);
        return (decoder, decoderContext, mapType) -> {
            final EnumMap<E, V> map = new EnumMap<>(enumType.getType());
            final RemainingLimits remainingLimits = decoderContext.getSerdeConfiguration().map(LimitingStream::limitsFromConfiguration).orElse(LimitingStream.DEFAULT_LIMITS);
            try (Decoder objectDecoder = decoder.decodeObject(mapType)) {
                String key = objectDecoder.decodeKey();
                while (key != null) {
                    JsonNodeDecoder keyDecoder = JsonNodeDecoder.create(JsonNode.createStringNode(key), remainingLimits);
                    E k = enumDeser.deserialize(keyDecoder, decoderContext, enumType);
                    if (valueDeser == null) {
                        map.put(k, (V) objectDecoder.decodeArbitrary());
                    } else {
                        map.put(k, valueDeser.deserializeNullable(objectDecoder, decoderContext, valueType));
                    }
                    key = objectDecoder.decodeKey();
                }
            }
            return map;
        };
    }

    @Override
    public Argument<EnumMap<E, V>> getType() {
        return (Argument) Argument.of(EnumMap.class, Argument.ofTypeVariable(Enum.class, "E"), Argument.ofTypeVariable(Object.class, "V"));
    }
}
