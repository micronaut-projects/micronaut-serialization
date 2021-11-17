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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.SerdeAnnotationUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Holder for data about a deserializable bean.
 *
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
    @Nullable
    public final AnySetter<Object> anySetter;

    public final int creatorSize;

    public final boolean ignoreUnknown;
    public final boolean delegating;
    // CHECKSTYLE:ON

    public DeserBean(
            BeanIntrospection<T> introspection,
            Deserializer.DecoderContext decoderContext,
            DeserBeanRegistry deserBeanRegistry)
            throws SerdeException {
        this.introspection = introspection;
        final SerdeConfig.CreatorMode creatorMode = introspection
                .getConstructor().getAnnotationMetadata()
                .enumValue(Creator.class, "mode", SerdeConfig.CreatorMode.class)
                .orElse(null);
        delegating = creatorMode == SerdeConfig.CreatorMode.DELEGATING;
        final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        creatorSize = constructorArguments.length;
        this.ignoreUnknown = introspection.booleanValue(SerdeConfig.Ignored.class, "ignoreUnknown").orElse(true);
        final HashMap<String, DerProperty<T, ?>> creatorParams = new HashMap<>(constructorArguments.length);
        List<DerProperty<T, ?>> creatorUnwrapped = null;
        AnySetter<Object> anySetterValue = null;
        List<DerProperty<T, ?>> unwrappedProperties = null;
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<Object> constructorArgument = (Argument<Object>) constructorArguments[i];
            final AnnotationMetadata annotationMetadata = resolveArgumentMetadata(introspection, constructorArgument, constructorArgument.getAnnotationMetadata());
            if (annotationMetadata.isTrue(SerdeConfig.class, SerdeConfig.IGNORED)) {
                continue;
            }
            if (annotationMetadata.isAnnotationPresent(SerdeConfig.AnySetter.class)) {
                anySetterValue = new AnySetter<>(constructorArgument, i, decoderContext);
                creatorParams.put(
                        constructorArgument.getName(),
                        new DerProperty<>(
                                introspection,
                                i,
                                constructorArgument,
                                null,
                                decoderContext.findDeserializer(constructorArgument),
                                null,
                                decoderContext
                        )
                );
                continue;
            }

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
                        introspection,
                        i,
                        constructorArgument,
                        null,
                        deserializer,
                        unwrapped,
                        decoderContext
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
                                introspection,
                                i,
                                constructorArgument,
                                null,
                                (Deserializer) deserializer,
                                unwrapped,
                                decoderContext
                        )
                );
            } else {
                creatorParams.put(
                        jsonProperty,
                        new DerProperty<>(
                                introspection,
                                i,
                                constructorArgument,
                                null,
                                (Deserializer) deserializer,
                                null,
                                decoderContext
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
        final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
        final List<BeanMethod<T, Object>> jsonSetters = new ArrayList<>(beanMethods.size());
        BeanMethod<T, Object> anySetter = null;
        for (BeanMethod<T, Object> method : beanMethods) {
            if (method.isAnnotationPresent(SerdeConfig.Setter.class)) {
                jsonSetters.add(method);
            } else if (method.isAnnotationPresent(SerdeConfig.AnySetter.class) && ArrayUtils.isNotEmpty(method.getArguments())) {
                anySetter = method;
            }
        }

        //noinspection unchecked
        if (anySetterValue == null) {
            anySetterValue = (anySetter != null ? new AnySetter(anySetter, decoderContext) : null);
        }

        if (CollectionUtils.isNotEmpty(beanProperties) || CollectionUtils.isNotEmpty(jsonSetters)) {
            final HashMap<String, DerProperty<T, Object>> readProps = new HashMap<>(beanProperties.size() + jsonSetters.size());
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanProperty<T, Object> beanProperty = beanProperties.get(i);
                final AnnotationMetadata annotationMetadata = beanProperty.getAnnotationMetadata();
                if (annotationMetadata.isAnnotationPresent(SerdeConfig.AnySetter.class)) {
                    anySetterValue = new AnySetter(beanProperty, decoderContext);
                } else {
                    final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.class);
                    final Argument<Object> t = beanProperty.asArgument();

                    if (isUnwrapped) {
                        if (unwrappedProperties == null) {
                            unwrappedProperties = new ArrayList<>();
                        }
                        final Deserializer<Object> deserializer = findDeserializer(decoderContext, t);
                        final DeserBean<Object> unwrapped = deserBeanRegistry.getDeserializableBean(
                                t,
                                decoderContext
                        );
                        final AnnotationMetadataHierarchy combinedMetadata =
                                new AnnotationMetadataHierarchy(annotationMetadata,
                                                                t.getAnnotationMetadata());
                        unwrappedProperties.add(new DerProperty<>(
                                introspection,
                                i,
                                t,
                                combinedMetadata,
                                beanProperty::set,
                                deserializer,
                                unwrapped,
                                decoderContext
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

                        readProps.put(jsonProperty, new DerProperty<>(
                                        introspection,
                                        i,
                                        t,
                                        beanProperty::set,
                                        findDeserializer(decoderContext, t),
                                        null,
                                        decoderContext
                                )
                        );
                    }
                }
            }

            for (BeanMethod<T, Object> jsonSetter : jsonSetters) {
                final String property = jsonSetter.getAnnotationMetadata()
                        .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                        .orElseGet(() -> NameUtils.getPropertyNameForSetter(jsonSetter.getName()));
                final Argument<Object> argument = (Argument<Object>) jsonSetter.getArguments()[0];
                readProps.put(property, new DerProperty<>(
                        introspection,
                        0,
                        argument,
                        jsonSetter::invoke,
                        findDeserializer(decoderContext, argument),
                        null,
                        decoderContext
                ));
            }

            this.readProperties = Collections.unmodifiableMap(readProps);
        } else {
            readProperties = null;
        }

        this.anySetter = anySetterValue;
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

    private Deserializer<Object> findDeserializer(Deserializer.DecoderContext decoderContext, Argument<Object> argument) throws SerdeException {
        Class customDeser = argument.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).orElse(null);
        if (customDeser != null) {
            return decoderContext.findCustomDeserializer(customDeser);
        }
        return (Deserializer<Object>) decoderContext.findDeserializer(argument);
    }

    static final class AnySetter<T> {
        final Argument<T> valueType;
        @Nullable
        final Deserializer<? extends T> deserializer;
        private final BiConsumer<Object, Map<String, ? extends T>> mapSetter;
        private final TriConsumer<Object, T> valueSetter;

        private AnySetter(
                BeanMethod<? super Object, Object> anySetter,
                Deserializer.DecoderContext decoderContext) throws SerdeException {
            final Argument<?>[] arguments = anySetter.getArguments();
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final boolean singleArg = arguments.length == 1;
            final Argument<T> argument =
                    (Argument<T>) (singleArg ? arguments[0].getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT) : arguments[1]);
            this.valueType = argument;
            this.deserializer = argument.equalsType(Argument.OBJECT_ARGUMENT) ? null : decoderContext.findDeserializer(argument);
            if (singleArg) {
                this.valueSetter = null;
                this.mapSetter = anySetter::invoke;
            } else {
                this.valueSetter = anySetter::invoke;
                this.mapSetter = null;
            }
        }

        private AnySetter(
                BeanProperty<? super Object, Object> anySetter,
                Deserializer.DecoderContext decoderContext) throws SerdeException {
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final Argument<T> argument = (Argument<T>) anySetter.asArgument().getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
            this.valueType = argument;
            this.deserializer = argument.equalsType(Argument.OBJECT_ARGUMENT) ? null : decoderContext.findDeserializer(argument);
            this.mapSetter = anySetter::set;
            this.valueSetter = null;
        }

        private AnySetter(
                Argument<Object> anySetter,
                int index,
                Deserializer.DecoderContext decoderContext) throws SerdeException {
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final Argument<T> argument = (Argument<T>) anySetter.getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
            this.valueType = argument;
            this.deserializer = argument.equalsType(Argument.OBJECT_ARGUMENT) ? null : decoderContext.findDeserializer(argument);
            this.mapSetter = (o, map) -> {
                ((Object[]) o)[index] = map;
            };
            this.valueSetter = null;
        }

        void bind(Map<String, T> values, Object object) {
            if (values != null) {
                if (mapSetter != null) {
                    mapSetter.accept(object, values);
                } else if (valueSetter != null) {
                    for (String s : values.keySet()) {
                        valueSetter.accept(object, s, values.get(s));
                    }
                }
            }
        }
    }

    private interface TriConsumer<T, V> {
        void accept(T t, String k, V v);
    }

    /**
     * Models a deserialization property.
     *
     * @param <B> The bean type
     * @param <P> The property type
     */
    @Internal
    // CHECKSTYLE:OFF
    public static final class DerProperty<B, P> {
        public final BeanIntrospection<B> instrospection;
        public final int index;
        public final Argument<P> argument;
        @Nullable
        public final P defaultValue;
        public final boolean required;
        public final boolean isAnySetter;
        @Nullable
        public final Class<?>[] views;
        public final @Nullable
        BiConsumer<B, P> writer;
        public final @NonNull
        Deserializer<? super P> deserializer;
        public final DeserBean<P> unwrapped;

        public DerProperty(BeanIntrospection<B> introspection,
                           int index,
                           Argument<P> argument,
                           @Nullable BiConsumer<B, P> writer,
                           @NonNull Deserializer<P> deserializer,
                           @Nullable DeserBean<P> unwrapped,
                           @NonNull Deserializer.DecoderContext decoderContext) throws SerdeException {
            this(
                    introspection,
                    index,
                    argument,
                    argument.getAnnotationMetadata(),
                    writer,
                    deserializer,
                    unwrapped,
                    decoderContext
            );
        }

        public DerProperty(BeanIntrospection<B> instrospection,
                           int index,
                           Argument<P> argument,
                           AnnotationMetadata argumentMetadata,
                           @Nullable BiConsumer<B, P> writer,
                           @NonNull Deserializer<P> deserializer,
                           @Nullable DeserBean<P> unwrapped,
                           @NonNull Deserializer.DecoderContext decoderContext) throws SerdeException {
            this.instrospection = instrospection;
            this.index = index;
            this.argument = argument;
            this.required = argument.isNonNull();
            this.writer = writer;
            this.deserializer = deserializer.createSpecific(argument, decoderContext);
            // compute default
            AnnotationMetadata annotationMetadata = resolveArgumentMetadata(instrospection, argument, argumentMetadata);
            this.views = SerdeAnnotationUtil.resolveViews(instrospection, annotationMetadata);

            try {
                this.defaultValue = annotationMetadata
                        .stringValue(Bindable.class, "defaultValue")
                        .map(s -> ConversionService.SHARED.convertRequired(s, argument))
                        .orElse(deserializer.getDefaultValue());
            } catch (ConversionErrorException e) {
                throw new SerdeException((index > -1 ? "Constructor Argument" : "Property") + " [" + argument + "] of type [" + instrospection.getBeanType().getName() + "] defines an invalid default value", e);
            }
            this.unwrapped = unwrapped;
            this.isAnySetter = annotationMetadata.isAnnotationPresent(SerdeConfig.AnySetter.class);
        }

        public void setDefault(@NonNull B bean) throws SerdeException {
            if (defaultValue != null && writer != null) {
                writer.accept(bean, defaultValue);
            } else if (required) {
                throw new SerdeException("Unable to deserialize type [" + instrospection.getBeanType().getName() + "]. Required property [" + argument +
                        "] is not present in supplied data");
            }
        }

        public void setDefault(@NonNull Object[] params) throws SerdeException {
            if (defaultValue != null) {
                params[index] = defaultValue;
            } else if (required) {
                throw new SerdeException("Unable to deserialize type [" + instrospection.getBeanType().getName() + "]. Required constructor parameter [" + argument + "] at index [" + index + "] is not present or is null in the supplied data");
            }
        }

        public void set(@NonNull B obj, @Nullable P v) throws SerdeException {
            if (v == null && argument.isNonNull()) {
                throw new SerdeException("Unable to deserialize type [" + instrospection.getBeanType().getName() + "]. Required property [" + argument +
                        "] is not present in supplied data");

            }
            if (writer != null) {
                writer.accept(obj, v);
            }
        }
    }

    private static <B, P> AnnotationMetadata resolveArgumentMetadata(BeanIntrospection<B> instrospection, Argument<P> argument, AnnotationMetadata annotationMetadata) {
        // records store metadata in the bean property
        final AnnotationMetadata propertyMetadata = instrospection.getProperty(argument.getName(), argument.getType())
                .map(BeanProperty::getAnnotationMetadata)
                .orElse(AnnotationMetadata.EMPTY_METADATA);
        return new AnnotationMetadataHierarchy(propertyMetadata, annotationMetadata);
    }
}
