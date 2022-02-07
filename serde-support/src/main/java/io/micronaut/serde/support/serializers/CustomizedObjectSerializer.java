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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
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
 */
@Internal
public class CustomizedObjectSerializer<T> implements Serializer<T> {
    private final SerBean<Object> serBean;

    public CustomizedObjectSerializer(SerBean<Object> serBean) {
        this.serBean = serBean;
    }

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        try {
            serBean.initialize();
            Encoder childEncoder = encoder.encodeObject(type);

            if (serBean.wrapperProperty != null) {
                childEncoder.encodeKey(serBean.wrapperProperty);
                childEncoder = childEncoder.encodeObject(type);
            }

            for (SerBean.SerProperty<Object, Object> property : getWriteProperties(serBean)) {
                final Object v = property.get(value);
                final String backRef = property.backRef;
                if (backRef != null) {
                    final PropertyReference<Object, Object> ref = context.resolveReference(
                            new SerializationReference<>(backRef,
                                                         serBean.introspection,
                                                         property.argument,
                                                         v,
                                                         property.serializer)
                    );
                    if (ref == null) {
                        continue;
                    }
                }
                final Serializer<Object> serializer = property.serializer;
                switch (property.include) {
                case NON_NULL:
                    if (v == null) {
                        continue;
                    }
                    break;
                case NON_ABSENT:
                    if (serializer.isAbsent(context, v)) {
                        continue;
                    }
                    break;
                case NON_EMPTY:
                    if (serializer.isEmpty(context, v)) {
                        continue;
                    }
                    break;
                case NEVER:
                    continue;
                default:
                    // fall through
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
                                    value,
                                    property.serializer
                            )
                    );
                }
                try {
                    childEncoder.encodeKey(property.name);
                    if (v == null) {
                        childEncoder.encodeNull();
                    } else {
                        serializer.serialize(
                                childEncoder,
                                context,
                                property.argument, v
                        );
                    }
                } finally {
                    if (managedRef != null) {
                        context.popManagedRef();
                    }
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
                                @SuppressWarnings("unchecked") final Serializer<Object> serializer =
                                        (Serializer<Object>) context.findSerializer(valueType);
                                serializer.serialize(
                                        childEncoder,
                                        context,
                                        valueType, v
                                );
                            }
                        }
                    }
                }
            }
            childEncoder.finishStructure();
        } catch (StackOverflowError e) {
            throw new SerdeException("Infinite recursion serializing type: " + type.getType()
                    .getSimpleName() + " at path " + encoder.currentPath(), e);
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder.currentPath() + ". No serializer found for "
                                             + "type: " + type,
                                     e);
        }

    }

    /**
     * Obtains the write properties for this serializer.
     * @param serBean The serialization bean.
     * @return The write properties, never {@code null}
     */
    protected @NonNull
    List<SerBean.SerProperty<Object, Object>> getWriteProperties(SerBean<Object> serBean) {
        return serBean.writeProperties;
    }
}
