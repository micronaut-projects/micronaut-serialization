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
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.DocumentContextTracker;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The customized iterable serializer.
 *
 * @param <T> The iterable type
 * @author Denis Stepanov
 */
@Internal
final class RuntimeValueIterableSerializer<T> extends AbstractIterableSerializer<T> {

    /**
     * The serializers of the element types, reused by the next documents.
     */
    private final Map<Class<?>, Component<T>> components = new ConcurrentHashMap<>(4);

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Iterable<T>> type, Iterable<T> value) throws IOException {
        // slow path, generic look up per element
        final Encoder childEncoder = encoder.encodeArray(type);
        Component<T> component = null;
        for (T t : value) {
            if (t == null) {
                childEncoder.encodeNull();
                continue;
            }
            if (component == null || component.type().getType() != t.getClass()) {
                component = findComponent(context, t.getClass());
            }
            component.serializer().serialize(childEncoder, context, component.type(), t);
        }
        childEncoder.finishStructure();
    }

    private Component<T> findComponent(EncoderContext context, Class<?> valueClass) throws SerdeException {
        Component<T> component = components.get(valueClass);
        if (component != null) {
            return component;
        }
        Map<Class<?>, Component<T>> documentComponents = (Map<Class<?>, Component<T>>) DocumentContextTracker.getDocumentValue(context, this);
        if (documentComponents != null) {
            component = documentComponents.get(valueClass);
            if (component != null) {
                return component;
            }
        }
        Argument<T> generic = (Argument<T>) Argument.of(valueClass);
        int bindings = DocumentContextTracker.documentBindings(context);
        Serializer<? super T> serializer = context.findSerializer(generic).createSpecific(context, generic);
        component = new Component<>(generic, serializer);
        // This serializer can serve several documents (a mapper caches the serializers that are not bound to a
        // document), so a serializer that the creation bound to the document is kept by the document, which
        // releases it with the values it holds when the document is finished.
        if (bindings >= 0 && DocumentContextTracker.documentBindings(context) == bindings) {
            components.put(valueClass, component);
        } else if (documentComponents != null) {
            documentComponents.put(valueClass, component);
        } else {
            documentComponents = new HashMap<>(4);
            if (DocumentContextTracker.putDocumentValue(context, this, documentComponents)) {
                documentComponents.put(valueClass, component);
            }
        }
        return component;
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
     * The serializer of an element type.
     *
     * @param type       The element type
     * @param serializer The serializer
     * @param <T>        The element type
     */
    private record Component<T>(Argument<T> type, Serializer<? super T> serializer) {
    }
}
