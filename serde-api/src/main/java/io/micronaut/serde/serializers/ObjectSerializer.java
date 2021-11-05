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
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Map;

/**
 * Fallback {@link io.micronaut.serde.Serializer} for general {@link Object} values. For deserialization, deserializes to
 * standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link java.util.List}.
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link io.micronaut.json.JsonMapper#writeValue}{@code (gen, }{@link Object}{@code
 *     .class)}</li>
 * </ul>
 */
@Internal
@Singleton
@Primary
public final class ObjectSerializer implements Serializer<Object> {
    private final SerdeIntrospections introspections;

    public ObjectSerializer(SerdeIntrospections introspections) {
        this.introspections = introspections;
    }

    @Override
    public void serialize(
            Encoder encoder,
            EncoderContext context,
            Object value,
            Argument<?> type)
            throws IOException {
        try {
            @SuppressWarnings("unchecked") final SerBean<Object> serBean = getSerBean(type, context);
            Encoder childEncoder = serBean.unwrapped ? encoder : encoder.encodeObject();

            if (serBean.wrapperProperty != null) {
                childEncoder.encodeKey(serBean.wrapperProperty);
                childEncoder = childEncoder.encodeObject();
            }

            for (Map.Entry<String, SerBean.SerProperty<Object, Object>> e : serBean.writeProperties.entrySet()) {
                final String propertyName = e.getKey();
                final SerBean.SerProperty<Object, Object> property = e.getValue();
                final Object v = property.get(value);
                final Serializer<Object> serializer = property.serializer;
                switch (property.include) {
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
                if (!property.unwrapped) {
                    childEncoder.encodeKey(propertyName);
                }
                if (v == null) {
                    childEncoder.encodeNull();
                } else {
                    serializer.serialize(
                            childEncoder,
                            context,
                            v,
                            property.argument
                    );
                }
            }
            final SerBean.SerProperty<Object, Object> anyGetter = serBean.anyGetter;
            if (anyGetter != null) {
                final Object data = anyGetter.get(value);
                if (data instanceof Map) {
                    Map<Object, Object> map = (Map<Object, Object>) data;
                    if (CollectionUtils.isNotEmpty(map)) {

                        for (Object k : map.keySet()) {
                            final Object v = map.get(k);
                            childEncoder.encodeKey(k.toString());
                            if (v == null) {
                                childEncoder.encodeNull();
                            } else {
                                Argument<?> valueType = anyGetter.argument.getTypeVariable("V")
                                        .orElse(null);
                                if (valueType == null || valueType.equalsType(Argument.OBJECT_ARGUMENT)) {
                                    valueType = Argument.of(v.getClass());
                                }
                                final Serializer<Object> serializer = (Serializer<Object>) context.findSerializer(valueType);
                                serializer.serialize(
                                        childEncoder,
                                        context,
                                        v,
                                        valueType
                                );
                            }
                        }
                    }
                }
            }
            if (!serBean.unwrapped) {
                childEncoder.finishStructure();
            }
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.toString() + ". No serializer found for type: " + type, e);
        }

    }

    @SuppressWarnings("unchecked")
    private SerBean<Object> getSerBean(Argument<?> type, EncoderContext encoderContext) {
        // TODO: cache these, the cache key should include the Unwrapped behaviour
        try {
            return new SerBean<>((Argument<Object>) type,
                                 introspections.getSerializableIntrospection((Argument<Object>) type),
                                 encoderContext
            );
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
