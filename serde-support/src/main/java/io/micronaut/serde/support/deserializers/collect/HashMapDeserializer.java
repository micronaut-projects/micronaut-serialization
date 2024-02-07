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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.HashMap;

/**
 * Deserializer of {@link HashMap}.
 *
 * @param <K> The map key type
 * @param <V> The map value type
 * @author Denis Stepanov
 */
@Internal
final class HashMapDeserializer<K, V> extends MapDeserializer<K, V, HashMap<K, V>> {

    HashMapDeserializer(Deserializer<? extends V> valueDeser, Argument<K> keyArgument, Argument<V> valueArgument) {
        super(valueDeser, keyArgument, valueArgument);
    }

    @Override
    public HashMap<K, V> deserialize(Decoder decoder, DecoderContext context, Argument<? super HashMap<K, V>> type) throws IOException {
        HashMap<K, V> map = new HashMap<>();
        doDeserialize(decoder, context, type, map);
        return map;
    }

    @Override
    public HashMap<K, V> getDefaultValue(DecoderContext context, Argument<? super HashMap<K, V>> type) {
        return new HashMap<>();
    }
}
