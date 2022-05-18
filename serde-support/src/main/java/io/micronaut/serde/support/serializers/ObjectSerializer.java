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
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
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
import java.util.function.Supplier;

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
public final class ObjectSerializer implements CustomizableSerializer<Object> {
    private final SerdeIntrospections introspections;
    private final SerializationConfiguration configuration;
    private final Map<TypeKey, Supplier<SerBean<Object>>> serBeanMap = new ConcurrentHashMap<>(50);

    public ObjectSerializer(SerdeIntrospections introspections, SerializationConfiguration configuration) {
        this.introspections = introspections;
        this.configuration = configuration;
    }

    @Override
    public Serializer<Object> createSpecific(EncoderContext encoderContext, Argument<? extends Object> type) {
        if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
            // dynamic type resolving
            return new RuntimeTypeSerializer(encoderContext);
        } else {

            SerBean<Object> serBean;
            try {
                serBean = getSerBean(type, encoderContext).get();
            } catch (IntrospectionException e) {
                return createRuntimeSerializer(encoderContext, type, e);
            }

            final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
            if (serBean.hasJsonValue()) {
                return new Serializer<Object>() {
                    @Override
                    public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value)
                            throws IOException {
                        serBean.initialize(context);
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
                        try {
                            serBean.initialize(context);
                        } catch (SerdeException e) {
                            throw new RuntimeException(e);
                        }
                        return serBean.jsonValue.serializer.isEmpty(context, serBean.jsonValue.get(value));
                    }

                    @Override
                    public boolean isAbsent(EncoderContext context, Object value) {
                        try {
                            serBean.initialize(context);
                        } catch (SerdeException e) {
                            throw new RuntimeException(e);
                        }
                        return serBean.jsonValue.serializer.isAbsent(context, serBean.jsonValue.get(value));
                    }
                };
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
                    return new CustomizedObjectSerializer<Object>(serBean) {
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
            if (serBean.simpleBean) {
                return new SimpleObjectSerializer<>(serBean);
            } else if (serBean.subtyped) {
                return new RuntimeTypeSerializer(encoderContext);        
            }
            return new CustomizedObjectSerializer<>(serBean);
        }
    }

    private Supplier<SerBean<Object>> getSerBean(Argument<? extends Object> type, Serializer.EncoderContext context) {
        TypeKey key = new TypeKey(type);
        Supplier<SerBean<Object>> serBeanSupplier = serBeanMap.get(key);
        if (serBeanSupplier == null) {
            serBeanSupplier = SupplierUtil.memoized(() -> create(type, context));
            serBeanMap.put(key, serBeanSupplier);
        }
        return serBeanSupplier;
    }

    @SuppressWarnings("unchecked")
    private SerBean<Object> create(Argument<? extends Object> type, EncoderContext encoderContext) {
        try {
            return new SerBean<>((Argument<Object>) type, introspections, encoderContext, configuration);
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }

    private Serializer<Object> createRuntimeSerializer(EncoderContext encoderContext, Argument<? extends Object> type, IntrospectionException e) {
        // no introspection, create dynamic serialization case
        return new RuntimeTypeSerializer(encoderContext) {
            
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
        private Serializer<Object> inner;

        public RuntimeTypeSerializer(EncoderContext encoderContext) {
            this.encoderContext = encoderContext;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value)
                throws IOException {
            if (value == null) {
                encoder.encodeNull();
            } else {
                Argument<Object> arg = Argument.of((Class) value.getClass());
                getSerializer(context, value).serialize(encoder, context, arg, value);
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
            if (inner == null) {
                inner = tryToFindSerializer(context, value);
            }
            return inner;
        }

        protected Serializer<Object> tryToFindSerializer(EncoderContext context, Object value) throws SerdeException {
            Argument<Object> arg = Argument.of((Class) value.getClass());
            return encoderContext.findSerializer(arg).createSpecific(context, arg);
        }
    }
}
