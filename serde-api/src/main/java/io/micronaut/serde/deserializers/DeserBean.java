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
package io.micronaut.serde.deserializers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Holder for data about a deserializable bean.
 * @param <T> The generic type
 */
@Internal
class DeserBean<T> {
    // CHECKSTYLE:OFF
    @NonNull
    public final BeanIntrospection<T> introspection;
    @Nullable
    public final Map<String, DerProperty<T, ?>> creatorParams;
    @Nullable
    public final DerProperty<T, ?>[] creatorUnwrapped;
    @Nullable
    public final Map<String, DerProperty<T, Object>> readProperties;
    @Nullable
    public final DerProperty<T, Object>[] unwrappedProperties;
    public final int creatorSize;
    // CHECKSTYLE:ON

    public DeserBean(
            BeanIntrospection<T> introspection,
            Deserializer.DecoderContext decoderContext,
            DeserBeanRegistry deserBeanRegistry)
            throws SerdeException {
        this.introspection = introspection;
        final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        creatorSize = constructorArguments.length;
        final HashMap<String, DerProperty<T, ?>> creatorParams = new HashMap<>(constructorArguments.length);
        List<DerProperty<T, ?>> creatorUnwrapped = null;
        List<DerProperty<T, ?>> unwrappedProperties = null;
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<Object> constructorArgument = (Argument<Object>) constructorArguments[i];
            final AnnotationMetadata annotationMetadata = constructorArgument.getAnnotationMetadata();
            final String jsonProperty = annotationMetadata
                    .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                    .orElse(constructorArgument.getName());
            final Deserializer<?> deserializer = decoderContext.findDeserializer(constructorArgument);
            final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.class);
            if (isUnwrapped) {
                if (creatorUnwrapped == null) {
                    creatorUnwrapped = new ArrayList<>();
                }

                final DeserBean<Object> unwrapped = deserBeanRegistry.getDeserializableBean(
                        constructorArgument,
                        decoderContext
                );
                creatorUnwrapped.add(new DerProperty(
                        introspection.getBeanType(),
                        i,
                        constructorArgument,
                        null,
                        deserializer,
                        unwrapped
                ));
                String prefix = annotationMetadata.stringValue(SerdeConfig.Unwrapped.class, SerdeConfig.Unwrapped.PREFIX).orElse("");
                String suffix = annotationMetadata.stringValue(SerdeConfig.Unwrapped.class, SerdeConfig.Unwrapped.SUFFIX).orElse("");

                final Map<String, DerProperty<Object, ?>> unwrappedCreatorParams = unwrapped.creatorParams;
                if (unwrappedCreatorParams != null) {
                    for (String n : unwrappedCreatorParams.keySet()) {
                        String resolved = prefix + n + suffix;
                        //noinspection unchecked
                        creatorParams.put(resolved, (DerProperty<T, ?>) unwrappedCreatorParams.get(n));
                    }
                }

                creatorParams.put(
                        jsonProperty,
                        new DerProperty<>(
                                introspection.getBeanType(),
                                i,
                                constructorArgument,
                                null,
                                (Deserializer) deserializer,
                                unwrapped
                        )
                );
            } else {
                creatorParams.put(
                        jsonProperty,
                        new DerProperty<>(
                                introspection.getBeanType(),
                                i,
                                constructorArgument,
                                null,
                                (Deserializer) deserializer,
                                null
                        )
                );
            }
        }


        final List<BeanProperty<T, Object>> beanProperties = introspection.getBeanProperties()
                .stream().filter(bp -> {
                    final AnnotationMetadata annotationMetadata = bp.getAnnotationMetadata();
                    return !bp.isReadOnly() &&
                            !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false) &&
                            !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false);
                }).collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(beanProperties)) {
            final HashMap<String, DerProperty<T, Object>> readProps = new HashMap<>(beanProperties.size());
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanProperty<T, Object> beanProperty = beanProperties.get(i);
                final AnnotationMetadata annotationMetadata = beanProperty.getAnnotationMetadata();
                final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.class);
                final Argument<Object> t = beanProperty.asArgument();

                if (isUnwrapped) {
                    if (unwrappedProperties == null) {
                        unwrappedProperties = new ArrayList<>();
                    }
                    final Deserializer<?> deserializer = decoderContext.findDeserializer(t);

                    final DeserBean<Object> unwrapped = deserBeanRegistry.getDeserializableBean(
                            t,
                            decoderContext
                    );
                    unwrappedProperties.add(new DerProperty(
                            beanProperty.getDeclaringType(),
                            i,
                            t,
                            beanProperty,
                            deserializer,
                            unwrapped
                    ));
                    String prefix = annotationMetadata.stringValue(SerdeConfig.Unwrapped.class, SerdeConfig.Unwrapped.PREFIX).orElse("");
                    String suffix = annotationMetadata.stringValue(SerdeConfig.Unwrapped.class, SerdeConfig.Unwrapped.SUFFIX).orElse("");

                    final Map<String, DerProperty<Object, Object>> unwrappedProps = unwrapped.readProperties;
                    if (unwrappedProps != null) {
                        for (String n : unwrappedProps.keySet()) {
                            String resolved = prefix + n + suffix;
                            //noinspection unchecked
                            readProps.put(resolved, (DerProperty<T, Object>) unwrappedProps.get(n));
                        }
                    }
                    final Map<String, DerProperty<Object, ?>> unwrappedCreatorParams = unwrapped.creatorParams;
                    if (unwrappedCreatorParams != null) {
                        for (String n : unwrappedCreatorParams.keySet()) {
                            String resolved = prefix + n + suffix;
                            //noinspection unchecked
                            creatorParams.put(resolved, (DerProperty<T, ?>) unwrappedCreatorParams.get(n));
                        }
                    }
                } else {

                    final String jsonProperty = annotationMetadata
                            .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                            .orElse(beanProperty.getName());

                    final Deserializer<?> deserializer = decoderContext.findDeserializer(t);
                    readProps.put(jsonProperty, new DerProperty<>(
                            beanProperty.getDeclaringType(),
                            i,
                            t,
                            beanProperty,
                            (Deserializer) deserializer,
                            null
                      )
                    );
                }

            }

            this.readProperties = Collections.unmodifiableMap(readProps);
        } else {
            readProperties = null;
        }

        if (creatorParams.isEmpty()) {
            this.creatorParams = null;
        } else {
            this.creatorParams = Collections.unmodifiableMap(creatorParams);
        }
        //noinspection unchecked
        this.creatorUnwrapped = creatorUnwrapped != null ? creatorUnwrapped.toArray(new DerProperty[0]) : null;
        //noinspection unchecked
        this.unwrappedProperties = unwrappedProperties != null ? unwrappedProperties.toArray(new DerProperty[0]) : null;
    }

    /**
     * Models a deserialization property.
     * @param <B> The bean type
     * @param <P> The property type
     */
    @Internal
    // CHECKSTYLE:OFF
    public static final class DerProperty<B, P> {
        public final Class<B> declaringType;
        public final int index;
        public final Argument<P> argument;
        @Nullable
        public final P defaultValue;
        public final boolean required;
        public final @Nullable BeanProperty<B, P> writer;
        public final @NonNull Deserializer<? super P> deserializer;
        public final DeserBean<P> unwrapped;

        public DerProperty(Class<B> declaringType,
                           int index,
                           Argument<P> argument,
                           @Nullable BeanProperty<B, P> writer,
                           @NonNull Deserializer<P> deserializer,
                           @Nullable DeserBean<P> unwrapped) {
            this.declaringType = declaringType;
            this.index = index;
            this.argument = argument;
            this.required = argument.isNonNull();
            this.writer = writer;
            this.deserializer = deserializer;
            // compute default
            this.defaultValue = argument.getAnnotationMetadata()
                    .getValue(Bindable.class, "defaultValue", argument)
                    .orElse(deserializer.getDefaultValue());
            this.unwrapped = unwrapped;
        }

        public void setDefault(@NonNull B bean) throws SerdeException {
            if (defaultValue != null && writer != null) {
                writer.set(bean, defaultValue);
            } else if (isNonNull()) {
                throw new SerdeException("Unable to deserialize type [" + declaringType.getName() + "]. Required property [" + argument +
                                                 "] is not present in supplied data");
            }
        }

        public void setDefault(@NonNull Object[] params) throws SerdeException {
            if (defaultValue != null) {
                params[index] = defaultValue;
            } else if (isNonNull()) {
                throw new SerdeException("Unable to deserialize type [" + declaringType.getName() + "]. Required constructor parameter [" + argument + "] at index [" + index + "] is not present or is null in the supplied data");
            }
        }

        public boolean isNonNull() {
            return argument.isPrimitive() || !argument.isNullable();
        }

        public void set(@NonNull B obj, @Nullable P v) throws SerdeException {
            if (v == null && argument.isNonNull()) {
                throw new SerdeException("Unable to deserialize type [" + declaringType.getName() + "]. Required property [" + argument +
                                                 "] is not present in supplied data");

            }
            if (writer != null) {
                writer.set(obj, v);
            }
        }
    }
}
