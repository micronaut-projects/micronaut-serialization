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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The customized iterable serializer.
 *
 * @param <T> The iterable type
 * @author Denis Stepanov
 */
@Internal
final class RuntimeValueIterableSerializer<T> extends AbstractIterableSerializer<T> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Iterable<T>> type, Iterable<T> value) throws IOException {
        // slow path, generic look up per element
        final Encoder childEncoder = encoder.encodeArray(type);
        Class<?> lastValueClass = null;
        Element<T> element = null;
        // The element serializers are created with the context of this call, so they are only kept for this call:
        // a serializer may depend on its context (views, features, user serdes created with it).
        // A homogeneous iterable needs only the last element; the map is created on the first change of class.
        Map<Class<?>, Element<T>> elements = null;
        for (T t : value) {
            if (t == null) {
                childEncoder.encodeNull();
                continue;
            }
            Class<?> valueClass = t.getClass();
            if (lastValueClass != valueClass || element == null) {
                if (element != null && elements == null) {
                    elements = new HashMap<>();
                    elements.put(lastValueClass, element);
                }
                lastValueClass = valueClass;
                element = elements == null ? null : elements.get(valueClass);
                if (element == null) {
                    Argument<T> generic = (Argument<T>) Argument.of(valueClass);
                    element = new Element<>(generic, context.findSerializer(generic).createSpecific(context, generic));
                    if (elements != null) {
                        elements.put(valueClass, element);
                    }
                }
            }
            element.serializer.serialize(childEncoder, context, element.argument, t);
        }
        childEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(EncoderContext context, @Nullable Iterable<T> value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        } else {
            return !value.iterator().hasNext();
        }
    }

    @Override
    public boolean isAbsent(EncoderContext context, @Nullable Iterable<T> value) {
        return value == null;
    }

    /**
     * The serializer of the elements of a class and the argument it is called with.
     *
     * @param argument   The argument of the class of the element
     * @param serializer The specific serializer
     * @param <T>        The element type
     */
    private record Element<T>(Argument<T> argument, Serializer<? super T> serializer) {
    }

}
