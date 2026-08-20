/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a JAXB {@code @XmlIDREF} value against XML IDs read from the current document.
 *
 * @since 3.2
 */
@Internal
@Singleton
public final class XmlIdRefDeserializer implements Deserializer<Object> {

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        if (type.isArray() || Iterable.class.isAssignableFrom(type.getType())) {
            return deserializeCollection(decoder, context, type);
        }
        return resolveReference(decoder.decodeString(), context, type);
    }

    private static Object deserializeCollection(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        Argument<?> referenceType = collectionElementType(type);
        Decoder arrayDecoder = decoder.decodeArray(type);
        List<Object> values = new ArrayList<>();
        while (arrayDecoder.hasNextArrayValue()) {
            values.add(arrayDecoder.decodeNull() ? null : resolveReference(arrayDecoder.decodeString(), context, referenceType));
        }
        arrayDecoder.finishStructure();
        if (type.isArray()) {
            Object array = Array.newInstance(referenceType.getType(), values.size());
            for (int i = 0; i < values.size(); i++) {
                Array.set(array, i, values.get(i));
            }
            return array;
        }
        if (Set.class.isAssignableFrom(type.getType())) {
            return new LinkedHashSet<>(values);
        }
        return values;
    }

    private static Argument<?> collectionElementType(Argument<?> type) throws SerdeException {
        if (type.isArray()) {
            return Argument.of(type.getType().getComponentType());
        }
        return type.getFirstTypeVariable().orElseThrow(() -> new SerdeException(
            "Cannot deserialize XmlIDREF collection of type [" + type.getType().getName() + "]: no element type available"));
    }

    private static Object resolveReference(String id, DecoderContext context, Argument<?> type) throws SerdeException {
        PropertyReference<Object, Object> reference = context.resolveReference(
            new PropertyReference<>(id, null, Argument.of(Object.class, id), null)
        );
        Object value = reference == null ? null : reference.getReference();
        if (value == null) {
            throw new SerdeException("Unable to resolve XmlIDREF [" + id + "]");
        }
        if (!type.getType().isInstance(value)) {
            throw new SerdeException("XmlIDREF [" + id + "] resolved to incompatible type ["
                + value.getClass().getName() + "] for [" + type.getType().getName() + "]");
        }
        return value;
    }
}
