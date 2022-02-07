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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;
import java.util.Collection;

/**
 * The customized iterable serializer.
 *
 * @param <T> The iterable type
 * @author Denis Stepanov
 */
final class CustomizedIterableSerializer<T> implements Serializer<Iterable<T>> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Iterable<T>> type, Iterable<T> value) throws IOException {
        // slow path, generic look up per element
        final Encoder childEncoder = encoder.encodeArray(type);
        Class<?> lastValueClass = null;
        Serializer<? super T> componentSerializer = null;
        Argument<T> generic = null;
        for (T t : value) {
            if (t == null) {
                encoder.encodeNull();
                continue;
            }
            if (lastValueClass != t.getClass()) {
                generic = (Argument<T>) Argument.of(t.getClass());
                componentSerializer = context.findSerializer(generic).createSpecific(context, generic);
                lastValueClass = t.getClass();
            }
            componentSerializer.serialize(childEncoder, context, generic, t);
        }
    }

    @Override
    public boolean isEmpty(EncoderContext context, Iterable<T> value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection) {
            return ((Collection<T>) value).isEmpty();
        } else {
            return !value.iterator().hasNext();
        }
    }

    @Override
    public boolean isAbsent(EncoderContext context, Iterable<T> value) {
        return value == null;
    }

}
