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
package io.micronaut.serde.serializers;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

@Singleton
class OptionalValuesSerializer<V> implements Serializer<OptionalValues<V>> {

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context, OptionalValues<V> value, Argument<? extends OptionalValues<V>> type,
                          Argument<?>... generics) throws IOException {
        Objects.requireNonNull(value, "Value cannot be null");
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot serialize raw OptionalValues");
        }
        Encoder objectEncoder = encoder.encodeObject();

        final Argument<V> generic = (Argument<V>) generics[0];
        Serializer<V> valueSerializer = (Serializer<V>) context.findSerializer(
                generic
        );
        for (CharSequence key : value) {
            Optional<V> opt = value.get(key);
            if (opt.isPresent()) {
                objectEncoder.encodeKey(key.toString());
                valueSerializer.serialize(
                        encoder,
                        context, opt.get(), generic,
                        generic.getTypeParameters()
                );
            }
        }
        objectEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(OptionalValues<V> value) {
        return value.isEmpty();
    }
}
