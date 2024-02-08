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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.util.Map;

/**
 * Deserializer of {@link Map}.
 *
 * @param <M> The map type
 * @param <K> The map key type
 * @param <V> The map value type
 * @author Denis Stepanov
 */
@Internal
abstract class MapDeserializer<K, V, M extends Map<K, V>> implements Deserializer<M> {

    private final Deserializer<? extends V> valueDeser;
    private final Argument<K> keyArgument;
    private final Argument<V> valueArgument;

    MapDeserializer(Deserializer<? extends V> valueDeser, Argument<K> keyArgument, Argument<V> valueArgument) {
        this.valueDeser = valueDeser;
        this.keyArgument = keyArgument;
        this.valueArgument = valueArgument;
    }

    protected final void doDeserialize(Decoder decoder,
                                       DecoderContext decoderContext,
                                       Argument<? super M> mapType,
                                       Map<K, V> map) throws IOException {
        final Decoder objectDecoder = decoder.decodeObject(mapType);
        String key = objectDecoder.decodeKey();
        ConversionService conversionService = decoderContext.getConversionService();
        while (key != null) {
            K k;
            if (keyArgument.isInstance(key)) {
                k = (K) key;
            } else {
                try {
                    k = conversionService.convertRequired(key, keyArgument);
                } catch (ConversionErrorException e) {
                    throw new SerdeException("Error converting Map key [" + key + "] to target type [" + keyArgument + "]: " + e.getMessage(), e);
                }
            }
            if (valueDeser == null) {
                map.put(k, (V) objectDecoder.decodeArbitrary());
            } else {
                map.put(k, valueDeser.deserializeNullable(objectDecoder, decoderContext, valueArgument));
            }
            key = objectDecoder.decodeKey();
        }
        objectDecoder.finishStructure();
    }

}
