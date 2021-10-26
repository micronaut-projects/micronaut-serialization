/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.serializers;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Fallback {@link io.micronaut.serde.Serializer} for general {@link Object} values. For deserialization, deserializes to standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link List}.
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link io.micronaut.json.JsonMapper#writeValue}{@code (gen, }{@link Object}{@code .class)}</li>
 * </ul>
 */
@Internal
@Singleton
@Primary
public final class ObjectSerializer implements Serializer<Object> {

    @Override
    public void serialize(
            Encoder encoder,
            EncoderContext context,
            Object value,
            Argument<?> type)
            throws IOException {
        try {
            @SuppressWarnings("unchecked")
            final BeanIntrospection<Object> introspection = context
                    .getSerializableIntrospection((Argument<Object>) type);
            final Encoder childEncoder = encoder.encodeObject();
            final Collection<BeanProperty<Object, Object>> properties = introspection.getBeanProperties();
            for (BeanProperty<Object, Object> property : properties) {
                // TODO Move to cache
                if (!property.isWriteOnly() &&
                        !property.booleanValue(SerdeConfig.class, "ignored").orElse(false) &&
                        !property.booleanValue(SerdeConfig.class, "readOnly").orElse(false)) {
                    final Argument<Object> argument = property.asArgument();
                    final Serializer<? super Object> serializer = context.findSerializer(argument);
                    final Object v = property.get(value);
                    final String n = property.stringValue(SerdeConfig.class, "property").orElse(argument.getName());
                    final SerdeConfig.Include include = property.enumValue(SerdeConfig.class, "include", SerdeConfig.Include.class)
                                                                    .orElse(SerdeConfig.Include.ALWAYS);
                    switch (include) {
                        case NON_NULL:
                            if (v == null) {
                                continue;
                            }
                        break;
                        case NON_ABSENT:
                            if (serializer.isAbsent(v)) {
                                continue;
                            }
                        break;
                        case NON_EMPTY:
                            if (serializer.isEmpty(v)) {
                                continue;
                            }
                        break;
                        default:
                            // fall through
                    }
                    childEncoder.encodeKey(n);
                    if (v == null) {
                        childEncoder.encodeNull();
                    } else {
                        serializer.serialize(
                                childEncoder,
                                context,
                                v,
                                argument
                        );
                    }
                }
            }
            childEncoder.finishStructure();
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.toString() + ". No serializer found for type: " + type, e);
        }

    }
}
