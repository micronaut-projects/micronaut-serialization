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

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.serde.Encoder;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
class OptionalMultiValuesSerializer<V> implements Serializer<OptionalMultiValues<V>> {
    private final boolean alwaysSerializeErrorsAsList;

    public OptionalMultiValuesSerializer(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context, OptionalMultiValues<V> value, Argument<? extends OptionalMultiValues<V>> type,
                          Argument<?>... generics) throws IOException {
        Objects.requireNonNull(value, "Values can't be null");
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot serialize raw OptionalMultiValues");
        }
        final Argument generic = generics[0];
        final Argument listGeneric = Argument.listOf(generic);
        Serializer listSerializer = context.findSerializer(listGeneric);
        Serializer valueSerializer = context.findSerializer(generic);
        Encoder objectEncoder = encoder.encodeObject();
        for (CharSequence key : value) {
            Optional<? extends List<V>> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                objectEncoder.encodeKey(fieldName);
                List<V> list = opt.get();
                if (list.size() == 1 && (list.get(0).getClass() != JsonError.class || !alwaysSerializeErrorsAsList)) {
                    valueSerializer.serialize(
                            encoder,
                            context, list.get(0), listGeneric,
                            generic.getTypeParameters()
                    );
                } else {
                    listSerializer.serialize(
                            encoder,
                            context, list, generic,
                            generic
                    );
                }
            }
        }
        objectEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(OptionalMultiValues<V> value) {
        return value.isEmpty();
    }

}
