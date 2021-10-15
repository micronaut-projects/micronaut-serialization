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
import io.micronaut.core.value.OptionalValues;
import io.micronaut.json.ArgumentResolver;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerdeRegistry;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;

class OptionalValuesSerializer<V> implements Serializer<OptionalValues<V>> {
    private final Serializer<? super V> valueSerializer;

    public OptionalValuesSerializer(Serializer<? super V> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Override
    public void serialize(Encoder encoder, OptionalValues<V> value) throws IOException {
        Encoder objectEncoder = encoder.encodeObject();
        for (CharSequence key : value) {
            Optional<V> opt = value.get(key);
            if (opt.isPresent()) {
                objectEncoder.encodeKey(key.toString());
                valueSerializer.serialize(objectEncoder, opt.get());
            }
        }
        objectEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(OptionalValues<V> value) {
        return value.isEmpty();
    }

    @Singleton
    static class Factory implements Serializer.Factory {
        @SuppressWarnings("rawtypes")
        @Override
        public Argument<OptionalValues> getGenericType() {
            return Argument.of(
                    OptionalValues.class,
                    Argument.ofTypeVariable(Object.class, "V")
            );
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Serializer<? super OptionalValues<?>> newInstance(SerdeRegistry locator, ArgumentResolver getTypeParameter) {
            return new OptionalValuesSerializer(locator.findSerializer(getTypeParameter.apply("V")));
        }
    }
}
