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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.DocumentIdUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Serializes a property that always references beans by their document-scoped identifier
 * (JAXB {@code @XmlIDREF}, Jackson {@code @JsonIdentityReference(alwaysAsId = true)}): the referenced bean,
 * or each element of a referenced collection, is written as its identifier.
 *
 * @param <T> The value type
 * @since 3.2
 */
@Internal
@Singleton
public final class IdReferenceSerializer<T> implements Serializer<T> {
    private final SerdeIntrospections introspections;
    private final @Nullable BeanProperty<Object, Object> idProperty;
    private final @Nullable Argument<Object> idArgument;
    private final @Nullable Serializer<Object> idSerializer;
    private final boolean collection;

    /**
     * Creates the unspecialized identifier-reference serializer.
     *
     * @param introspections The serde introspections
     */
    @Inject
    public IdReferenceSerializer(SerdeIntrospections introspections) {
        this(introspections, null, null, null, false);
    }

    private IdReferenceSerializer(SerdeIntrospections introspections,
                                  @Nullable BeanProperty<Object, Object> idProperty,
                                  @Nullable Argument<Object> idArgument,
                                  @Nullable Serializer<Object> idSerializer,
                                  boolean collection) {
        this.introspections = introspections;
        this.idProperty = idProperty;
        this.idArgument = idArgument;
        this.idSerializer = idSerializer;
        this.collection = collection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
        boolean isCollection = type.isArray() || Iterable.class.isAssignableFrom(type.getType());
        Argument<?> referenceType = isCollection ? DocumentIdUtil.collectionElementType(type, "serialize identifier reference") : type;
        BeanIntrospection<Object> introspection;
        try {
            introspection = introspections.getSerializableIntrospection((Argument<Object>) Argument.of(referenceType.getType()));
        } catch (IntrospectionException e) {
            throw new SerdeException("Cannot serialize identifier reference of type [" + referenceType.getType().getName() + "]: " + e.getMessage(), e);
        }
        BeanProperty<Object, Object> property = DocumentIdUtil.findDocumentIdProperty(introspection);
        if (property == null) {
            throw new SerdeException("Cannot serialize identifier reference of type [" + referenceType.getType().getName()
                + "]: the type does not declare an identifier property");
        }
        Argument<Object> argument = property.asArgument();
        Serializer<Object> serializer = (Serializer<Object>) context.findSerializer(argument).createSpecific(context, argument);
        return new IdReferenceSerializer<>(introspections, property, argument, serializer, isCollection);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (idProperty == null || idArgument == null || idSerializer == null) {
            throw new SerdeException("Identifier reference serializer was not specialized");
        }
        if (!collection) {
            serializeReference(encoder, context, value);
            return;
        }
        Encoder arrayEncoder = encoder.encodeArray(type);
        if (value instanceof Iterable<?> values) {
            for (Object reference : values) {
                serializeReference(arrayEncoder, context, reference);
            }
        } else if (value instanceof Object[] values) {
            for (Object reference : values) {
                serializeReference(arrayEncoder, context, reference);
            }
        } else {
            throw new SerdeException("Cannot serialize identifier references of type [" + value.getClass().getName() + "]");
        }
        arrayEncoder.finishStructure();
    }

    @SuppressWarnings("NullAway")
    private void serializeReference(Encoder encoder, EncoderContext context, @Nullable Object value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
            return;
        }
        Object id = idProperty.get(value);
        if (id == null) {
            throw new SerdeException("Cannot serialize identifier reference of type [" + value.getClass().getName() + "]: identifier property is null");
        }
        idSerializer.serialize(encoder, context, idArgument, id);
    }
}
