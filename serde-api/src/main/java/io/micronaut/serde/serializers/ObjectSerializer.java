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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class ObjectSerializer implements Serializer<Object> {
    private final SerdeIntrospections introspections;

    public ObjectSerializer(SerdeIntrospections introspections) {
        this.introspections = introspections;
    }

    @Override
    public Serializer<Object> createSpecific(Argument<?> type, EncoderContext encoderContext) {
        final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        if (annotationMetadata.isAnnotationPresent(SerdeConfig.Ignored.class) || annotationMetadata.isAnnotationPresent(
                SerdeConfig.PropertyOrder.class)) {
            final String[] ignored = annotationMetadata.stringValues(SerdeConfig.Ignored.class);
            List<String> order = Arrays.asList(annotationMetadata.stringValues(SerdeConfig.PropertyOrder.class));
            final boolean hasIgnored = ArrayUtils.isNotEmpty(ignored);
            Set<String> ignoreSet = hasIgnored ? CollectionUtils.setOf(ignored) : Collections.emptySet();
            return new ObjectSerializer(introspections) {
                @Override
                protected List<SerBean.SerProperty<Object, Object>> getWriteProperties(SerBean<Object> serBean) {
                    final List<SerBean.SerProperty<Object, Object>> writeProperties = new ArrayList<>(super.getWriteProperties(serBean));
                    if (!order.isEmpty()) {
                        writeProperties.sort(Comparator.comparingInt(o -> order.indexOf(o.name)));
                    }
                    if (hasIgnored) {
                        writeProperties.removeIf(p -> ignoreSet.contains(p.name));
                    }
                    return writeProperties;
                }

            };
        }
        return this;
    }

    @Override
    public final void serialize(
            Encoder encoder,
            EncoderContext context,
            Object value,
            Argument<?> type)
            throws IOException {
        try {
            final SerBean<Object> serBean = getSerBean(type, context);
            final SerBean.SerProperty<Object, Object> jsonValue = serBean.jsonValue;
            if (jsonValue != null) {
                final Object v = jsonValue.get(value);
                if (v == null) {
                    encoder.encodeNull();
                } else {
                    jsonValue.serializer.serialize(
                            encoder,
                            context,
                            v,
                            jsonValue.argument
                    );
                }
            } else {
                Encoder childEncoder = encoder.encodeObject();

                if (serBean.wrapperProperty != null) {
                    childEncoder.encodeKey(serBean.wrapperProperty);
                    childEncoder = childEncoder.encodeObject();
                }

                for (SerBean.SerProperty<Object, Object> property : getWriteProperties(serBean)) {
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
                    case NEVER:
                        continue;
                    default:
                        // fall through
                    }

                    if (property.views != null && !context.hasView(property.views)) {
                        continue;
                    }

                    childEncoder.encodeKey(property.name);
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
                                    @SuppressWarnings("unchecked") final Serializer<Object> serializer =
                                            (Serializer<Object>) context.findSerializer(valueType);
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
                childEncoder.finishStructure();
            }
        } catch (IntrospectionException e) {
            throw new SerdeException("Error serializing value at path: " + encoder + ". No serializer found for type: " + type, e);
        }

    }

    /**
     * Obtains the write properties for this serializer.
     * @param serBean The serialization bean.
     * @return The write properties, never {@code null}
     */
    protected @NonNull List<SerBean.SerProperty<Object, Object>> getWriteProperties(SerBean<Object> serBean) {
        return serBean.writeProperties;
    }

    @SuppressWarnings("unchecked")
    private SerBean<Object> getSerBean(Argument<?> type, EncoderContext encoderContext) {
        // TODO: cache these, the cache key should include the Unwrapped behaviour
        try {
            return new SerBean<>((Argument<Object>) type, introspections, encoderContext);
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
