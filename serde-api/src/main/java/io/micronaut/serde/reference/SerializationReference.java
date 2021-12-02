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
package io.micronaut.serde.reference;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serializer;

/**
 * Represents a reference to a property for serialization.
 * @param <B> The bean type
 * @param <T> The property type
 */
@Internal
public class SerializationReference<B, T> extends PropertyReference<B, T> {
    private final Serializer<T> serializer;

    public SerializationReference(String referenceName,
                                  BeanIntrospection<B> introspection,
                                  Argument<T> property,
                                  T value,
                                  Serializer<T> serializer) {
        super(referenceName, introspection, property, value);
        this.serializer = serializer;
    }

    /**
     * @return The serializer
     */
    public @NonNull Serializer<T> getSerializer() {
        return serializer;
    }
}
