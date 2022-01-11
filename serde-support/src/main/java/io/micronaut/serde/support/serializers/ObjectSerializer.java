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

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;
import io.micronaut.serde.support.util.TypeKey;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final SerializationConfiguration configuration;
    private final Map<TypeKey, SerBean<Object>> serBeanMap = new ConcurrentHashMap<>(50);

    public ObjectSerializer(SerdeIntrospections introspections,
                            SerializationConfiguration configuration) {
        this.introspections = introspections;
        this.configuration = configuration;
    }

    @Override
    public Serializer<Object> createSpecific(Argument<?> type, EncoderContext encoderContext) {
        if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
            // dynamic type resolving
            return new Serializer<Object>() {
                Serializer<Object> inner;

                @Override
                public void serialize(Encoder encoder, EncoderContext context, Object value, Argument<?> type)
                        throws IOException {
                    if (value == null) {
                        encoder.encodeNull();
                    }
                    if (inner == null) {
                        inner = (Serializer<Object>) encoderContext.findSerializer(value.getClass());
                    }
                    inner.serialize(
                            encoder,
                            context,
                            value,
                            Argument.of(value.getClass())
                    );
                }

                @Override
                public boolean isEmpty(Object value) {
                    if (value == null) {
                        return true;
                    }
                    if (inner == null) {
                        try {
                            inner = (Serializer<Object>) encoderContext.findSerializer(value.getClass());
                            return inner.isEmpty(value);
                        } catch (SerdeException e) {
                            // will fail later
                        }
                    }
                    return Serializer.super.isEmpty(value);
                }

                @Override
                public boolean isAbsent(Object value) {
                    if (value == null) {
                        return true;
                    }
                    if (inner == null) {
                        try {
                            inner = (Serializer<Object>) encoderContext.findSerializer(value.getClass());
                            return inner.isAbsent(value);
                        } catch (SerdeException e) {
                            // will fail later
                        }
                    }
                    return Serializer.super.isAbsent(value);
                }
            };
        } else {

            final SerBean<Object> serBean;
            try {
                serBean = getSerBean(type, null, encoderContext);
            } catch (IntrospectionException e) {
                // no introspection, create dynamic serialization case
                return (encoder, context, value, type1) -> {
                    final Argument<Object> t = Argument.of(
                            (Class<Object>) value.getClass(),
                            type1.getAnnotationMetadata()
                    );
                    context.findSerializer(t)
                           .createSpecific(t, encoderContext)
                            .serialize(encoder, context, value, t);
                };
            }
            final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
            final SerBean.SerProperty<Object, Object> jsonValue = serBean.jsonValue;
            if (jsonValue != null) {
                final Serializer<Object> serializer = jsonValue.serializer;
                return new Serializer<Object>() {
                    @Override
                    public void serialize(Encoder encoder, EncoderContext context, Object value, Argument<?> type)
                            throws IOException {
                        final Object v = jsonValue.get(value);
                        serializer.serialize(
                                encoder,
                                context,
                                v,
                                jsonValue.argument
                        );
                    }

                    @Override
                    public boolean isEmpty(Object value) {
                        return serializer.isEmpty(jsonValue.get(value));
                    }

                    @Override
                    public boolean isAbsent(Object value) {
                        return serializer.isAbsent(jsonValue.get(value));
                    }
                };
            } else if (annotationMetadata.isAnnotationPresent(SerdeConfig.Ignored.class) || annotationMetadata.isAnnotationPresent(
                    SerdeConfig.PropertyOrder.class) || annotationMetadata.isAnnotationPresent(SerdeConfig.Included.class)) {
                final String[] ignored = annotationMetadata.stringValues(SerdeConfig.Ignored.class);
                final String[] included = annotationMetadata.stringValues(SerdeConfig.Included.class);
                List<String> order = Arrays.asList(annotationMetadata.stringValues(SerdeConfig.PropertyOrder.class));
                final boolean hasIgnored = ArrayUtils.isNotEmpty(ignored);
                final boolean hasIncluded = ArrayUtils.isNotEmpty(included);
                Set<String> ignoreSet = hasIgnored ? CollectionUtils.setOf(ignored) : null;
                Set<String> includedSet = hasIncluded ? CollectionUtils.setOf(included) : null;
                return new ObjectSerializer(introspections, configuration) {
                    @Override
                    protected List<SerBean.SerProperty<Object, Object>> getWriteProperties(SerBean<Object> serBean) {
                        final List<SerBean.SerProperty<Object, Object>> writeProperties =
                                new ArrayList<>(super.getWriteProperties(
                                serBean));
                        if (!order.isEmpty()) {
                            writeProperties.sort(Comparator.comparingInt(o -> order.indexOf(o.name)));
                        }
                        if (hasIgnored) {
                            writeProperties.removeIf(p -> ignoreSet.contains(p.name));
                        }
                        if (hasIncluded) {
                            writeProperties.removeIf(p -> !includedSet.contains(p.name));
                        }
                        return writeProperties;
                    }

                };
            }
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
            final SerBean<Object> serBean = getSerBean(type, value, context);
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
                                v,
                                property.argument
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
                                        v,
                                        valueType
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

    @SuppressWarnings("unchecked")
    private SerBean<Object> getSerBean(Argument<?> type, @Nullable Object value, EncoderContext encoderContext) {
        final TypeKey key = new TypeKey(type);
        SerBean<Object> serBean = serBeanMap.get(key);
        if (serBean == null || !serBean.initialized) {
            synchronized (serBeanMap) {
                try {
                    serBean = serBeanMap.get(key);
                    if (serBean == null) {
                        serBean = new SerBean<>(key, serBeanMap, (Argument<Object>) type, introspections, encoderContext, configuration);
                    }
                } catch (IntrospectionException e) {
                    if (value != null && value.getClass() != type.getClass()) {
                        serBean = getSerBean(Argument.of(value.getClass()), null, encoderContext);
                    } else {
                        throw e;
                    }
                } catch (SerdeException e) {
                    throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
                }
            }
        }
        return serBean;
    }
}
