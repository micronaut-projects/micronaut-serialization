/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.jsonb;

import io.micronaut.context.BeanContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.DefaultSerdeIntrospections;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.spi.JsonProvider;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import tools.jackson.core.ObjectReadContext;

/**
 * Micronaut Serialization backed JSON-B provider with reflection fallback behavior for JSON-B compatibility.
 *
 * @since 3.1.0
 */
public final class MicronautJsonbReflectionProvider extends MicronautJsonbProvider {
    @Override
    public JsonbBuilder create() {
        return new Builder();
    }

    /**
     * Creates the context-backed reflection provider used by {@link JsonbFactory}
     * when reflection fallback is enabled.
     *
     * @param config The JSON-B configuration
     * @param beanContext The Micronaut bean context
     * @param objectMapper The base object mapper
     * @param serdeIntrospections The base Serde introspections
     * @param serdeConfiguration The effective Serde configuration
     * @param serializationConfiguration The effective serialization configuration
     * @param deserializationConfiguration The effective deserialization configuration
     * @return The configured JSON-B instance
     */
    static Jsonb create(JsonbConfig config,
                        BeanContext beanContext,
                        ObjectMapper objectMapper,
                        SerdeIntrospections serdeIntrospections,
                        SerdeConfiguration serdeConfiguration,
                        SerializationConfiguration serializationConfiguration,
                        DeserializationConfiguration deserializationConfiguration) {
        return new MicronautJsonb(config, beanContext, objectMapper, serdeIntrospections, serdeConfiguration, serializationConfiguration, deserializationConfiguration);
    }

    private static final class Builder extends MicronautJsonbProvider.Builder {
        @Override
        protected Jsonb build(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            return new MicronautJsonb(config, jsonProvider);
        }
    }

    static final class MicronautJsonb extends MicronautJsonbProvider.MicronautJsonb {
        private final @Nullable Object propertyNamingStrategy;
        private final @Nullable PropertyVisibilityStrategy propertyVisibilityStrategy;
        private final JsonbRuntimeCustomizations customizations;
        private final JsonbFallbackCodec fallbackCodec;
        private final boolean failOnUnknownProperties;
        private final JsonbRuntimeIntrospectionResolver runtimeIntrospectionResolver;
        private final JsonbRuntimeIntrospectionResolver fallbackRuntimeIntrospectionResolver;
        private final JsonbBridgeSupport.@Nullable ComponentFactory componentFactory;
        private final ConcurrentMap<Class<? extends PropertyVisibilityStrategy>, PropertyVisibilityStrategy> visibilityStrategies = new ConcurrentHashMap<>();
        private final ConcurrentMap<Class<?>, JsonbRuntimeBeanIntrospection<?>> visibilityIntrospections = new ConcurrentHashMap<>();
        private final ConcurrentMap<Argument<?>, Boolean> generatedReadDirectAvailability = generatedSerdeCache();
        private final ConcurrentMap<GeneratedWriteKey, Boolean> generatedWriteDirectAvailability = generatedSerdeCache();

        /**
         * Creates a standalone reflection provider. Standalone instances build a
         * reduced internal context because no Micronaut application context is
         * available for callback component lookup.
         *
         * @param config The JSON-B configuration
         * @param jsonProvider The optional JSON-P provider supplied by the builder
         */
        MicronautJsonb(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            this(config, standaloneRuntimeMapper(config, jsonProvider));
        }

        private MicronautJsonb(JsonbConfig config, RuntimeMapperAndClose mapperAndClose) {
            super(config, mapperAndClose.mapper(), mapperAndClose.serdeConfiguration(), mapperAndClose.closeAction());
            this.componentFactory = null;
            this.runtimeIntrospectionResolver = mapperAndClose.resolver();
            this.fallbackRuntimeIntrospectionResolver = mapperAndClose.fallbackResolver();
            this.propertyNamingStrategy = config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null);
            this.propertyVisibilityStrategy = propertyVisibilityStrategy(config);
            this.customizations = mapperAndClose.customizations();
            this.fallbackCodec = new JsonbFallbackCodec(mapperAndClose.fallbackMapper(), mapperAndClose.serdeConfiguration(), binaryDataStrategy);
            this.failOnUnknownProperties = config.getProperty("jsonb.fail-on-unknown-properties").filter(Boolean.TRUE::equals).isPresent();
        }

        /**
         * Creates a context-backed reflection provider. This path reuses the
         * application mapper configuration and resolves JSON-B callback
         * components through the Micronaut bean context.
         *
         * @param config The JSON-B configuration
         * @param beanContext The Micronaut bean context
         * @param objectMapper The base object mapper
         * @param serdeIntrospections The base Serde introspections
         * @param serdeConfiguration The effective Serde configuration
         * @param serializationConfiguration The effective serialization configuration
         * @param deserializationConfiguration The effective deserialization configuration
         */
        MicronautJsonb(JsonbConfig config,
                       BeanContext beanContext,
                       ObjectMapper objectMapper,
                       SerdeIntrospections serdeIntrospections,
                       SerdeConfiguration serdeConfiguration,
                       SerializationConfiguration serializationConfiguration,
                       DeserializationConfiguration deserializationConfiguration) {
            this(config, runtimeMapper(config, objectMapper, serdeIntrospections, serdeConfiguration, serializationConfiguration, deserializationConfiguration, JsonbRuntimeCustomizations.of(config)), beanContext);
        }

        private MicronautJsonb(JsonbConfig config, RuntimeMapperAndClose mapperAndClose, BeanContext beanContext) {
            super(config, mapperAndClose.mapper(), mapperAndClose.serdeConfiguration(), mapperAndClose.closeAction());
            this.componentFactory = new JsonbBridgeSupport.ComponentFactory(beanContext);
            this.runtimeIntrospectionResolver = mapperAndClose.resolver();
            this.fallbackRuntimeIntrospectionResolver = mapperAndClose.fallbackResolver();
            this.propertyNamingStrategy = config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null);
            this.propertyVisibilityStrategy = propertyVisibilityStrategy(config);
            this.customizations = mapperAndClose.customizations();
            this.fallbackCodec = new JsonbFallbackCodec(mapperAndClose.fallbackMapper(), mapperAndClose.serdeConfiguration(), binaryDataStrategy);
            this.failOnUnknownProperties = config.getProperty("jsonb.fail-on-unknown-properties").filter(Boolean.TRUE::equals).isPresent();
        }

        private static RuntimeMapperAndClose standaloneRuntimeMapper(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            Map<String, Object> properties = properties(config);
            if (jsonProvider != null) {
                properties.put("micronaut.serde.jsonb.provider", jsonProvider.getClass().getName());
            }
            ObjectMapper.CloseableObjectMapper mapper = ObjectMapper.create(properties, additionalPackages(config));
            SerdeConfiguration serdeConfiguration = mapper.getSerdeRegistry().newEncoderContext(null)
                .getSerdeConfiguration()
                .orElseThrow();
            SerializationConfiguration serializationConfiguration = mapper.getSerdeRegistry().newEncoderContext(null)
                .getSerializationConfiguration()
                .orElseThrow();
            DeserializationConfiguration deserializationConfiguration = mapper.getSerdeRegistry().newDecoderContext(null)
                .getDeserializationConfiguration()
                .orElseThrow();
            JsonbRuntimeCustomizations customizations = JsonbRuntimeCustomizations.of(config);
            RuntimeMapperAndClose runtimeMapper = runtimeMapper(
                config,
                mapper,
                new DefaultSerdeIntrospections(serdeConfiguration),
                serdeConfiguration,
                serializationConfiguration,
                deserializationConfiguration,
                customizations
            );
            return new RuntimeMapperAndClose(runtimeMapper.mapper(), runtimeMapper.fallbackMapper(), runtimeMapper.serdeConfiguration(), runtimeMapper.customizations(), mapper::close, runtimeMapper.resolver(), runtimeMapper.fallbackResolver());
        }

        private static RuntimeMapperAndClose runtimeMapper(JsonbConfig config,
                                                           ObjectMapper objectMapper,
                                                           SerdeIntrospections serdeIntrospections,
                                                           SerdeConfiguration serdeConfiguration,
                                                           SerializationConfiguration serializationConfiguration,
                                                           DeserializationConfiguration deserializationConfiguration,
                                                           JsonbRuntimeCustomizations customizations) {
            JsonbSerdeConfiguration effectiveSerdeConfiguration = new JsonbSerdeConfiguration(config, serdeConfiguration);
            JsonbSerializationConfiguration effectiveSerializationConfiguration = new JsonbSerializationConfiguration(config, serializationConfiguration);
            JsonbDeserializationConfiguration effectiveDeserializationConfiguration = new JsonbDeserializationConfiguration(config, deserializationConfiguration);
            JsonbRuntimeIntrospectionResolver resolver = new JsonbRuntimeIntrospectionResolver(
                config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null),
                propertyOrderStrategy(config),
                propertyVisibilityStrategy(config),
                customizations,
                false
            );
            ObjectMapper mapper = objectMapper.cloneWithConfiguration(
                effectiveSerdeConfiguration,
                effectiveSerializationConfiguration,
                effectiveDeserializationConfiguration,
                serdeIntrospections.withRuntimeIntrospectionResolver(resolver)
            );
            JsonbSerializationConfiguration fallbackSerializationConfiguration = new JsonbSerializationConfiguration(config, serializationConfiguration, true);
            JsonbDeserializationConfiguration fallbackDeserializationConfiguration = new JsonbDeserializationConfiguration(config, deserializationConfiguration, true);
            JsonbRuntimeIntrospectionResolver fallbackResolver = new JsonbRuntimeIntrospectionResolver(
                config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null),
                propertyOrderStrategy(config),
                propertyVisibilityStrategy(config),
                customizations,
                true
            );
            ObjectMapper fallbackMapper = objectMapper.cloneWithConfiguration(
                effectiveSerdeConfiguration,
                fallbackSerializationConfiguration,
                fallbackDeserializationConfiguration,
                serdeIntrospections.withRuntimeIntrospectionResolver(fallbackResolver)
            );
            return new RuntimeMapperAndClose(mapper, fallbackMapper, effectiveSerdeConfiguration, customizations, () -> {
            }, resolver, fallbackResolver);
        }

        @Override
        protected void ensureGeneratedOnlyFeatures() {
            // Reflection provider supports the full JSON-B feature set.
        }

        @Override
        protected <T> @Nullable T readString(String str, Argument<T> argument) {
            if (canReadGeneratedDirectly(argument)) {
                validateGeneratedReadModel(argument);
                return applyTypeVariableValues(super.readString(str, argument), argument);
            }
            @SuppressWarnings("unchecked")
            T value = (T) read(str.getBytes(charset), argument);
            return value;
        }

        @Override
        protected <T> @Nullable T readReader(Reader reader, Argument<T> argument) {
            if (canReadGeneratedDirectly(argument)) {
                validateGeneratedReadModel(argument);
                return applyTypeVariableValues(super.readReader(reader, argument), argument);
            }
            @SuppressWarnings("unchecked")
            T value = (T) read(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), reader));
            return value;
        }

        @Override
        protected <T> @Nullable T readStream(InputStream stream, Argument<T> argument) {
            if (canReadGeneratedDirectly(argument)) {
                validateGeneratedReadModel(argument);
                return applyTypeVariableValues(super.readStream(stream, argument), argument);
            }
            @SuppressWarnings("unchecked")
            T value = (T) read(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), stream));
            return value;
        }

        private boolean canReadGeneratedDirectly(Argument<?> argument) {
            return generatedReadDirectAvailability.computeIfAbsent(argument, this::canReadGeneratedDirectlyUncached);
        }

        private boolean canReadGeneratedDirectlyUncached(Argument<?> argument) {
            Class<?> type = argument.getType();
            JsonbRuntimeBeanIntrospection<?> runtimeModel = runtimeModel(type);
            return type != Object.class
                && propertyVisibilityStrategy == null
                && (runtimeModel == null || runtimeModel.visibilityStrategyType() == null)
                && !customizations.hasDeserializers()
                && (runtimeModel == null || !runtimeModel.requiresFallback())
                && !requiresGenericNumberFallback(argument)
                && canResolveGeneratedSerde(type)
                && canCreateGeneratedDeserializer(argument);
        }

        private void validateGeneratedReadModel(Argument<?> argument) {
            JsonbRuntimeBeanIntrospection<?> runtimeModel = runtimeModel(argument.getType());
            if (runtimeModel != null) {
                runtimeModel.validateReadModel();
            }
        }

        static boolean canResolveGeneratedSerde(Class<?> type) {
            return JsonbReflectionUtil.isJsonScalar(type)
                || hasIntrospection(type);
        }

        private static boolean hasIntrospection(Class<?> type) {
            return BeanIntrospector.SHARED.findIntrospection(type).isPresent();
        }

        @Override
        public void toJson(Object object, Writer writer) throws JsonbException {
            try {
                writer.write(toJson(object));
                writer.flush();
            } catch (IOException e) {
                throw new JsonbException("Cannot write JSON-B value", e);
            }
        }

        @Override
        public void toJson(Object object, Type runtimeType, Writer writer) throws JsonbException {
            try {
                writer.write(toJson(object, runtimeType));
                writer.flush();
            } catch (IOException e) {
                throw new JsonbException("Cannot write JSON-B value", e);
            }
        }

        @Override
        @SuppressWarnings("java:S2583")
        public void toJson(Object object, OutputStream stream) throws JsonbException {
            validateStrictTopLevel(object);
            if (object == null) {
                try {
                    writeGenerated(null, Argument.OBJECT_ARGUMENT, stream);
                } catch (IOException e) {
                    throw new JsonbException("Cannot write JSON-B value", e);
                }
                return;
            }
            Class<?> theClass = object.getClass();
            JsonbRuntimeBeanIntrospection<?> runtimeModel = runtimeModel(theClass);
            validateWriteModel(runtimeModel);
            PropertyVisibilityStrategy visibilityStrategy = visibilityStrategy(theClass, runtimeModel);
            JsonbRuntimeBeanIntrospection<?> effectiveRuntimeModel = runtimeModel(theClass, visibilityStrategy);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Argument<Object> argument = (Argument) Argument.of(theClass);
            if (!canWriteGeneratedDirectly(argument, effectiveRuntimeModel, visibilityStrategy)) {
                writeFallback(object, stream, visibilityStrategy);
            } else {
                try {
                    writeGenerated(object, argument, stream);
                } catch (IOException e) {
                    throw new JsonbException("Cannot write JSON-B value", e);
                }
            }
        }

        @Override
        @SuppressWarnings("java:S2583")
        public void toJson(Object object, Type runtimeType, OutputStream stream) throws JsonbException {
            validateStrictTopLevel(object);
            if (object == null) {
                try {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Argument<Object> argument = (Argument) argument(runtimeType);
                    writeGenerated(null, argument, stream);
                } catch (IOException e) {
                    throw new JsonbException("Cannot write JSON-B value", e);
                }
                return;
            }
            Class<?> theClass = object.getClass();
            JsonbRuntimeBeanIntrospection<?> runtimeModel = runtimeModel(theClass);
            validateWriteModel(runtimeModel);
            PropertyVisibilityStrategy visibilityStrategy = visibilityStrategy(theClass, runtimeModel);
            JsonbRuntimeBeanIntrospection<?> effectiveRuntimeModel = runtimeModel(theClass, visibilityStrategy);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Argument<Object> argument = (Argument) argument(runtimeType);
            if (!canWriteGeneratedDirectly(argument, effectiveRuntimeModel, visibilityStrategy)) {
                writeFallback(object, stream, visibilityStrategy);
            } else {
                try {
                    writeGenerated(object, argument, stream);
                } catch (IOException e) {
                    throw new JsonbException("Cannot write JSON-B value", e);
                }
            }
        }

        @SuppressWarnings({"java:S2583", "java:S3776"})
        private @Nullable Object read(byte[] bytes, Argument<?> argument) {

            Class<?> theClass = argument.getType();
            if (theClass == Object.class || isRawUntypedContainer(argument)) {
                try {
                    JsonNode value = readTree(() -> jsonFactory.createParser(ObjectReadContext.empty(), bytes));
                    return fallbackCodec.readValue(value, Argument.OBJECT_ARGUMENT);
                } catch (IOException | RuntimeException e) {
                    throw new JsonbException("Cannot read JSON-B value", e);
                }
            } else {
                JsonbRuntimeBeanIntrospection<?> runtimeModel = runtimeModel(theClass);
                if (runtimeModel != null) {
                    runtimeModel.validateReadModel();
                }
                PropertyVisibilityStrategy visibilityStrategy = visibilityStrategy(theClass, runtimeModel);
                JsonbRuntimeBeanIntrospection<?> effectiveRuntimeModel = runtimeModel(theClass, visibilityStrategy);
                JsonNode tree;
                try {
                    tree = readTree(() -> jsonFactory.createParser(ObjectReadContext.empty(), bytes));
                } catch (IOException | RuntimeException e) {
                    throw new JsonbException("Cannot read JSON-B value", e);
                }
                validateUnknownProperties(tree, theClass, effectiveRuntimeModel);
                if (visibilityStrategy != null || customizations.hasDeserializers() || (runtimeModel != null && runtimeModel.requiresFallback()) || requiresGenericNumberFallback(argument)) {
                    return readFallback(tree, argument, null);
                }
                try {
                    return applyTypeVariableValues(readGenerated(bytes, argument), argument);
                } catch (IOException e) {
                    return readFallback(tree, argument, e);
                } catch (RuntimeException e) {
                    if (failOnUnknownProperties) {
                        throw new JsonbException("Cannot read JSON-B value", e);
                    }
                    return readFallback(tree, argument, e);
                }
            }
        }

        @SuppressWarnings({"java:S2583", "java:S3776"})
        private @Nullable Object read(Argument<?> argument, ParserSource parserSource) {
            Class<?> theClass = argument.getType();
            if (theClass == Object.class || isRawUntypedContainer(argument)) {
                try {
                    JsonNode value = readTree(parserSource);
                    return fallbackCodec.readValue(value, Argument.OBJECT_ARGUMENT);
                } catch (IOException | RuntimeException e) {
                    throw new JsonbException("Cannot read JSON-B value", e);
                }
            }
            JsonbRuntimeBeanIntrospection<?> runtimeModel = runtimeModel(theClass);
            if (runtimeModel != null) {
                runtimeModel.validateReadModel();
            }
            PropertyVisibilityStrategy visibilityStrategy = visibilityStrategy(theClass, runtimeModel);
            JsonbRuntimeBeanIntrospection<?> effectiveRuntimeModel = runtimeModel(theClass, visibilityStrategy);
            if (visibilityStrategy != null || customizations.hasDeserializers() || (runtimeModel != null && runtimeModel.requiresFallback()) || requiresGenericNumberFallback(argument)) {
                JsonNode value;
                try {
                    value = readTree(parserSource);
                } catch (IOException | RuntimeException e) {
                    throw new JsonbException("Cannot read JSON-B value", e);
                }
                validateUnknownProperties(value, theClass, effectiveRuntimeModel);
                return readFallback(value, argument, null);
            }
            return applyTypeVariableValues(readGenerated(parserSource, argument), argument);
        }

        private static boolean isRawUntypedContainer(Argument<?> argument) {
            return argument.getTypeParameters().length == 0
                && (Collection.class.isAssignableFrom(argument.getType()) || Map.class.isAssignableFrom(argument.getType()));
        }

        private void validateUnknownProperties(JsonNode value, Class<?> type, @Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel) {
            if (!failOnUnknownProperties || JsonbReflectionUtil.isJsonScalar(type)) {
                return;
            }
            if (!value.isObject()) {
                return;
            }
            Set<String> propertyNames = runtimeModel == null ? introspectedPropertyNames(type) : runtimeModel.deserializablePropertyNames();
            if (propertyNames.isEmpty()) {
                return;
            }
            for (Map.Entry<String, JsonNode> entry : value.entries()) {
                if (!propertyNames.contains(entry.getKey())) {
                    throw new JsonbException("Unknown JSON-B property " + entry.getKey() + " for type " + type.getName());
                }
            }
        }

        private Set<String> introspectedPropertyNames(Class<?> type) {
            try {
                BeanIntrospection<?> introspection = BeanIntrospector.SHARED.getIntrospection(type);
                Set<String> names = new HashSet<>();
                for (BeanProperty<?, ?> property : introspection.getBeanProperties()) {
                    names.add(propertyName(property.getName()));
                }
                return names;
            } catch (IntrospectionException e) {
                return Set.of();
            }
        }

        private String propertyName(String name) {
            if (propertyNamingStrategy == null) {
                return name;
            }
            return JsonbReflectionUtil.translateName(name, propertyNamingStrategy);
        }

        private <T> void writeGenerated(@Nullable T object, Argument<T> argument, OutputStream stream) throws IOException {
            super.writeGenerated(object, argument, () -> jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), new NonClosingOutputStream(stream)));
        }

        private <T> @Nullable T readGenerated(byte[] bytes, Argument<T> argument) throws IOException {
            return super.readGenerated(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), bytes));
        }

        private @Nullable Object readGenerated(ParserSource parserSource, Argument<?> argument) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Argument<Object> generatedArgument = (Argument) argument;
            return super.readGenerated(generatedArgument, parserSource);
        }

        private boolean canWriteGeneratedDirectly(Argument<Object> argument,
                                                  @Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel,
                                                  @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            return generatedWriteDirectAvailability.computeIfAbsent(
                new GeneratedWriteKey(argument, runtimeModel == null ? null : runtimeModel.getBeanType(), visibilityStrategy != null),
                _ -> canWriteGeneratedDirectlyUncached(argument, runtimeModel, visibilityStrategy)
            );
        }

        private boolean canWriteGeneratedDirectlyUncached(Argument<Object> argument,
                                                          @Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel,
                                                          @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            return visibilityStrategy == null
                && !customizations.hasSerializers()
                && runtimeModel != null
                && runtimeModel.canWriteGeneratedDirectly(argument)
                && canCreateGeneratedSerializer(argument);
        }

        private void validateWriteModel(@Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel) {
            if (runtimeModel != null) {
                runtimeModel.validateWriteModel();
            }
        }

        static boolean requiresGenericNumberFallback(Argument<?> argument) {
            if (!argument.hasTypeVariables()) {
                return false;
            }
            for (Argument<?> typeVariable : argument.getTypeVariables().values()) {
                if (Number.class.isAssignableFrom(typeVariable.getType())) {
                    return true;
                }
            }
            return false;
        }

        private void writeFallback(@Nullable Object object,
                                   OutputStream stream,
                                   @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            try {
                if (visibilityStrategy == null && canUseRuntimeObjectMapping(object, customizations.hasSerializers())) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Argument<Object> argument = (Argument) Argument.of(Objects.requireNonNull(object).getClass());
                    writeFallbackValue(stream, argument, object);
                } else {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Argument<Object> argument = object == null ? Argument.OBJECT_ARGUMENT : (Argument) Argument.of(object.getClass());
                    writeFallbackValue(stream, argument, object);
                }
            } catch (IOException | RuntimeException fallbackFailure) {
                throw new JsonbException("Cannot write JSON-B value", fallbackFailure);
            }
        }

        private <T> void writeFallbackValue(OutputStream stream, Argument<T> argument, @Nullable T object) throws IOException {
            try (tools.jackson.core.JsonGenerator generator = jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), new NonClosingOutputStream(stream))) {
                JsonNode customized = customizations.serialize(object, fallbackCodec);
                if (customized != null) {
                    fallbackCodec.writeTree(generator, customized);
                } else {
                    fallbackCodec.writeValue(generator, argument, object);
                }
            }
        }

        private @Nullable Object readFallback(JsonNode value,
                                              Argument<?> argument,
                                              @Nullable Exception failure) {
            try {
                Object customized = customizations.deserialize(value, argument.asType(), fallbackCodec);
                if (customized != null) {
                    return customized;
                }
                return applyTypeVariableValues(fallbackCodec.readValue(value, argument), argument);
            } catch (IOException | RuntimeException fallbackFailure) {
                JsonbException exception = failure == null
                    ? new JsonbException("Cannot read JSON-B value", fallbackFailure)
                    : new JsonbException("Cannot read JSON-B value", failure);
                if (failure != null) {
                    exception.addSuppressed(fallbackFailure);
                }
                throw exception;
            }
        }

        private static boolean canUseRuntimeObjectMapping(@Nullable Object object,
                                                          boolean hasCustomizers) {
            return object != null && canUseRuntimeObjectMapping(object.getClass(), hasCustomizers);
        }

        private static boolean canUseRuntimeObjectMapping(Class<?> type,
                                                          boolean hasCustomizers) {
            return !hasCustomizers
                && !JsonbReflectionUtil.isJsonScalar(type)
                && !JsonValue.class.isAssignableFrom(type)
                && !Optional.class.isAssignableFrom(type)
                && !type.isArray()
                && !Collection.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type)
                && !hasIntrospection(type)
                && !type.isAnonymousClass()
                && !type.isLocalClass();
        }

        private <T> @Nullable T applyTypeVariableValues(@Nullable T value, Argument<?> argument) {
            if (value == null || !argument.hasTypeVariables()) {
                return value;
            }
            JsonbRuntimeBeanIntrospection<?> runtimeModel = fallbackRuntimeIntrospectionResolver.introspection(argument.getType());
            for (JsonbRuntimeProperty<?> property : runtimeModel.runtimeProperties()) {
                Type type = property.deserializationType();
                if (type instanceof java.lang.reflect.TypeVariable<?> typeVariable) {
                    Argument<?> target = argument.getTypeVariables().get(typeVariable.getName());
                    if (target == null && argument.getTypeVariables().size() == 1) {
                        target = argument.getTypeVariables().values().iterator().next();
                    }
                    if (target != null) {
                        applyTypeVariableValue(value, property, target);
                    }
                }
            }
            return value;
        }

        private static <T> void applyTypeVariableValue(T bean, JsonbRuntimeProperty<?> property, Argument<?> target) {
            @SuppressWarnings("unchecked")
            JsonbRuntimeProperty<T> typedProperty = (JsonbRuntimeProperty<T>) property;
            Object current = typedProperty.getUnsafe(bean);
            if (current == null) {
                return;
            }
            if (target.getType().isInstance(current)) {
                return;
            }
            Object converted = convertTypeVariableValue(current, target.getType());
            if (converted != current) {
                typedProperty.setUnsafe(bean, converted);
            }
        }

        @SuppressWarnings("java:S3776")
        private static Object convertTypeVariableValue(Object value, Class<?> targetType) {
            if (value instanceof Number number) {
                if (targetType == Integer.class || targetType == int.class) {
                    return checkedInteger(number);
                }
                if (targetType == Long.class || targetType == long.class) {
                    return checkedLong(number);
                }
                if (targetType == Double.class || targetType == double.class) {
                    return number.doubleValue();
                }
                if (targetType == Float.class || targetType == float.class) {
                    return number.floatValue();
                }
                if (targetType == Short.class || targetType == short.class) {
                    return checkedShort(number);
                }
                if (targetType == Byte.class || targetType == byte.class) {
                    return checkedByte(number);
                }
            }
            return value;
        }

        private static int checkedInteger(Number number) {
            BigInteger integer = integralValue(number);
            if (integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new JsonbException("JSON-B numeric value is out of range for Integer: " + number);
            }
            return integer.intValue();
        }

        private static long checkedLong(Number number) {
            BigInteger integer = integralValue(number);
            if (integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new JsonbException("JSON-B numeric value is out of range for Long: " + number);
            }
            return integer.longValue();
        }

        private static short checkedShort(Number number) {
            BigInteger integer = integralValue(number);
            if (integer.compareTo(BigInteger.valueOf(Short.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Short.MAX_VALUE)) > 0) {
                throw new JsonbException("JSON-B numeric value is out of range for Short: " + number);
            }
            return integer.shortValue();
        }

        private static byte checkedByte(Number number) {
            BigInteger integer = integralValue(number);
            if (integer.compareTo(BigInteger.valueOf(Byte.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Byte.MAX_VALUE)) > 0) {
                throw new JsonbException("JSON-B numeric value is out of range for Byte: " + number);
            }
            return integer.byteValue();
        }

        private static BigInteger integralValue(Number number) {
            try {
                if (number instanceof BigInteger integer) {
                    return integer;
                }
                if (number instanceof BigDecimal decimal) {
                    return decimal.toBigIntegerExact();
                }
                if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                    return BigInteger.valueOf(number.longValue());
                }
                if (number instanceof Float || number instanceof Double) {
                    return BigDecimal.valueOf(number.doubleValue()).toBigIntegerExact();
                }
                return new BigDecimal(number.toString()).toBigIntegerExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new JsonbException("JSON-B numeric value is not an integral value: " + number, e);
            }
        }

        private JsonNode readTree(ParserSource parserSource) throws IOException {
            try (tools.jackson.core.JsonParser parser = parserSource.createParser()) {
                return fallbackCodec.readTree(parser);
            }
        }

        private @Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel(Class<?> type) {
            if (type == Object.class
                || JsonbReflectionUtil.isJsonScalar(type)
                || JsonValue.class.isAssignableFrom(type)
                || Optional.class.isAssignableFrom(type)
                || type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || type.isAnonymousClass()
                || type.isLocalClass()) {
                return null;
            }
            return runtimeIntrospectionResolver.introspection(type);
        }

        private @Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel(Class<?> type, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            if (visibilityStrategy == null || visibilityStrategy == propertyVisibilityStrategy) {
                return runtimeModel(type);
            }
            return visibilityIntrospections.computeIfAbsent(
                type,
                beanType -> fallbackRuntimeIntrospectionResolver.introspection(beanType, visibilityStrategy)
            );
        }

        private @Nullable PropertyVisibilityStrategy visibilityStrategy(Class<?> type, @Nullable JsonbRuntimeBeanIntrospection<?> runtimeModel) {
            if (propertyVisibilityStrategy != null) {
                return propertyVisibilityStrategy;
            }
            Class<? extends PropertyVisibilityStrategy> strategyType = runtimeModel == null
                ? JsonbRuntimeBeanIntrospection.visibilityStrategyType(type)
                : runtimeModel.visibilityStrategyType();
            return strategyType == null ? null : visibilityStrategies.computeIfAbsent(strategyType, this::component);
        }

        private <T extends PropertyVisibilityStrategy> T component(Class<T> type) {
            if (componentFactory != null) {
                return componentFactory.get(type);
            }
            return JsonbBridgeSupport.ComponentFactory.cdiBean(type).orElseGet(() -> JsonbReflectionUtil.instantiate(type));
        }

        private static @Nullable PropertyVisibilityStrategy propertyVisibilityStrategy(JsonbConfig config) {
            return config.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY)
                .filter(PropertyVisibilityStrategy.class::isInstance)
                .map(PropertyVisibilityStrategy.class::cast)
                .orElse(null);
        }

        private record RuntimeMapperAndClose(ObjectMapper mapper,
                                             ObjectMapper fallbackMapper,
                                             SerdeConfiguration serdeConfiguration,
                                             JsonbRuntimeCustomizations customizations,
                                             Runnable closeAction,
                                             JsonbRuntimeIntrospectionResolver resolver,
                                             JsonbRuntimeIntrospectionResolver fallbackResolver) {
        }

        private record GeneratedWriteKey(Argument<?> argument,
                                         @Nullable Class<?> beanType,
                                         boolean hasVisibilityStrategy) {
        }
    }
}
