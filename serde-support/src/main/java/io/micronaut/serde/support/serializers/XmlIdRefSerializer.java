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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Array;

/**
 * Serializes a JAXB {@code @XmlIDREF} value using its declared {@code @XmlID}.
 *
 * @param <T> The value type
 * @since 3.2
 */
@Internal
@Singleton
public final class XmlIdRefSerializer<T> implements Serializer<T> {
    private final @Nullable BeanProperty<Object, Object> idProperty;
    private final boolean collection;

    /**
     * Creates an XML ID-reference serializer.
     */
    public XmlIdRefSerializer() {
        this.idProperty = null;
        this.collection = false;
    }

    private XmlIdRefSerializer(BeanProperty<Object, Object> idProperty, boolean collection) {
        this.idProperty = idProperty;
        this.collection = collection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
        boolean isCollection = type.isArray() || Iterable.class.isAssignableFrom(type.getType());
        Argument<?> referenceType = isCollection ? collectionElementType(type) : type;
        BeanIntrospection<Object> introspection = (BeanIntrospection<Object>) BeanIntrospector.SHARED.findIntrospection(referenceType.getType())
            .orElseThrow(() -> new SerdeException("Cannot serialize XmlIDREF value of type [" + referenceType.getType().getName() + "]: no bean introspection available"));
        BeanProperty<Object, Object> xmlIdProperty = introspection.getBeanProperties().stream()
            .filter(property -> property.enumValue(SerdeConfig.SerManagedRef.class, SerdeConfig.SerManagedRef.SCOPE,
                SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT)
            .findFirst()
            .orElseThrow(() -> new SerdeException("Cannot serialize XmlIDREF value of type [" + referenceType.getType().getName() + "]: no XmlID property found"));
        return new XmlIdRefSerializer<>(xmlIdProperty, isCollection);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        BeanProperty<Object, Object> xmlIdProperty = idProperty;
        if (xmlIdProperty == null) {
            throw new SerdeException("XmlIDREF serializer was not specialized");
        }
        if (collection) {
            Encoder arrayEncoder = encoder.encodeArray(type);
            if (value instanceof Iterable<?> values) {
                for (Object reference : values) {
                    serializeReference(arrayEncoder, xmlIdProperty, reference);
                }
            } else {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    serializeReference(arrayEncoder, xmlIdProperty, Array.get(value, i));
                }
            }
            arrayEncoder.finishStructure();
            return;
        }
        serializeReference(encoder, xmlIdProperty, value);
    }

    private static Argument<?> collectionElementType(Argument<?> type) throws SerdeException {
        if (type.isArray()) {
            return Argument.of(type.getType().getComponentType());
        }
        return type.getFirstTypeVariable().orElseThrow(() -> new SerdeException(
            "Cannot serialize XmlIDREF collection of type [" + type.getType().getName() + "]: no element type available"));
    }

    private static void serializeReference(Encoder encoder, BeanProperty<Object, Object> xmlIdProperty, @Nullable Object value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
            return;
        }
        Object id = xmlIdProperty.get(value);
        if (!(id instanceof String stringId)) {
            throw new SerdeException("Cannot serialize XmlIDREF value of type [" + value.getClass().getName() + "]: XmlID must be a String");
        }
        encoder.encodeString(stringId);
    }
}
