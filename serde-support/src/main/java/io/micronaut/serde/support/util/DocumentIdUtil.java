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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.support.reference.DocumentIdReference;
import io.micronaut.serde.support.reference.DocumentIdSerializationReference;
import io.micronaut.serde.support.reference.PendingDocumentIdReference;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Support for document-scoped identifiers: JAXB {@code @XmlID} and Jackson {@code @JsonIdentityInfo} properties
 * share the {@link SerdeConfig.SerManagedRef.Scope#DOCUMENT} marker, one registry per document and the
 * same reference serdes.
 *
 * @since 3.2
 */
@Internal
public final class DocumentIdUtil {

    private DocumentIdUtil() {
    }

    /**
     * Checks whether a property is the document-scoped identifier of its bean.
     *
     * @param annotationMetadata The annotation metadata of a property
     * @return Whether the property is the document-scoped identifier of its bean
     */
    public static boolean isDocumentId(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.enumValue(SerdeConfig.SerManagedRef.class, SerdeConfig.SerManagedRef.SCOPE,
            SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT;
    }

    /**
     * Finds the document-scoped identifier property of a bean.
     *
     * @param introspection The bean introspection
     * @param <T> The bean type
     * @return The document-scoped identifier property, or {@code null} if the bean has none
     */
    public static <T> @Nullable BeanProperty<T, Object> findDocumentIdProperty(BeanIntrospection<T> introspection) {
        for (BeanProperty<T, Object> property : introspection.getBeanProperties()) {
            if (isDocumentId(property.getAnnotationMetadata())) {
                return property;
            }
        }
        return null;
    }

    /**
     * Checks whether a document-scoped identifier carries object identity semantics, meaning repeated
     * occurrences of the bean in a document are written and read as the identifier.
     *
     * @param documentIdProperty The document-scoped identifier property
     * @return Whether the identifier carries object identity semantics
     */
    public static boolean hasObjectIdentity(@Nullable BeanProperty<?, ?> documentIdProperty) {
        return documentIdProperty != null
            && documentIdProperty.booleanValue(SerdeConfig.class, SerdeConfig.OBJECT_IDENTITY).orElse(false);
    }

    /**
     * Registers a fully read bean under its identifier for the current document.
     *
     * @param context The decoder context
     * @param id The identifier
     * @param introspection The bean introspection
     * @param idArgument The identifier property
     * @param bean The bean
     * @param <B> The bean type
     */
    public static <B> void register(Deserializer.DecoderContext context,
                                    Object id,
                                    BeanIntrospection<B> introspection,
                                    Argument<Object> idArgument,
                                    B bean) {
        context.pushManagedRef(new DocumentIdReference<>(String.valueOf(id), introspection, idArgument, bean));
    }

    /**
     * Resolves a bean registered in the current document.
     *
     * @param context The decoder context
     * @param id The identifier
     * @param type The expected bean type
     * @return The bean, or {@code null} if no compatible bean was registered under the identifier
     */
    public static @Nullable Object resolve(Deserializer.DecoderContext context, Object id, Argument<?> type) {
        PropertyReference<Object, Object> reference = context.resolveReference(
            new DocumentIdReference<>(String.valueOf(id), null, Argument.OBJECT_ARGUMENT, null));
        Object bean = reference == null ? null : reference.getReference();
        return bean != null && type.getType().isInstance(bean) ? bean : null;
    }

    /**
     * Resolves a bean registered in the current document, or defers the reference until it is registered.
     *
     * @param context The decoder context
     * @param id The identifier
     * @param type The expected bean type
     * @param consumer Receives the bean
     * @throws IOException If the reference cannot be set
     */
    public static void resolveOrDefer(Deserializer.DecoderContext context,
                                      Object id,
                                      Argument<?> type,
                                      PendingDocumentIdReference.DocumentIdConsumer consumer) throws IOException {
        Object bean = resolve(context, id, type);
        if (bean != null) {
            consumer.accept(bean);
        } else {
            context.pushManagedRef(new PendingDocumentIdReference(String.valueOf(id), type, consumer));
        }
    }

    /**
     * Checks whether a bean has already been written in full in the current document.
     *
     * @param context The encoder context
     * @param reference The bean's identifier reference
     * @return {@code true} if the bean was written before and must now be written as its identifier
     */
    public static boolean isWritten(Serializer.EncoderContext context, DocumentIdSerializationReference<?> reference) {
        return context.resolveReference(reference) == null;
    }

    /**
     * Records that a bean has been written in full in the current document.
     *
     * @param context The encoder context
     * @param reference The bean's identifier reference
     */
    public static void markWritten(Serializer.EncoderContext context, DocumentIdSerializationReference<?> reference) {
        context.pushManagedRef(reference);
    }

    /**
     * Resolves the element type of an array or {@link Iterable} reference collection.
     *
     * @param type The array or {@link Iterable} type
     * @param description A description of the failing operation, used in the error message
     * @return The element type
     * @throws SerdeException If the element type is not available
     */
    @SuppressWarnings("unchecked")
    public static Argument<Object> collectionElementType(Argument<?> type, String description) throws SerdeException {
        if (type.isArray()) {
            return (Argument<Object>) Argument.of(type.getType().getComponentType());
        }
        return (Argument<Object>) type.getFirstTypeVariable().orElseThrow(() -> new SerdeException(
            "Cannot " + description + " collection of type [" + type.getType().getName() + "]: no element type available"));
    }
}
