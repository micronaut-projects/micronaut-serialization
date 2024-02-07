/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;
import java.util.Collection;

/**
 * Customized iterable serializer.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
final class CustomizedIterableSerializer<T> implements Serializer<Iterable<T>> {

    private final Argument<T> generic;
    private final Serializer<? super T> componentSerializer;

    CustomizedIterableSerializer(Argument<T> generic, Serializer<? super T> componentSerializer) {
        this.generic = generic;
        this.componentSerializer = componentSerializer;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Iterable<T>> type, Iterable<T> value)
        throws IOException {
        try (Encoder array = encoder.encodeArray(type)) {
            for (T t : value) {
                if (t == null) {
                    array.encodeNull();
                } else {
                    componentSerializer.serialize(array, context, generic, t);
                }
            }
        }
    }

    @Override
    public boolean isEmpty(EncoderContext context, Iterable<T> value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection<T> collection) {
            return collection.isEmpty();
        }
        return !value.iterator().hasNext();
    }

    @Override
    public boolean isAbsent(EncoderContext context, Iterable<T> value) {
        return value == null;
    }
}
