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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Fallback {@link Serializer} for general {@link Object} values. For deserialization, deserializes to
 * standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link List}.
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link io.micronaut.json.JsonMapper#writeValue}{@code (gen, }{@link Object}{@code
 *     .class)}</li>
 * </ul>
 *
 * @param <T> The type to serialize
 */
@Internal
final class CustomizedObjectSerializer<T> implements ObjectSerializer<T> {
    private final SerBean<T> serBean;
    private final List<SerBean.SerProperty<T, Object>> writeProperties;

    CustomizedObjectSerializer(SerBean<T> serBean) {
        this(serBean, serBean.writeProperties);
    }

    public CustomizedObjectSerializer(SerBean<T> serBean, List<SerBean.SerProperty<T, Object>> writeProperties) {
        this.serBean = serBean;
        this.writeProperties = writeProperties;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try {
            Encoder childEncoder = encoder.encodeObject(type);
            serializeIntoInternal(childEncoder, value, context);
            childEncoder.finishStructure();
        } catch (StackOverflowError e) {
            throw new SerdeException("Infinite recursion serializing type: " + type.getType()
                    .getSimpleName() + " at path " + encoder.currentPath(), e);
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.currentPath() + ". No serializer found for "
                                             + "type: " + type, e);
        }
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try {
            serializeIntoInternal(encoder, value, context);
        } catch (StackOverflowError e) {
            throw new SerdeException("Infinite recursion serializing type: " + type.getType()
                .getSimpleName() + " at path " + encoder.currentPath(), e);
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.currentPath() + ". No serializer found for "
                + "type: " + type, e);
        }
    }

    private void serializeIntoInternal(Encoder objectEncoder, T objectValue, EncoderContext context) throws IOException {
        for (SerBean.SerProperty<T, Object> property : writeProperties) {
            final Object propertyValue = property.get(objectValue);
            final String backRef = property.backRef;
            if (backRef != null) {
                final PropertyReference<T, Object> ref = context.resolveReference(
                        new SerializationReference<>(backRef,
                                                     serBean.introspection,
                                                     property.argument,
                                                     propertyValue,
                                                     property.serializer)
                );
                if (ref == null) {
                    continue;
                }
            }

            final Serializer<Object> serializer = property.serializer;

            if (serBean.propertyFilter != null) {
                if (!serBean.propertyFilter.shouldInclude(context, serializer, objectValue, property.name, propertyValue)) {
                    continue;
                }
            } else {
                switch (property.include) {
                    case NON_NULL:
                        if (propertyValue == null) {
                            continue;
                        }
                        break;
                    case NON_ABSENT:
                        if (serializer.isAbsent(context, propertyValue)) {
                            continue;
                        }
                        break;
                    case NON_EMPTY:
                        if (serializer.isEmpty(context, propertyValue)) {
                            continue;
                        }
                        break;
                    case NEVER:
                        continue;
                    default:
                        // fall through
                }
            }

            if (property.views != null && !context.hasView(property.views)) {
                continue;
            }

            final String managedRef = property.managedRef;
            if (managedRef != null) {
                context.pushManagedRef(
                        new SerializationReference<>(
                                managedRef,
                                serBean.introspection,
                                property.argument,
                            objectValue,
                                property.serializer
                        )
                );
            }
            try {
                if (property.unwrapped) {
                    if (property.objectSerializer != null) {
                        property.objectSerializer.serializeInto(objectEncoder, context, property.argument, propertyValue);
                    } else {
                        throw new SerdeException("Serializer for a property: " + property.name + " doesn't support serializing into an existing object");
                    }
                } else {
                    objectEncoder.encodeKey(property.name);
                    if (propertyValue == null) {
                        objectEncoder.encodeNull();
                    } else {
                        serializer.serialize(objectEncoder, context, property.argument, propertyValue);
                    }
                }
            } finally {
                if (managedRef != null) {
                    context.popManagedRef();
                }
            }
        }
        final SerBean.SerProperty<T, Object> anyGetter = serBean.anyGetter;
        if (anyGetter != null) {
            final Object data = anyGetter.get(objectValue);
            if (data instanceof Map<?, ?> map) {
                if (CollectionUtils.isNotEmpty(map)) {
                    for (Object k : map.keySet()) {
                        final Object v = map.get(k);
                        objectEncoder.encodeKey(k.toString());
                        if (v == null) {
                            objectEncoder.encodeNull();
                        } else {
                            Argument<?> valueType = anyGetter.argument.getTypeVariable("V")
                                    .orElse(null);
                            if (valueType == null || valueType.equalsType(Argument.OBJECT_ARGUMENT)) {
                                valueType = Argument.of(v.getClass());
                            }
                            @SuppressWarnings("unchecked")
                            Serializer<Object> foundSerializer = (Serializer<Object>) context.findSerializer(valueType);
                            final Serializer<Object> serializer = foundSerializer.createSpecific(context, valueType);
                            serializer.serialize(objectEncoder, context, valueType, v);
                        }
                    }
                }
            }
        }
    }

}
