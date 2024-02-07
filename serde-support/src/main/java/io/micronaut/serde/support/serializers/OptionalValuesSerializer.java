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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.util.CustomizableSerializer;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

@Internal
final class OptionalValuesSerializer<V> implements CustomizableSerializer<OptionalValues<V>>, SerializerRegistrar<OptionalValues<V>> {

    @Override
    public io.micronaut.serde.ObjectSerializer<OptionalValues<V>> createSpecific(EncoderContext context, Argument<? extends OptionalValues<V>> type) throws SerdeException {
        Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot serialize raw OptionalValues");
        }
        Argument<V> generic = (Argument<V>) generics[0];
        Serializer<V> valueSerializer = (Serializer<V>) context.findSerializer(generic).createSpecific(context, generic);

        return new io.micronaut.serde.ObjectSerializer<>() {

            @Override
            public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends OptionalValues<V>> type, OptionalValues<V> value) throws IOException {
                for (CharSequence key : value) {
                    Optional<V> opt = value.get(key);
                    if (opt.isPresent()) {
                        encoder.encodeKey(key.toString());
                        valueSerializer.serialize(
                                encoder,
                                context, generic, opt.get()
                        );
                    }
                }
            }

            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<? extends OptionalValues<V>> type, OptionalValues<V> value) throws IOException {
                Objects.requireNonNull(value, "Value cannot be null");

                Encoder objectEncoder = encoder.encodeObject(type);
                serializeInto(encoder, context, type, value);
                objectEncoder.finishStructure();
            }
        };
    }

    @Override
    public boolean isEmpty(EncoderContext context, OptionalValues<V> value) {
        return value.isEmpty();
    }

    @Override
    public Argument<OptionalValues<V>> getType() {
        return (Argument) Argument.of(OptionalValues.class, Argument.ofTypeVariable(Object.class, "V"));
    }
}
