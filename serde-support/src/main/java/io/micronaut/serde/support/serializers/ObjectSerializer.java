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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.TypeKey;
import io.micronaut.serde.util.CustomizableSerializer;
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
@BootstrapContextCompatible
public final class ObjectSerializer implements CustomizableSerializer<Object> {
    private final SerdeIntrospections introspections;
    private final SerializationConfiguration configuration;
    private final BeanContext beanContext;
    private final Map<TypeKey, SerBean<Object>> serBeanMap = new ConcurrentHashMap<>(50);

    public ObjectSerializer(SerdeIntrospections introspections, SerializationConfiguration configuration, BeanContext beanContext) {
        this.introspections = introspections;
        this.configuration = configuration;
        this.beanContext = beanContext;
    }

    @Override
    public Serializer<Object> createSpecific(@NonNull EncoderContext encoderContext, Argument<?> type) throws SerdeException {
        boolean isObjectType = type.equalsType(Argument.OBJECT_ARGUMENT);
        if (isObjectType || type instanceof GenericPlaceholder) {
            // dynamic type resolving
            Serializer<Object> outer = !isObjectType ? createSpecificInternal(encoderContext, type) : null;
            return new RuntimeTypeSerializer(encoderContext, outer);
        } else {
            return createSpecificInternal(encoderContext, type);
        }
    }

    private Serializer<Object> createSpecificInternal(EncoderContext encoderContext, Argument<?> type) throws SerdeException {
        SerBean<Object> serBean;
        try {
            serBean = getSerBean(type, encoderContext);
        } catch (IntrospectionException e) {
            return createRuntimeSerializer(encoderContext, type, e);
        }

        final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        if (serBean.hasJsonValue()) {
            return createJsonValueSerializer(serBean);
        } else if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerIgnored.class) || annotationMetadata.isAnnotationPresent(
                SerdeConfig.META_ANNOTATION_PROPERTY_ORDER) || annotationMetadata.isAnnotationPresent(SerdeConfig.SerIncluded.class)) {
            final String[] ignored = annotationMetadata.stringValues(SerdeConfig.SerIgnored.class);
            final String[] included = annotationMetadata.stringValues(SerdeConfig.SerIncluded.class);
            List<String> order = Arrays.asList(annotationMetadata.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER));
            final boolean hasIgnored = ArrayUtils.isNotEmpty(ignored);
            final boolean hasIncluded = ArrayUtils.isNotEmpty(included);
            Set<String> ignoreSet = hasIgnored ? CollectionUtils.setOf(ignored) : null;
            Set<String> includedSet = hasIncluded ? CollectionUtils.setOf(included) : null;
            if (!order.isEmpty() || hasIgnored || hasIncluded) {
                return createIgnoringCustomObjectSerializer(serBean, order, hasIgnored, hasIncluded, ignoreSet, includedSet);
            }
        }
        Serializer<Object> outer;
        if (serBean.simpleBean) {
            outer = new SimpleObjectSerializer<>(serBean);
        } else {
            outer = new CustomizedObjectSerializer<>(serBean);
        }

        if (serBean.subtyped) {
            return createSubTypedObjectSerializer(encoderContext, type, outer);
        } else {
            return outer;
        }
    }

    private static RuntimeTypeSerializer createSubTypedObjectSerializer(EncoderContext encoderContext, Argument<?> type, Serializer<Object> outer) {
        return new RuntimeTypeSerializer(encoderContext, outer) {
            @Override
            protected Serializer<Object> tryToFindSerializer(EncoderContext context, Object value) throws SerdeException {
                if (value.getClass().equals(type.getType())) {
                    return outer;
                } else {
                    return super.tryToFindSerializer(context, value);
                }
            }

        };
    }

    private static CustomizedObjectSerializer<Object> createIgnoringCustomObjectSerializer(SerBean<Object> serBean, List<String> order, boolean hasIgnored, boolean hasIncluded, Set<String> ignoreSet, Set<String> includedSet) {
        return new CustomizedObjectSerializer<>(serBean) {
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

    private static Serializer<Object> createJsonValueSerializer(SerBean<Object> serBean) {
        return new Serializer<>() {
            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value)
                throws IOException {
                SerBean.SerProperty<Object, Object> jsonValue = serBean.jsonValue;
                final Object v = jsonValue.get(value);
                serBean.jsonValue.serializer.serialize(
                    encoder,
                    context,
                    jsonValue.argument, v
                );
            }

            @Override
            public boolean isEmpty(EncoderContext context, Object value) {
                return serBean.jsonValue.serializer.isEmpty(context, serBean.jsonValue.get(value));
            }

            @Override
            public boolean isAbsent(EncoderContext context, Object value) {
                return serBean.jsonValue.serializer.isAbsent(context, serBean.jsonValue.get(value));
            }
        };
    }

    private SerBean<Object> getSerBean(Argument<?> type, Serializer.EncoderContext context) throws SerdeException {
        TypeKey key = new TypeKey(type);
        SerBean<Object> serBean = serBeanMap.computeIfAbsent(key, ignore -> create(type, context));
        serBean.initialize(context);
        return serBean;
    }

    @SuppressWarnings("unchecked")
    private SerBean<Object> create(Argument<?> type, EncoderContext encoderContext) {
        try {
            return new SerBean<>((Argument<Object>) type, introspections, encoderContext, configuration, beanContext);
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }

    private Serializer<Object> createRuntimeSerializer(EncoderContext encoderContext, Argument<?> type, IntrospectionException e) {
        // no introspection, create dynamic serialization case
        return new RuntimeTypeSerializer(encoderContext, null) {

            @Override
            protected Serializer<Object> tryToFindSerializer(EncoderContext context, Object value) throws SerdeException {
                final Class<Object> theType = (Class<Object>) value.getClass();
                if (!theType.equals(type.getType())) {
                    return super.tryToFindSerializer(context, value);
                } else {
                    throw new SerdeException(e.getMessage(), e);
                }
            }
        };
    }

    private static class RuntimeTypeSerializer implements Serializer<Object> {
        private final EncoderContext encoderContext;
        private final Map<Class<?>, Serializer<Object>> inners = new ConcurrentHashMap<>(10);
        private final Serializer<Object> outer;

        public RuntimeTypeSerializer(EncoderContext encoderContext, Serializer<Object> outer) {
            this.encoderContext = encoderContext;
            this.outer = outer;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value)
                throws IOException {
            if (value == null) {
                encoder.encodeNull();
            } else {
                Class<?> t = value.getClass();
                if (outer != null && t == type.getType()) {
                    outer.serialize(encoder, context, type, value);
                } else {
                    Argument<?> arg = Argument.of(t);
                    getSerializer(context, value).serialize(encoder, context, arg, value);
                }
            }
        }

        @Override
        public boolean isEmpty(EncoderContext context, Object value) {
            if (value == null) {
                return true;
            }
            try {
                return getSerializer(context, value).isEmpty(context, value);
            } catch (SerdeException e) {
                // will fail later
            }
            return Serializer.super.isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(EncoderContext context, Object value) {
            if (value == null) {
                return true;
            }
            try {
                return getSerializer(context, value).isAbsent(context, value);
            } catch (SerdeException e) {
                // will fail later
            }
            return Serializer.super.isAbsent(context, value);
        }

        private Serializer<Object> getSerializer(EncoderContext context, Object value) throws SerdeException {
            try {
                return inners.computeIfAbsent(value.getClass(), t -> {
                    try {
                        return tryToFindSerializer(context, value);
                    } catch (SerdeException ex) {
                        throw new IntrospectionException("No serializer found for type: " + value.getClass(), ex);
                    }
                });
            } catch (IntrospectionException e) {
                if (e.getCause() instanceof SerdeException serdeException) {
                    throw serdeException;
                } else {
                    throw e;
                }
            }
        }

        protected Serializer<Object> tryToFindSerializer(EncoderContext context, Object value) throws SerdeException {
            Argument<Object> arg = Argument.of((Class) value.getClass());
            return encoderContext.findSerializer(arg).createSpecific(context, arg);
        }
    }
}
