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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

/**
 * Common helpers for POJO-like shape handling.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public final class ObjectShapeSerdeHelper {

    private ObjectShapeSerdeHelper() {
    }

    /**
     * Select the generic object serializer for POJO-like shape handling.
     *
     * @param context The encoder context
     * @param type The type
     * @param <T> The serialized type
     * @return The object serializer
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Serializer<T> objectSerializer(Serializer.EncoderContext context,
                                                     Argument<? extends T> type) throws SerdeException {
        Serializer<T> objectSerializer = (Serializer<T>) context.findSerializer((Argument) Argument.OBJECT_ARGUMENT);
        return objectSerializer.createSpecific(context, type);
    }

    /**
     * Select the generic object deserializer for POJO-like shape handling.
     *
     * @param context The decoder context
     * @param type The type
     * @param <T> The deserialized type
     * @return The object deserializer
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Deserializer<T> objectDeserializer(Deserializer.DecoderContext context,
                                                         Argument<? super T> type) throws SerdeException {
        Deserializer<T> objectDeserializer = (Deserializer<T>) context.findDeserializer((Argument) Argument.OBJECT_ARGUMENT);
        return objectDeserializer.createSpecific(context, type);
    }

    /**
     * Select the generic object deserializer only when it supports updating this type.
     *
     * <p>This is used as a merge fallback for generated deserializers that are replace-only. If the type is not
     * object-shaped, the generic object deserializer may not be able to create a specific deserializer. In that case
     * there is no updating fallback and callers should keep replacement semantics.</p>
     *
     * @param context The decoder context
     * @param type The type
     * @param <T> The deserialized type
     * @return The updating object deserializer, or {@code null} if the type cannot be updated through the object path
     */
    public static <T> @Nullable UpdatingDeserializer<T> updatingObjectDeserializer(Deserializer.DecoderContext context,
                                                                                   Argument<? super T> type) throws SerdeException {
        try {
            Deserializer<T> deserializer = objectDeserializer(context, type);
            return deserializer instanceof UpdatingDeserializer<?> ? (UpdatingDeserializer<T>) deserializer : null;
        } catch (IntrospectionException e) {
            return null;
        }
    }
}
