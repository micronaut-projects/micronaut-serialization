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

/** Serializes a JAXB {@code @XmlIDREF} value using its declared {@code @XmlID}. */
@Internal
@Singleton
public final class XmlIdRefSerializer<T> implements Serializer<T> {
    private final @Nullable BeanProperty<T, Object> idProperty;

    /**
     * Creates an XML ID-reference serializer.
     */
    public XmlIdRefSerializer() {
        this.idProperty = null;
    }

    private XmlIdRefSerializer(BeanProperty<T, Object> idProperty) {
        this.idProperty = idProperty;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
        BeanIntrospection<T> introspection = (BeanIntrospection<T>) BeanIntrospector.SHARED.findIntrospection(type.getType())
            .orElseThrow(() -> new SerdeException("Cannot serialize XmlIDREF value of type [" + type.getType().getName() + "]: no bean introspection available"));
        BeanProperty<T, Object> xmlIdProperty = introspection.getBeanProperties().stream()
            .filter(property -> property.booleanValue(SerdeConfig.class, SerdeConfig.XML_ID).orElse(false))
            .findFirst()
            .orElseThrow(() -> new SerdeException("Cannot serialize XmlIDREF value of type [" + type.getType().getName() + "]: no XmlID property found"));
        return new XmlIdRefSerializer<>(xmlIdProperty);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        BeanProperty<T, Object> xmlIdProperty = idProperty;
        if (xmlIdProperty == null) {
            throw new SerdeException("XmlIDREF serializer was not specialized");
        }
        Object id = xmlIdProperty.get(value);
        if (!(id instanceof String stringId)) {
            throw new SerdeException("Cannot serialize XmlIDREF value of type [" + value.getClass().getName() + "]: XmlID must be a String");
        }
        encoder.encodeString(stringId);
    }
}
