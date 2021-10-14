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
package io.micronaut.json.generated.serializer;

import io.micronaut.core.type.Argument;
import io.micronaut.json.GenericTypeFactory;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.json.Encoder;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerdeRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

class OptionalMultiValuesSerializer<V> implements Serializer<OptionalMultiValues<V>> {
    private final boolean alwaysSerializeErrorsAsList;
    private final Serializer<? super V> valueSerializer;
    private final Serializer<? super List<V>> listSerializer;

    public OptionalMultiValuesSerializer(JsonConfiguration jacksonConfiguration, Serializer<? super V> valueSerializer, Serializer<? super List<V>> listSerializer) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
        this.valueSerializer = valueSerializer;
        this.listSerializer = listSerializer;
    }

    @Override
    public void serialize(Encoder encoder, OptionalMultiValues<V> value) throws IOException {
        Encoder objectEncoder = encoder.encodeObject();
        for (CharSequence key : value) {
            Optional<? extends List<V>> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                objectEncoder.encodeKey(fieldName);
                List<V> list = opt.get();
                if (list.size() == 1 && (list.get(0).getClass() != JsonError.class || !alwaysSerializeErrorsAsList)) {
                    valueSerializer.serialize(objectEncoder, list.get(0));
                } else {
                    listSerializer.serialize(objectEncoder, list);
                }
            }
        }
        objectEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(OptionalMultiValues<V> value) {
        return value.isEmpty();
    }

    @Singleton
    static class Factory implements Serializer.Factory {
        private final JsonConfiguration jacksonConfiguration;

        @Inject
        Factory(JsonConfiguration jacksonConfiguration) {
            this.jacksonConfiguration = jacksonConfiguration;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Argument<OptionalMultiValues> getGenericType() {
            return Argument.of(
                    OptionalMultiValues.class,
                    Argument.ofTypeVariable(Object.class, "T")
            );
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Serializer<? super OptionalMultiValues<?>> newInstance(SerdeRegistry locator, Function<String, Type> getTypeParameter) {
            return new OptionalMultiValuesSerializer(
                    jacksonConfiguration,
                    locator.findContravariantSerializer(getTypeParameter.apply("V")),
                    locator.findContravariantSerializer(GenericTypeFactory.makeParameterizedTypeWithOwner(null, List.class, getTypeParameter.apply("V"))));
        }
    }
}
