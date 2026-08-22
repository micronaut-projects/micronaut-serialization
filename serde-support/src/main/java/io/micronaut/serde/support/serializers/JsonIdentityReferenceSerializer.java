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

/**
 * Serializes a Jackson object identity reference using its identity property.
 *
 * @param <T> The value type
 * @since 3.2
 */
@Internal
@Singleton
public final class JsonIdentityReferenceSerializer<T> implements Serializer<T> {
    private final @Nullable BeanProperty<Object, Object> idProperty;
    private final @Nullable Serializer<Object> idSerializer;
    private final @Nullable Serializer<Object> objectSerializer;
    private final boolean alwaysAsId;

    /**
     * Creates an identity-reference serializer.
     */
    public JsonIdentityReferenceSerializer() {
        idProperty = null;
        idSerializer = null;
        objectSerializer = null;
        alwaysAsId = false;
    }

    private JsonIdentityReferenceSerializer(BeanProperty<Object, Object> idProperty,
                                            Serializer<Object> idSerializer,
                                            Serializer<Object> objectSerializer,
                                            boolean alwaysAsId) {
        this.idProperty = idProperty;
        this.idSerializer = idSerializer;
        this.objectSerializer = objectSerializer;
        this.alwaysAsId = alwaysAsId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
        BeanIntrospection<Object> introspection = (BeanIntrospection<Object>) BeanIntrospector.SHARED.findIntrospection(type.getType())
            .orElseThrow(() -> new SerdeException("Cannot serialize JSON object identity reference of type [" + type.getType().getName() + "]: no bean introspection available"));
        BeanProperty<Object, Object> property = introspection.getBeanProperties().stream()
            .filter(candidate -> candidate.enumValue(SerdeConfig.SerManagedRef.class, SerdeConfig.SerManagedRef.SCOPE,
                SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT)
            .findFirst()
            .orElseThrow(() -> new SerdeException("Cannot serialize JSON object identity reference of type [" + type.getType().getName() + "]: no identity property found"));
        Serializer<Object> serializer = (Serializer<Object>) context.findSerializer(property.asArgument());
        Serializer<Object> fallbackSerializer = (Serializer<Object>) context.findSerializer(Argument.of(type.getType()));
        boolean asId = type.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.JSON_IDENTITY_REFERENCE).orElse(false);
        return new JsonIdentityReferenceSerializer<>(property, serializer, fallbackSerializer, asId);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (idProperty == null || idSerializer == null || objectSerializer == null) {
            throw new SerdeException("JSON object identity reference serializer was not specialized");
        }
        Object id = idProperty.get(value);
        if (id == null) {
            throw new SerdeException("Cannot serialize JSON object identity reference of type [" + value.getClass().getName() + "]: identity property is null");
        }
        if (alwaysAsId || context.resolveObjectId(value) != null) {
            idSerializer.serialize(encoder, context, idProperty.asArgument(), id);
            return;
        }
        context.registerObjectId(value, id);
        objectSerializer.serialize(encoder, context, Argument.of(value.getClass()), value);
    }
}
