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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.UnsafeBeanProperty;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

/**
 * Holder for data about a deserializable bean.
 *
 * @param <T> The generic type
 */
@Internal
class DeserBean<T> {
    private static final String JK_PROP = "com.fasterxml.jackson.annotation.JsonProperty";

    // CHECKSTYLE:OFF
    @NonNull
    public final BeanIntrospection<T> introspection;
    @Nullable
    public final PropertiesBag<T> creatorParams;
    @Nullable
    public final DerProperty<T, ?>[] creatorUnwrapped;
    @Nullable
    public final PropertiesBag<T> readProperties;
    @Nullable
    public final DerProperty<T, Object>[] unwrappedProperties;
    @Nullable
    public final AnySetter<Object> anySetter;

    public final int creatorSize;

    public final boolean ignoreUnknown;
    public final boolean delegating;
    public final boolean simpleBean;
    public final boolean recordLikeBean;
    public final ConversionService conversionService;

    private volatile boolean initialized;
    // CHECKSTYLE:ON

    public DeserBean(
            BeanIntrospection<T> introspection,
            Deserializer.DecoderContext decoderContext,
            DeserBeanRegistry deserBeanRegistry)
            throws SerdeException {
        this.conversionService = decoderContext.getConversionService();
        this.introspection = introspection;
        final SerdeConfig.SerCreatorMode creatorMode = introspection
                .getConstructor().getAnnotationMetadata()
                .enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class)
                .orElse(null);
        delegating = creatorMode == SerdeConfig.SerCreatorMode.DELEGATING;
        final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        creatorSize = constructorArguments.length;
        PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, decoderContext, null);

        this.ignoreUnknown = introspection.booleanValue(SerdeConfig.SerIgnored.class, "ignoreUnknown").orElse(true);
        final PropertiesBag.Builder<T> creatorPropertiesBuilder = new PropertiesBag.Builder<>(introspection, constructorArguments.length);
        List<DerProperty<T, ?>> creatorUnwrapped = null;
        AnySetter<Object> anySetterValue = null;
        List<DerProperty<T, ?>> unwrappedProperties = null;
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<Object> constructorArgument = resolveArgument((Argument<Object>) constructorArguments[i]);
            final AnnotationMetadata annotationMetadata = resolveArgumentMetadata(introspection, constructorArgument, constructorArgument.getAnnotationMetadata());
            if (annotationMetadata.isTrue(SerdeConfig.class, SerdeConfig.IGNORED)) {
                continue;
            }
            if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerAnySetter.class)) {
                anySetterValue = new AnySetter<>(constructorArgument, i);
                final String n = constructorArgument.getName();
                creatorPropertiesBuilder.register(
                        n,
                        new DerProperty<>(
                                conversionService,
                                introspection,
                                i,
                                n,
                                constructorArgument,
                                null,
                                null,
                                null
                        ),
                        false
                );
                continue;
            }

            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(annotationMetadata, decoderContext, entityPropertyNamingStrategy);
            final String propertyName = resolveName(constructorArgument, annotationMetadata, propertyNamingStrategy);
            Argument<Object> constructorWithPropertyArgument = Argument.of(
                    constructorArgument.getType(),
                    constructorArgument.getName(),
                    annotationMetadata,
                    constructorArgument.getTypeParameters()
            );
            final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class);
            final DerProperty<T, Object> derProperty;
            if (isUnwrapped) {
                if (creatorUnwrapped == null) {
                    creatorUnwrapped = new ArrayList<>();
                }

                final DeserBean<Object> unwrapped = deserBeanRegistry.getDeserializableBean(
                        constructorArgument,
                        decoderContext
                );
                creatorUnwrapped.add(new DerProperty(
                        conversionService,
                        introspection,
                        i,
                        propertyName,
                        constructorWithPropertyArgument,
                        null,
                        null,
                        unwrapped
                ));
                String prefix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.PREFIX).orElse("");
                String suffix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.SUFFIX).orElse("");

                final PropertiesBag<Object> unwrappedCreatorParams = unwrapped.creatorParams;
                if (unwrappedCreatorParams != null) {
                    for (Map.Entry<String, DerProperty<Object, Object>> e : unwrappedCreatorParams.getProperties()) {
                        String resolved = prefix + e.getKey() + suffix;
                        //noinspection unchecked
                        creatorPropertiesBuilder.register(resolved, (DerProperty<T, Object>) e.getValue(), false);
                    }
                }

                derProperty = new DerProperty<>(
                        conversionService,
                        introspection,
                        i,
                        propertyName,
                        constructorWithPropertyArgument,
                        null,
                        null,
                        unwrapped
                );

            } else {
                derProperty = new DerProperty<>(
                        conversionService,
                        introspection,
                        i,
                        propertyName,
                        constructorWithPropertyArgument,
                        introspection.getProperty(propertyName).orElse(null),
                        null,
                        null
                );
            }
            creatorPropertiesBuilder.register(propertyName, derProperty, true);
        }

        final List<BeanProperty<T, Object>> beanProperties = introspection.getBeanProperties()
                .stream().filter(bp -> {
                    final AnnotationMetadata annotationMetadata = bp.getAnnotationMetadata();
                    return !bp.isReadOnly() &&
                            !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false) &&
                            !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false);
                }).toList();
        final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
        final List<BeanMethod<T, Object>> jsonSetters = new ArrayList<>(beanMethods.size());
        BeanMethod<T, Object> anySetter = null;
        for (BeanMethod<T, Object> method : beanMethods) {
            if (method.isAnnotationPresent(SerdeConfig.SerSetter.class)) {
                jsonSetters.add(method);
            } else if (method.isAnnotationPresent(SerdeConfig.SerAnySetter.class) && ArrayUtils.isNotEmpty(method.getArguments())) {
                anySetter = method;
            }
        }

        if (anySetterValue == null) {
            anySetterValue = (anySetter != null ? new AnySetter(anySetter) : null);
        }

        if (CollectionUtils.isNotEmpty(beanProperties) || CollectionUtils.isNotEmpty(jsonSetters)) {
            PropertiesBag.Builder<T> readPropertiesBuilder = new PropertiesBag.Builder<>(introspection);
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanProperty<T, Object> beanProperty = beanProperties.get(i);
                PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(beanProperty.getAnnotationMetadata(), decoderContext, entityPropertyNamingStrategy);
                final AnnotationMetadata annotationMetadata = beanProperty.getAnnotationMetadata();
                if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerAnySetter.class)) {
                    anySetterValue = new AnySetter(beanProperty);
                } else {
                    final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class);
                    final Argument<Object> t = resolveArgument(beanProperty.asArgument());

                    if (isUnwrapped) {
                        if (unwrappedProperties == null) {
                            unwrappedProperties = new ArrayList<>();
                        }
                        final DeserBean<Object> unwrapped = deserBeanRegistry.getDeserializableBean(
                                t,
                                decoderContext
                        );
                        final AnnotationMetadataHierarchy combinedMetadata =
                                new AnnotationMetadataHierarchy(annotationMetadata,
                                        t.getAnnotationMetadata());
                        unwrappedProperties.add(new DerProperty<>(
                                conversionService,
                                introspection,
                                i,
                                t.getName(),
                                t,
                                combinedMetadata,
                                beanProperty,
                                null,
                                unwrapped
                        ));
                        String prefix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.PREFIX).orElse("");
                        String suffix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.SUFFIX).orElse("");

                        PropertiesBag<T> unwrappedProps = (PropertiesBag) unwrapped.readProperties;
                        if (unwrappedProps != null) {
                            for (Map.Entry<String, DerProperty<T, Object>> e : unwrappedProps.getProperties()) {
                                String resolved = prefix + e.getKey() + suffix;
                                //noinspection unchecked
                                readPropertiesBuilder.register(resolved, e.getValue(), false);

                            }
                        }
                        final PropertiesBag<Object> unwrappedCreatorParams = unwrapped.creatorParams;
                        if (unwrappedCreatorParams != null) {
                            for (Map.Entry<String, DerProperty<Object, Object>> e : unwrappedCreatorParams.getProperties()) {
                                String resolved = prefix + e.getKey() + suffix;
                                //noinspection unchecked
                                creatorPropertiesBuilder.register(resolved, (DerProperty) e.getValue(), false);
                            }
                        }
                    } else {

                        final String jsonProperty = resolveName(beanProperty, annotationMetadata, propertyNamingStrategy);
                        final DerProperty<T, Object> derProperty = new DerProperty<>(
                                conversionService,
                                introspection,
                                i,
                                jsonProperty,
                                t,
                                beanProperty,
                                null,
                                null
                        );
                        readPropertiesBuilder.register(jsonProperty, derProperty, true);
                    }
                }
            }

            for (BeanMethod<T, Object> jsonSetter : jsonSetters) {
                PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(jsonSetter.getAnnotationMetadata(), decoderContext, entityPropertyNamingStrategy);
                final String property = resolveName(
                        new AnnotatedElement() {
                            @Override
                            public String getName() {
                                return NameUtils.getPropertyNameForSetter(jsonSetter.getName());
                            }

                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return jsonSetter.getAnnotationMetadata();
                            }
                        },
                        jsonSetter.getAnnotationMetadata(),
                        propertyNamingStrategy
                );
                final Argument<Object> argument = resolveArgument((Argument<Object>) jsonSetter.getArguments()[0]);
                final DerProperty<T, Object> derProperty = new DerProperty<>(
                        conversionService,
                        introspection,
                        0,
                        property,
                        argument,
                        null,
                        jsonSetter,
                        null
                );
                readPropertiesBuilder.register(property, derProperty, true);
            }
            readProperties = readPropertiesBuilder.build();
        } else {
            readProperties = null;
        }

        this.anySetter = anySetterValue;
        this.creatorParams = creatorPropertiesBuilder.build();
        //noinspection unchecked
        this.creatorUnwrapped = creatorUnwrapped != null ? creatorUnwrapped.toArray(new DerProperty[0]) : null;
        //noinspection unchecked
        this.unwrappedProperties = unwrappedProperties != null ? unwrappedProperties.toArray(new DerProperty[0]) : null;

        simpleBean = isSimpleBean();
        recordLikeBean = isRecordLikeBean();
    }

    public boolean isSubtyped() {
        return false;
    }

    public void initialize(Deserializer.DecoderContext decoderContext) throws SerdeException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    if (readProperties != null) {
                        List<Map.Entry<String, DerProperty<T, Object>>> properties = readProperties.getProperties();
                        for (Map.Entry<String, DerProperty<T, Object>> e : properties) {
                            DerProperty<T, Object> property = e.getValue();
                            initProperty(property, decoderContext);
                        }
                    }
                    if (creatorParams != null) {
                        List<Map.Entry<String, DerProperty<T, Object>>> properties = creatorParams.getProperties();
                        for (Map.Entry<String, DerProperty<T, Object>> e : properties) {
                            DerProperty<T, Object> property = e.getValue();
                            initProperty(property, decoderContext);
                        }
                    }
                    if (anySetter != null) {
                        anySetter.deserializer = anySetter.valueType.equalsType(Argument.OBJECT_ARGUMENT) ? null : findDeserializer(decoderContext, anySetter.valueType);
                    }
                    if (unwrappedProperties != null) {
                        for (DerProperty<T, Object> unwrappedProperty : unwrappedProperties) {
                            initProperty(unwrappedProperty, decoderContext);
                        }
                    }
                    initialized = true;
                }
            }
        }
    }

    private boolean isSimpleBean() {
        if (delegating || this instanceof SubtypedDeserBean || creatorParams != null || creatorUnwrapped != null || unwrappedProperties != null || anySetter != null) {
            return false;
        }
        if (readProperties != null) {
            for (Map.Entry<String, DerProperty<T, Object>> e : readProperties.getProperties()) {
                DerProperty<T, Object> property = e.getValue();
                if (property.isAnySetter || property.views != null || property.managedRef != null || introspection != property.instrospection || property.backRef != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isRecordLikeBean() {
        if (delegating || this instanceof SubtypedDeserBean || readProperties != null || creatorUnwrapped != null || unwrappedProperties != null || anySetter != null) {
            return false;
        }
        if (creatorParams != null) {
            for (Map.Entry<String, DerProperty<T, Object>> e : creatorParams.getProperties()) {
                DerProperty<T, Object> property = e.getValue();
                if (property.beanProperty != null && !property.beanProperty.isReadOnly() || property.isAnySetter || property.views != null || property.managedRef != null || introspection != property.instrospection || property.backRef != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void initProperty(DerProperty<T, Object> property, Deserializer.DecoderContext decoderContext) throws SerdeException {
        property.deserializer = findDeserializer(decoderContext, property.argument);
        if (property.unwrapped != null) {
            property.unwrapped.initialize(decoderContext);
        }
    }

    private PropertyNamingStrategy getPropertyNamingStrategy(AnnotationMetadata annotationMetadata,
                                                             Deserializer.DecoderContext decoderContext,
                                                             PropertyNamingStrategy defaultNamingStrategy) throws SerdeException {
        Class<? extends PropertyNamingStrategy> namingStrategyClass = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
                .orElse(null);
        return namingStrategyClass == null ? defaultNamingStrategy : decoderContext.findNamingStrategy(namingStrategyClass);
    }

    private <A> Argument<A> resolveArgument(Argument<A> argument) {
        if (argument instanceof GenericPlaceholder || argument.hasTypeVariables()) {
            Map<String, Argument<?>> bounds = getBounds();
            if (!bounds.isEmpty()) {
                return resolveArgument(argument, bounds);
            }
        }
        return argument;
    }

    @SuppressWarnings("unchecked")
    private <A> Argument<A> resolveArgument(Argument<A> argument, Map<String, Argument<?>> bounds) {
        Argument<?>[] declaredParameters = argument.getTypeParameters();
        Argument<?>[] typeParameters = resolveParameters(bounds, declaredParameters);
        if (argument instanceof GenericPlaceholder) {
            GenericPlaceholder<A> gp = (GenericPlaceholder<A>) argument;
            Argument<?> resolved = bounds.get(gp.getVariableName());
            if (resolved != null) {
                return (Argument<A>) Argument.of(
                        resolved.getType(),
                        argument.getName(),
                        argument.getAnnotationMetadata(),
                        typeParameters
                );
            } else if (typeParameters != declaredParameters) {
                return Argument.ofTypeVariable(
                        argument.getType(),
                        argument.getName(),
                        gp.getVariableName(),
                        gp.getAnnotationMetadata(),
                        typeParameters
                );
            }
        } else if (typeParameters != declaredParameters) {
            return Argument.of(
                    argument.getType(),
                    argument.getName(),
                    argument.getAnnotationMetadata(),
                    typeParameters
            );
        }
        return argument;
    }

    private Argument<?>[] resolveParameters(Map<String, Argument<?>> bounds, Argument[] typeParameters) {
        if (ArrayUtils.isEmpty(typeParameters)) {
            return typeParameters;
        }
        Argument<?>[] resolvedParameters = new Argument[typeParameters.length];
        boolean differ = false;
        for (int i = 0; i < typeParameters.length; i++) {
            Argument<?> typeParameter = typeParameters[i];
            Argument<?> resolved = resolveArgument(typeParameter, bounds);
            if (resolved != typeParameter) {
                resolvedParameters[i] = resolved;
                differ = true;
            } else {
                resolvedParameters[i] = typeParameter;
            }
        }
        return differ ? resolvedParameters : typeParameters;
    }

    /**
     * The generic bounds for this deserializable bean.
     *
     * @return The bounds, never {@code null}
     */
    protected @NonNull
    Map<String, Argument<?>> getBounds() {
        return Collections.emptyMap();
    }

    private String resolveName(AnnotatedElement annotatedElement, AnnotationMetadata annotationMetadata, PropertyNamingStrategy namingStrategy) {
        if (namingStrategy != null) {
            return namingStrategy.translate(annotatedElement);
        }
        return annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .orElseGet(() ->
                        annotationMetadata.stringValue(JK_PROP)
                                .orElseGet(annotatedElement::getName)
                );
    }

    private static <T> Deserializer<T> findDeserializer(Deserializer.DecoderContext decoderContext, Argument<T> argument) throws SerdeException {
        Class customDeser = argument.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).orElse(null);
        if (customDeser != null) {
            return decoderContext.findCustomDeserializer(customDeser).createSpecific(decoderContext, argument);
        }
         return (Deserializer<T>) decoderContext.findDeserializer(argument).createSpecific(decoderContext, argument);
    }

    static final class AnySetter<T> {
        // CHECKSTYLE:OFF
        final Argument<T> valueType;
        private final BiConsumer<Object, Map<String, ? extends T>> mapSetter;
        private final TriConsumer<Object, T> valueSetter;

        // Null when DeserBean not initialized
        public Deserializer<? extends T> deserializer;
        // CHECKSTYLE:ON

        private AnySetter(BeanMethod<? super Object, Object> anySetter) {
            final Argument<?>[] arguments = anySetter.getArguments();
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final boolean singleArg = arguments.length == 1;
            final Argument<T> argument =
                    (Argument<T>) (singleArg ? arguments[0].getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT) : arguments[1]);
            this.valueType = argument;
//            this.deserializer = argument.equalsType(Argument.OBJECT_ARGUMENT) ? null : findDeserializer(decoderContext, argument);
            if (singleArg) {
                this.valueSetter = null;
                this.mapSetter = anySetter::invoke;
            } else {
                this.valueSetter = anySetter::invoke;
                this.mapSetter = null;
            }
        }

        private AnySetter(BeanProperty<? super Object, Object> anySetter) {
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final Argument<T> argument = (Argument<T>) anySetter.asArgument().getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
            this.valueType = argument;
//            this.deserializer = argument.equalsType(Argument.OBJECT_ARGUMENT) ? null : findDeserializer(decoderContext, argument);
            this.mapSetter = anySetter::set;
            this.valueSetter = null;
        }

        private AnySetter(Argument<Object> anySetter, int index) throws SerdeException {
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final Argument<T> argument = (Argument<T>) anySetter.getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
            this.valueType = argument;
//            this.deserializer = argument.equalsType(Argument.OBJECT_ARGUMENT) ? null : findDeserializer(decoderContext, argument);
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
        public final boolean mustSetField;
        public final boolean explicitlyRequired;
        public final boolean nonNull;
        public final boolean nullable;
        public final boolean isAnySetter;
        @Nullable
        public final Class<?>[] views;
        @Nullable
        public final String[] aliases;

        @Nullable
        public final UnsafeBeanProperty<B, P> beanProperty;
        @Nullable
        public final BeanMethod<B, P> beanMethod;

        public final DeserBean<P> unwrapped;
        public final String managedRef;
        public final String backRef;

        // Null when DeserBean not initialized
        public Deserializer<? super P> deserializer;

        public DerProperty(ConversionService conversionService,
                           BeanIntrospection<B> introspection,
                           int index,
                           String property,
                           Argument<P> argument,
                           @Nullable BeanProperty<B, P> beanProperty,
                           @Nullable BeanMethod<B, P> beanMethod,
                           @Nullable DeserBean<P> unwrapped) throws SerdeException {
            this(   conversionService,
                    introspection,
                    index,
                    property,
                    argument,
                    argument.getAnnotationMetadata(),
                    beanProperty,
                    beanMethod,
                    unwrapped
            );
        }

        public DerProperty(ConversionService conversionService,
                           BeanIntrospection<B> instrospection,
                           int index,
                           String property,
                           Argument<P> argument,
                           AnnotationMetadata argumentMetadata,
                           @Nullable BeanProperty<B, P> beanProperty,
                           @Nullable BeanMethod<B, P> beanMethod,
                           @Nullable DeserBean<P> unwrapped) throws SerdeException {
            this.instrospection = instrospection;
            this.index = index;
            this.argument = argument;
            this.mustSetField = argument.isNonNull() || argument.isAssignableFrom(Optional.class)
                    || argument.isAssignableFrom(OptionalLong.class)
                    || argument.isAssignableFrom(OptionalDouble.class)
                    || argument.isAssignableFrom(OptionalInt.class);
            this.nonNull = argument.isNonNull();
            this.nullable = argument.isNullable();
            this.beanProperty = (UnsafeBeanProperty<B, P>) beanProperty;
            this.beanMethod = beanMethod;
            // compute default
            AnnotationMetadata annotationMetadata = resolveArgumentMetadata(instrospection, argument, argumentMetadata);
            this.views = SerdeAnnotationUtil.resolveViews(instrospection, annotationMetadata);

            try {
                this.defaultValue = annotationMetadata
                        .stringValue(Bindable.class, "defaultValue")
                        .map(s -> conversionService.convertRequired(s, argument))
                        .orElse(null);
            } catch (ConversionErrorException e) {
                throw new SerdeException((index > -1 ? "Constructor Argument" : "Property") + " [" + argument + "] of type [" + instrospection.getBeanType().getName() + "] defines an invalid default value", e);
            }
            this.unwrapped = unwrapped;
            this.isAnySetter = annotationMetadata.isAnnotationPresent(SerdeConfig.SerAnySetter.class);
            final String[] aliases = annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.ALIASES);
            if (ArrayUtils.isNotEmpty(aliases)) {
                this.aliases = ArrayUtils.concat(aliases, property);
            } else {
                this.aliases = null;
            }
            this.managedRef = annotationMetadata.stringValue(SerdeConfig.SerManagedRef.class)
                    .orElse(null);
            this.backRef = annotationMetadata.stringValue(SerdeConfig.SerBackRef.class)
                    .orElse(null);
            this.explicitlyRequired = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.REQUIRED)
                    .orElse(false);
        }

        public void setDefault(Deserializer.DecoderContext decoderContext, @NonNull B bean) throws SerdeException {
            if (!explicitlyRequired) {
                P def = defaultValue;
                if (def == null) {
                    if (!mustSetField) {
                        return;
                    }
                    def = (P) deserializer.getDefaultValue(decoderContext, (Argument) argument);
                }
                if (def != null) {
                    if (beanProperty != null) {
                        beanProperty.setUnsafe(bean, def);
                        return;
                    }
                    if (beanMethod != null) {
                        beanMethod.invoke(bean, def);
                        return;
                    }
                }
            }
            throw new SerdeException("Unable to deserialize type [" + instrospection.getBeanType().getName() + "]. Required property [" + argument +
                    "] is not present in supplied data");
        }

        public void setDefault(Deserializer.DecoderContext decoderContext, @NonNull Object[] params) throws SerdeException {
            if (!explicitlyRequired) {
                if (defaultValue != null) {
                    params[index] = defaultValue;
                    return;
                }
                if (!mustSetField && !argument.isPrimitive()) {
                    return;
                }
                P newDefaultValue = (P) deserializer.getDefaultValue(decoderContext, (Argument) argument);
                if (newDefaultValue != null) {
                    params[index] = newDefaultValue;
                    return;
                }
            }
            throw new SerdeException("Unable to deserialize type [" + instrospection.getBeanType().getName() + "]. Required constructor parameter [" + argument + "] at index [" + index + "] is not present or is null in the supplied data");
        }

        public void set(@NonNull B obj, @Nullable P v) throws SerdeException {
            if (v == null && nonNull) {
                throw new SerdeException("Unable to deserialize type [" + instrospection.getBeanType().getName() + "]. Required property [" + argument +
                        "] is not present in supplied data");

            }
            if (beanProperty != null) {
                beanProperty.setUnsafe(obj, v);
            }
            if (beanMethod != null) {
                beanMethod.invoke(obj, v);
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
