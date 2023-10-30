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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.BeanDefKey;
import io.micronaut.serde.util.CustomizableSerializer;
import jakarta.inject.Singleton;

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
@BootstrapContextCompatible
public final class ObjectSerializer implements CustomizableSerializer<Object> {
    private final SerdeIntrospections introspections;
    private final SerializationConfiguration configuration;
    private final BeanContext beanContext;
    private final Map<BeanDefKey, Supplier<SerBean<?>>> serBeanMap = new ConcurrentHashMap<>(50);

    public ObjectSerializer(SerdeIntrospections introspections, SerializationConfiguration configuration, BeanContext beanContext) {
        this.introspections = introspections;
        this.configuration = configuration;
        this.beanContext = beanContext;
    }

    @Override
    public io.micronaut.serde.ObjectSerializer<Object> createSpecific(@NonNull EncoderContext encoderContext, Argument<?> type) throws SerdeException {
        boolean isObjectType = type.equalsType(Argument.OBJECT_ARGUMENT);
        if (isObjectType || type instanceof GenericPlaceholder) {
            // dynamic type resolving
            Serializer<Object> outer = !isObjectType ? createSpecificInternal(encoderContext, null, null, type) : null;
            return new RuntimeTypeSerializer(encoderContext, outer, type);
        } else {
            return createSpecificInternal(encoderContext, null, null, type);
        }
    }

    io.micronaut.serde.ObjectSerializer<Object> createSpecificUnwrapped(@NonNull EncoderContext encoderContext,
                                                                        Argument<?> type,
                                                                        @Nullable String namePrefix,
                                                                        @Nullable String nameSuffix) throws SerdeException {
        boolean isObjectType = type.equalsType(Argument.OBJECT_ARGUMENT);
        if (isObjectType || type instanceof GenericPlaceholder) {
            // dynamic type resolving
            Serializer<Object> outer = !isObjectType ? createSpecificInternal(encoderContext, namePrefix, namePrefix, type) : null;
            return new RuntimeTypeSerializer(encoderContext, outer, type);
        } else {
            return createSpecificInternal(encoderContext, namePrefix, nameSuffix, type);
        }
    }

    private io.micronaut.serde.ObjectSerializer<Object> createSpecificInternal(EncoderContext encoderContext,
                                                                               @Nullable String namePrefix,
                                                                               @Nullable String nameSuffix,
                                                                               Argument<?> type) throws SerdeException {
        SerBean<Object> serBean;
        try {
            serBean = (SerBean<Object>) getSerializableBean(type, namePrefix, nameSuffix, encoderContext);
        } catch (IntrospectionException e) {
            // no introspection, create dynamic serialization case
            return new RuntimeTypeSerializer(encoderContext, e, type);
        }

        final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        if (serBean.hasJsonValue()) {
            return new JsonValueSerializer<>(serBean.jsonValue);
        }
        if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerIgnored.class) || annotationMetadata.isAnnotationPresent(
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
        io.micronaut.serde.ObjectSerializer<Object> outer;
        if (serBean.simpleBean) {
            outer = new SimpleObjectSerializer<>(serBean);
        } else {
            outer = new CustomizedObjectSerializer<>(serBean);
        }
        if (serBean.subtyped) {
            outer = new RuntimeTypeSerializer(encoderContext, outer, type);
        }
        if (serBean.wrapperProperty != null) {
            outer = new WrappedObjectSerializer<>(outer, serBean.wrapperProperty);
        }
        return outer;
    }

    private static CustomizedObjectSerializer<Object> createIgnoringCustomObjectSerializer(SerBean<Object> serBean, List<String> order, boolean hasIgnored, boolean hasIncluded, Set<String> ignoreSet, Set<String> includedSet) {
        final List<SerBean.SerProperty<Object, Object>> writeProperties = new ArrayList<>(serBean.writeProperties);
        if (!order.isEmpty()) {
            writeProperties.sort(Comparator.comparingInt(o -> order.indexOf(o.name)));
        }
        if (hasIgnored) {
            writeProperties.removeIf(p -> ignoreSet.contains(p.name));
        }
        if (hasIncluded) {
            writeProperties.removeIf(p -> !includedSet.contains(p.name));
        }
        return new CustomizedObjectSerializer<>(serBean, writeProperties);
    }

    private <T> SerBean<T> getSerializableBean(Argument<T> type, @Nullable String namePrefix, @Nullable String nameSuffix, EncoderContext context) throws SerdeException {
        BeanDefKey key = new BeanDefKey(type, namePrefix, nameSuffix);
        // Use suppliers to prevent recursive update because the lambda will can call the same method again
        Supplier<SerBean<?>> serBeanSupplier = serBeanMap.computeIfAbsent(key, ignore -> SupplierUtil.memoizedNonEmpty(() -> {
            try {
                return new SerBean<>((Argument<Object>) type, introspections, context, configuration, namePrefix, nameSuffix, beanContext);
            } catch (SerdeException e) {
                throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
            }
        }));
        SerBean<?> serBean = serBeanSupplier.get();
        serBean.initialize(context);
        return (SerBean<T>) serBean;
    }
}
