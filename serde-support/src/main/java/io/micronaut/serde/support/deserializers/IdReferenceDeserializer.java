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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.DocumentIdUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deserializes a property that always references beans by their document-scoped identifier
 * (JAXB {@code @XmlIDREF}, Jackson {@code @JsonIdentityReference(alwaysAsId = true)}): each identifier is resolved
 * against the beans already read in the current document.
 *
 * @since 3.2
 */
@Internal
@Singleton
public final class IdReferenceDeserializer implements Deserializer<Object> {
    private final SerdeIntrospections introspections;
    private final @Nullable Argument<?> referenceType;
    private final @Nullable Argument<Object> idArgument;
    private final @Nullable Deserializer<Object> idDeserializer;
    private final boolean collection;

    /**
     * Creates the unspecialized identifier-reference deserializer.
     *
     * @param introspections The serde introspections
     */
    @Inject
    public IdReferenceDeserializer(SerdeIntrospections introspections) {
        this(introspections, null, null, null, false);
    }

    private IdReferenceDeserializer(SerdeIntrospections introspections,
                                    @Nullable Argument<?> referenceType,
                                    @Nullable Argument<Object> idArgument,
                                    @Nullable Deserializer<Object> idDeserializer,
                                    boolean collection) {
        this.introspections = introspections;
        this.referenceType = referenceType;
        this.idArgument = idArgument;
        this.idDeserializer = idDeserializer;
        this.collection = collection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        boolean isCollection = type.isArray() || Iterable.class.isAssignableFrom(type.getType());
        Argument<?> elementType = isCollection ? DocumentIdUtil.collectionElementType(type, "deserialize identifier reference") : type;
        BeanIntrospection<Object> introspection;
        try {
            introspection = introspections.getDeserializableIntrospection((Argument<Object>) Argument.of(elementType.getType()));
        } catch (IntrospectionException e) {
            throw new SerdeException("Cannot deserialize identifier reference of type [" + elementType.getType().getName() + "]: " + e.getMessage(), e);
        }
        BeanProperty<Object, Object> property = DocumentIdUtil.findDocumentIdProperty(introspection);
        if (property == null) {
            throw new SerdeException("Cannot deserialize identifier reference of type [" + elementType.getType().getName()
                + "]: the type does not declare an identifier property");
        }
        Argument<Object> argument = property.asArgument();
        Deserializer<Object> deserializer = (Deserializer<Object>) context.findDeserializer(argument).createSpecific(context, argument);
        return new IdReferenceDeserializer(introspections, elementType, argument, deserializer, isCollection);
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        if (referenceType == null || idArgument == null || idDeserializer == null) {
            throw new SerdeException("Identifier reference deserializer was not specialized");
        }
        if (!collection) {
            return resolveReference(decoder, context);
        }
        Decoder arrayDecoder = decoder.decodeArray(type);
        List<Object> values = new ArrayList<>();
        while (arrayDecoder.hasNextArrayValue()) {
            values.add(arrayDecoder.decodeNull() ? null : resolveReference(arrayDecoder, context));
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

    @SuppressWarnings("NullAway")
    private Object resolveReference(Decoder decoder, DecoderContext context) throws IOException {
        Object id = idDeserializer.deserialize(decoder, context, idArgument);
        Object value = DocumentIdUtil.resolve(context, id, Argument.OBJECT_ARGUMENT);
        if (value == null) {
            throw new SerdeException("Unable to resolve identifier reference [" + id + "]");
        }
        if (!referenceType.getType().isInstance(value)) {
            throw new SerdeException("Identifier reference [" + id + "] resolved to incompatible type ["
                + value.getClass().getName() + "] for [" + referenceType.getType().getName() + "]");
        }
        return value;
    }
}
