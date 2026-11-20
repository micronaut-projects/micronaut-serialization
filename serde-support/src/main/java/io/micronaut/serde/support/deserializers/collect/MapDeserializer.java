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
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;
import org.jspecify.annotations.Nullable;

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
abstract class MapDeserializer<K, V, M extends Map<K, @Nullable V>> implements Deserializer<M>, UpdatingDeserializer<M> {

    @Nullable
    private final Deserializer<? extends V> valueDeser;
    private final Argument<K> keyArgument;
    private final Argument<V> valueArgument;

    MapDeserializer(@Nullable Deserializer<? extends V> valueDeser,
                    Argument<K> keyArgument,
                    Argument<V> valueArgument) {
        this.valueDeser = valueDeser;
        this.keyArgument = keyArgument;
        this.valueArgument = valueArgument;
    }

    protected final void doDeserialize(Decoder decoder,
                                       DecoderContext decoderContext,
                                       Argument<? super M> mapType,
                                       Map<K, @Nullable V> map) throws IOException {
        doDeserialize(decoder, decoderContext, mapType, map, false);
    }

    protected final void doDeserialize(Decoder decoder,
                                       DecoderContext decoderContext,
                                       Argument<? super M> mapType,
                                       Map<K, @Nullable V> map,
                                       boolean merge) throws IOException {
        final Decoder objectDecoder = decoder.decodeObject(mapType);
        String key = objectDecoder.decodeKey();
        ConversionService conversionService = decoderContext.getConversionService();
        @Nullable UpdatingDeserializer<V> valueUpdatingDeser = null;
        boolean valueUpdatingDeserResolved = false;
        try {
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
                } else if (merge && map.containsKey(k)) {
                    if (!valueUpdatingDeserResolved) {
                        valueUpdatingDeserResolved = true;
                        valueUpdatingDeser = resolveUpdatingValueDeserializer(decoderContext);
                    }
                    if (valueUpdatingDeser == null) {
                        map.put(k, valueDeser.deserializeNullable(objectDecoder, decoderContext, valueArgument));
                    } else {
                        mergeValue(objectDecoder, decoderContext, map, k, valueDeser, valueUpdatingDeser);
                    }
                } else {
                    map.put(k, valueDeser.deserializeNullable(objectDecoder, decoderContext, valueArgument));
                }
                key = objectDecoder.decodeKey();
            }
        } catch (SerdeException e) {
            e.getPath().add(ReferencePath.ofMap(map.getClass(), mapType, key));
            throw e;
        }
        objectDecoder.finishStructure();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private UpdatingDeserializer<V> resolveUpdatingValueDeserializer(DecoderContext context) throws SerdeException {
        if (valueDeser instanceof UpdatingDeserializer<?> updatingDeserializer) {
            return (UpdatingDeserializer<V>) updatingDeserializer;
        } else if (valueDeser != null) {
            // Generated value deserializers may be replace-only; map merge needs the runtime object path
            // to recursively update existing structured values in place.
            return ObjectShapeSerdeHelper.updatingObjectDeserializer(context, valueArgument);
        }
        return null;
    }

    private void mergeValue(Decoder objectDecoder,
                            DecoderContext decoderContext,
                            Map<K, @Nullable V> map,
                            K key,
                            Deserializer<? extends V> valueDeserializer,
                            UpdatingDeserializer<V> updatingDeserializer) throws IOException {
        V existingValue = map.get(key);
        if (existingValue == null) {
            map.put(key, valueDeserializer.deserializeNullable(objectDecoder, decoderContext, valueArgument));
        } else if (objectDecoder.decodeNull()) {
            map.put(key, null);
        } else {
            updatingDeserializer.deserializeInto(objectDecoder, decoderContext, valueArgument, existingValue);
        }
    }

    @Override
    public void deserializeInto(Decoder decoder,
                                DecoderContext decoderContext,
                                Argument<? super M> mapType,
                                M value) throws IOException {
        doDeserialize(decoder, decoderContext, mapType, value, true);
    }

}
