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
package io.micronaut.serde;

import java.util.Collection;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Default implementation of {@link io.micronaut.serde.Deserializer.DecoderContext}.
 *
 * @since 1.0.0
 */
@Internal
class DefaultDecoderContext implements Deserializer.DecoderContext {
    private final SerdeRegistry registry;

    DefaultDecoderContext(SerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public final <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass)
            throws SerdeException {
        return registry.findCustomDeserializer(deserializerClass);
    }

    @Override
    public final <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        return registry.findDeserializer(type);
    }

    @Override
    public final <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return registry.getDeserializableSubtypes(superType);
    }
}
