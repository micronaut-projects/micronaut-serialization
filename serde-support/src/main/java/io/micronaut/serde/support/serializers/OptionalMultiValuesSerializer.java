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

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Serializer for {@link OptionalMultiValues}.
 * @param <V> The value type
 */
@Singleton
final class OptionalMultiValuesSerializer<V> implements Serializer<OptionalMultiValues<V>> {
    private final boolean alwaysSerializeErrorsAsList;

    public OptionalMultiValuesSerializer(SerializationConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends OptionalMultiValues<V>> type, OptionalMultiValues<V> value) throws IOException {
        Objects.requireNonNull(value, "Values can't be null");
        final Argument[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot serialize raw OptionalMultiValues");
        }
        final Argument generic = generics[0];
        final Argument listGeneric = Argument.listOf(generic);
        Serializer listSerializer = context.findSerializer(listGeneric);
        Serializer valueSerializer = context.findSerializer(generic);
        Encoder objectEncoder = encoder.encodeObject(type);
        for (CharSequence key : value) {
            Optional<? extends List<V>> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                objectEncoder.encodeKey(fieldName);
                List<V> list = opt.get();
                if (alwaysSerializeErrorsAsList) {
                    listSerializer.serialize(
                            objectEncoder,
                            context,
                            listGeneric, list
                    );
                } else {
                    if (list.size() == 1) {
                        valueSerializer.serialize(
                                objectEncoder,
                                context,
                                generic, list.get(0)
                        );
                    } else {
                        listSerializer.serialize(
                                objectEncoder,
                                context,
                                listGeneric, list
                        );
                    }
                }
            }
        }
        objectEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(OptionalMultiValues<V> value) {
        return value == null || value.isEmpty();
    }

}
