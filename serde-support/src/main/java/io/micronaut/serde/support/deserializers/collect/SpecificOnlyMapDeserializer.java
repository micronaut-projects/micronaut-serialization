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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.DeserializerRegistrar;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.util.CustomizableDeserializer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Internal
abstract class SpecificOnlyMapDeserializer<K, V, M extends Map<K, V>> implements CustomizableDeserializer<M>, DeserializerRegistrar<M> {

    private final Class<? extends Map> type;

    SpecificOnlyMapDeserializer(Class<? extends Map> type) {
        this.type = type;
    }

    @Override
    public Deserializer<M> createSpecific(DecoderContext context, Argument<? super M> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length == 2) {
            @SuppressWarnings("unchecked") final Argument<K> keyType = (Argument<K>) generics[0];
            @SuppressWarnings("unchecked") final Argument<V> valueType = SerdeArgumentConf.reconstructGenericWithParentMetadata(type, (Argument<V>) generics[1]);
            final @Nullable Deserializer<? extends V> valueDeser = valueType.equalsType(Argument.OBJECT_ARGUMENT) ? null : context.findDeserializer(valueType)
                .createSpecific(context, valueType);
            return createSpecific(keyType, valueType, valueDeser);
        }
        return new Deserializer<>() {

            @Override
            public M deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super M> type) throws IOException {
                // raw map
                final Object o = decoder.decodeArbitrary();
                if (type.isInstance(o)) {
                    return (M) Objects.requireNonNull(o);
                } else if (o instanceof Map) {
                    final M map = newMap();
                    map.putAll((Map) o);
                    return map;
                } else {
                    throw new SerdeException("Cannot deserialize map of type [" + type + "] from value: " + o);
                }
            }

        };
    }

    private M newMap() {
        if (type == TreeMap.class) {
            return (M) new TreeMap<>();
        }
        if (type == LinkedHashMap.class) {
            return (M) new LinkedHashMap<>();
        }
        return (M) new HashMap<>();
    }

    protected abstract Deserializer<M> createSpecific(Argument<K> keyType, Argument<V> valueType, @Nullable Deserializer<? extends V> valueDeser);

    @Override
    public Argument<M> getType() {
        return (Argument) Argument.of(type, Argument.ofTypeVariable(Object.class, "K"), Argument.ofTypeVariable(Object.class, "V"));
    }
}
