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
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeInfo;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.spi.JsonProvider;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import tools.jackson.core.ObjectReadContext;

/**
 * Micronaut Serialization backed JSON-B provider with reflection fallback behavior for JSON-B compatibility.
 *
 * @since 3.0.1
 */
public final class MicronautJsonbReflectionProvider extends MicronautJsonbProvider {
    @Override
    public JsonbBuilder create() {
        return new Builder();
    }

    static Jsonb create(JsonbConfig config,
                        BeanContext beanContext,
                        ObjectMapper objectMapper,
                        SerdeConfiguration serdeConfiguration,
                        SerializationConfiguration serializationConfiguration,
                        DeserializationConfiguration deserializationConfiguration) {
        return new MicronautJsonb(config, beanContext, objectMapper, serdeConfiguration, serializationConfiguration, deserializationConfiguration);
    }

    private static final class Builder extends MicronautJsonbProvider.Builder {
        @Override
        protected Jsonb build(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            return new MicronautJsonb(config, jsonProvider);
        }
    }

    private static final class MicronautJsonb extends MicronautJsonbProvider.MicronautJsonb {
        private final @Nullable Object propertyNamingStrategy;
        private final String propertyOrderStrategy;
        private final @Nullable PropertyVisibilityStrategy propertyVisibilityStrategy;
        private final JsonbRuntimeCustomizations customizations;
        private final boolean serializeNullValues;
        private final boolean failOnUnknownProperties;
        private final JsonbBridgeSupport.@Nullable ComponentFactory componentFactory;

        MicronautJsonb(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            super(config, jsonProvider);
            this.componentFactory = null;
            this.propertyNamingStrategy = config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null);
            this.propertyOrderStrategy = propertyOrderStrategy(config);
            this.propertyVisibilityStrategy = propertyVisibilityStrategy(config);
            this.customizations = JsonbRuntimeCustomizations.of(config, mapper);
            this.serializeNullValues = config.getProperty(JsonbConfig.NULL_VALUES).filter(Boolean.TRUE::equals).isPresent();
            this.failOnUnknownProperties = config.getProperty("jsonb.fail-on-unknown-properties").filter(Boolean.TRUE::equals).isPresent();
        }

        MicronautJsonb(JsonbConfig config,
                       BeanContext beanContext,
                       ObjectMapper objectMapper,
                       SerdeConfiguration serdeConfiguration,
                       SerializationConfiguration serializationConfiguration,
                       DeserializationConfiguration deserializationConfiguration) {
            super(config, objectMapper, serdeConfiguration, serializationConfiguration, deserializationConfiguration);
            this.componentFactory = new JsonbBridgeSupport.ComponentFactory(beanContext);
            this.propertyNamingStrategy = config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY).orElse(null);
            this.propertyOrderStrategy = propertyOrderStrategy(config);
            this.propertyVisibilityStrategy = propertyVisibilityStrategy(config);
            this.customizations = JsonbRuntimeCustomizations.of(config, mapper);
            this.serializeNullValues = config.getProperty(JsonbConfig.NULL_VALUES).filter(Boolean.TRUE::equals).isPresent();
            this.failOnUnknownProperties = config.getProperty("jsonb.fail-on-unknown-properties").filter(Boolean.TRUE::equals).isPresent();
        }

        @Override
        protected void ensureGeneratedOnlyFeatures() {
        }

        @Override
        protected <T> @Nullable T readString(String str, Argument<T> argument) {
            if (canReadGeneratedDirectly(argument)) {
                validateGeneratedReadModel(argument);
                return super.readString(str, argument);
            }
            @SuppressWarnings("unchecked")
            T value = (T) read(str.getBytes(charset), argument);
            return value;
        }

        @Override
        protected <T> @Nullable T readReader(Reader reader, Argument<T> argument) {
            if (canReadGeneratedDirectly(argument)) {
                validateGeneratedReadModel(argument);
                return super.readReader(reader, argument);
            }
            @SuppressWarnings("unchecked")
            T value = (T) read(readAll(reader).getBytes(charset), argument);
            return value;
        }

        @Override
        protected <T> @Nullable T readStream(InputStream stream, Argument<T> argument) {
            if (canReadGeneratedDirectly(argument)) {
                validateGeneratedReadModel(argument);
                return super.readStream(stream, argument);
            }
            try {
                @SuppressWarnings("unchecked")
                T value = (T) read(stream.readAllBytes(), argument);
                return value;
            } catch (IOException e) {
                throw new JsonbException("Cannot read JSON-B value", e);
            }
        }

        private boolean canReadGeneratedDirectly(Argument<?> argument) {
            Class<?> type = argument.getType();
            return type != Object.class
                && !JsonbTypeInfoSupport.hasTypeInfo(type)
                && propertyVisibilityStrategy == null
                && visibilityStrategy(type) == null
                && !customizations.hasDeserializers()
                && !requiresFallback(type)
                && !requiresGenericNumberFallback(argument)
                && canResolveGeneratedSerde(type)
                && canCreateGeneratedDeserializer(argument);
        }

        private void validateGeneratedReadModel(Argument<?> argument) {
            ReflectionFallback.validateObjectModel(argument.getType(), propertyNamingStrategy);
            ReflectionFallback.validateCreatorModel(argument.getType());
            ReflectionFallback.validateDefaultConstructorAccess(argument.getType());
            JsonbTypeInfoSupport.validateTypeInfoModel(argument.getType());
        }

        private static boolean canResolveGeneratedSerde(Class<?> type) {
            return ReflectionFallback.isJsonScalar(type)
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
        public void toJson(Object object, OutputStream stream) throws JsonbException {
            validateStrictTopLevel(object);
            validateObjectModel(object);
            JsonbTypeInfoSupport.validateTypeInfoModel(object.getClass());
            PropertyVisibilityStrategy visibilityStrategy = visibilityStrategy(object);
            if (visibilityStrategy != null || customizations.hasSerializers() || PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy) || requiresFallback(object)) {
                writeFallback(object, stream, null, visibilityStrategy);
                return;
            }
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Argument<Object> argument = (Argument) Argument.of(object.getClass());
                writeGenerated(object, argument, stream);
            } catch (IOException | RuntimeException e) {
                writeFallback(object, stream, e);
            }
        }

        @Override
        public void toJson(Object object, Type runtimeType, OutputStream stream) throws JsonbException {
            validateStrictTopLevel(object);
            validateObjectModel(object);
            JsonbTypeInfoSupport.validateTypeInfoModel(object.getClass());
            PropertyVisibilityStrategy visibilityStrategy = visibilityStrategy(object);
            if (visibilityStrategy != null || customizations.hasSerializers() || PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy) || requiresFallback(object)) {
                writeFallback(object, stream, null, visibilityStrategy);
                return;
            }
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Argument<Object> argument = (Argument) argument(runtimeType);
                writeGenerated(object, argument, stream);
            } catch (IOException | RuntimeException e) {
                writeFallback(object, stream, e);
            }
        }

        private @Nullable Object read(byte[] bytes, Argument<?> argument) {

            if (argument.getType() == Object.class) {
                try {
                    Object value = mapper.readValue(new ByteArrayInputStream(bytes), Argument.of(Object.class));
                    return ReflectionFallback.normalizeUntypedValue(value);
                } catch (IOException | RuntimeException e) {
                    throw new JsonbException("Cannot read JSON-B value", e);
                }
            } else {
                ReflectionFallback.validateObjectModel(argument.getType(), propertyNamingStrategy);
                ReflectionFallback.validateCreatorModel(argument.getType());
                ReflectionFallback.validateDefaultConstructorAccess(argument.getType());
                JsonbTypeInfoSupport.validateTypeInfoModel(argument.getType());
                validateUnknownProperties(bytes, argument.getType());
                PropertyVisibilityStrategy visibilityStrategy = propertyVisibilityStrategy != null ? propertyVisibilityStrategy : visibilityStrategy(argument.getType());
                if (visibilityStrategy != null || customizations.hasDeserializers() || requiresFallback(argument.getType()) || requiresGenericNumberFallback(argument)) {
                    return readFallback(bytes, argument, null, visibilityStrategy);
                }
                try {
                    return readGenerated(bytes, argument);
                } catch (IOException e) {
                    return readFallback(bytes, argument, e);
                } catch (RuntimeException e) {
                    if (failOnUnknownProperties) {
                        throw new JsonbException("Cannot read JSON-B value", e);
                    }
                    return readFallback(bytes, argument, e);
                }
            }
        }

        private void validateUnknownProperties(byte[] bytes, Class<?> type) {
            if (!failOnUnknownProperties || ReflectionFallback.isJsonScalar(type)) {
                return;
            }
            try {
                Object value = mapper.readValue(new ByteArrayInputStream(bytes), Argument.of(Object.class));
                if (!(value instanceof Map<?, ?> map)) {
                    return;
                }
                Set<String> propertyNames = introspectedPropertyNames(type);
                if (propertyNames.isEmpty()) {
                    return;
                }
                for (Object key : map.keySet()) {
                    if (!propertyNames.contains(String.valueOf(key))) {
                        throw new JsonbException("Unknown JSON-B property " + key + " for type " + type.getName());
                    }
                }
            } catch (JsonbException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot read JSON-B value", e);
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
            return ReflectionFallback.translateName(name, propertyNamingStrategy);
        }

        private <T> void writeGenerated(@Nullable T object, Argument<T> argument, OutputStream stream) throws IOException {
            ByteArrayOutputStream generated = new ByteArrayOutputStream();
            super.writeGenerated(object, argument, () -> jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), generated));
            ByteArrayOutputStream buffer = generated;
            if (object != null) {
                buffer = JsonbTypeInfoSupport.enrichSerializedTypeInfo(mapper, object.getClass(), buffer);
            }
            buffer.writeTo(stream);
        }

        private <T> @Nullable T readGenerated(byte[] bytes, Argument<T> argument) throws IOException {
            argument = JsonbTypeInfoSupport.resolveDeserializationArgument(mapper, bytes, argument);
            return super.readGenerated(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), bytes));
        }

        private void validateObjectModel(@Nullable Object object) {
            if (object != null && !ReflectionFallback.isJsonScalar(object.getClass())) {
                ReflectionFallback.validateObjectModel(object.getClass(), propertyNamingStrategy);
            }
        }

        private static boolean requiresFallback(@Nullable Object object) {
            return object != null && !ReflectionFallback.isJsonScalar(object.getClass()) && requiresFallback(object.getClass());
        }

        private static boolean requiresFallback(Class<?> type) {
            return requiresAnonymousFallback(type)
                || ReflectionFallback.hasTransientProperty(type)
                || ReflectionFallback.hasPropertyCustomization(type)
                || ReflectionFallback.hasAsymmetricAccessorNames(type)
                || ReflectionFallback.hasAsymmetricAccessorFormats(type)
                || ReflectionFallback.hasStaticBackedAccessor(type);
        }

        private static boolean requiresAnonymousFallback(Class<?> type) {
            if (!type.isAnonymousClass() && !type.isLocalClass()) {
                return false;
            }
            Class<?> superclass = type.getSuperclass();
            return superclass != null && superclass.getSuperclass() != null && superclass.getSuperclass() != Object.class;
        }

        private static boolean requiresGenericNumberFallback(Argument<?> argument) {
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

        private void writeFallback(@Nullable Object object, OutputStream stream, Exception failure) {
            writeFallback(object, stream, failure, null);
        }

        private void writeFallback(@Nullable Object object, OutputStream stream, @Nullable Exception failure, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            try {
                mapper.writeValue(stream, ReflectionFallback.toJsonValue(object, propertyNamingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, serializeNullValues, strictIJson));
            } catch (IOException | RuntimeException fallbackFailure) {
                JsonbException exception = failure == null
                    ? new JsonbException("Cannot write JSON-B value", fallbackFailure)
                    : new JsonbException("Cannot write JSON-B value", failure);
                if (failure != null) {
                    exception.addSuppressed(fallbackFailure);
                }
                throw exception;
            }
        }

        private @Nullable Object readFallback(byte[] bytes, Argument<?> argument, Exception failure) {
            return readFallback(bytes, argument, failure, null);
        }

        private @Nullable Object readFallback(byte[] bytes, Argument<?> argument, @Nullable Exception failure, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            try {
                Object value = mapper.readValue(new ByteArrayInputStream(bytes), Argument.of(Object.class));
                return ReflectionFallback.fromJsonValue(value, argument.asType(), propertyNamingStrategy, visibilityStrategy, customizations);
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

        private @Nullable PropertyVisibilityStrategy visibilityStrategy(@Nullable Object object) {
            if (object == null || ReflectionFallback.isJsonScalar(object.getClass())) {
                return null;
            }
            if (propertyVisibilityStrategy != null) {
                return propertyVisibilityStrategy;
            }
            return visibilityStrategy(object.getClass());
        }

        private @Nullable PropertyVisibilityStrategy visibilityStrategy(Class<?> type) {
            Class<?> current = type;
            while (current != Object.class && current != null) {
                JsonbVisibility annotation = current.getAnnotation(JsonbVisibility.class);
                if (annotation != null) {
                    return component(annotation.value());
                }
                if (current.getPackage() != null) {
                    annotation = current.getPackage().getAnnotation(JsonbVisibility.class);
                    if (annotation != null) {
                        return component(annotation.value());
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private <T extends PropertyVisibilityStrategy> T component(Class<T> type) {
            if (componentFactory != null) {
                return componentFactory.get(type);
            }
            return JsonbBridgeSupport.ComponentFactory.cdiBean(type).orElseGet(() -> ReflectionFallback.instantiate(type));
        }

        private static @Nullable PropertyVisibilityStrategy propertyVisibilityStrategy(JsonbConfig config) {
            return config.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY)
                .filter(PropertyVisibilityStrategy.class::isInstance)
                .map(PropertyVisibilityStrategy.class::cast)
                .orElse(null);
        }

        private static String readAll(Reader reader) {
            try {
                StringWriter writer = new StringWriter();
                reader.transferTo(writer);
                return writer.toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class JsonbRuntimeCustomizations {
        private final JsonMapper mapper;
        private final List<ConfigAdapter> adapters;
        private final List<ConfigSerializer> serializers;
        private final List<ConfigDeserializer> deserializers;

        private JsonbRuntimeCustomizations(JsonMapper mapper,
                                           List<ConfigAdapter> adapters,
                                           List<ConfigSerializer> serializers,
                                           List<ConfigDeserializer> deserializers) {
            this.mapper = mapper;
            this.adapters = adapters;
            this.serializers = serializers;
            this.deserializers = deserializers;
        }

        static JsonbRuntimeCustomizations of(JsonbConfig config, JsonMapper mapper) {
            List<ConfigAdapter> adapters = configValues(config, JsonbConfig.ADAPTERS, JsonbAdapter.class)
                .stream()
                .map(adapter -> ConfigAdapter.of(adapterType(adapter.getClass(), 0), adapterType(adapter.getClass(), 1), adapter))
                .toList();
            List<ConfigSerializer> serializers = configValues(config, JsonbConfig.SERIALIZERS, JsonbSerializer.class)
                .stream()
                .map(serializer -> ConfigSerializer.of(componentType(serializer.getClass(), JsonbSerializer.class), serializer))
                .toList();
            List<ConfigDeserializer> deserializers = configValues(config, JsonbConfig.DESERIALIZERS, JsonbDeserializer.class)
                .stream()
                .map(deserializer -> ConfigDeserializer.of(componentType(deserializer.getClass(), JsonbDeserializer.class), deserializer))
                .toList();
            return new JsonbRuntimeCustomizations(mapper, adapters, serializers, deserializers);
        }

        boolean hasSerializers() {
            return !adapters.isEmpty() || !serializers.isEmpty();
        }

        boolean hasDeserializers() {
            return !adapters.isEmpty() || !deserializers.isEmpty();
        }

        @Nullable Object serialize(@Nullable Object value,
                                   @Nullable Object namingStrategy,
                                   String propertyOrderStrategy,
                                   @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                   boolean includeNullValues,
                                   boolean strictIJson,
                                   IdentityHashMap<Object, Boolean> seen) {
            if (value == null) {
                return null;
            }
            for (ConfigSerializer serializer : serializers) {
                if (matches(serializer.type(), value.getClass())) {
                    try {
                        return JsonbBridgeSupport.writeWithJsonbSerializer(serializer.serializer(), value, mapper);
                    } catch (IOException e) {
                        throw new JsonbException("Cannot serialize JSON-B value with configured serializer", e);
                    }
                }
            }
            for (ConfigAdapter adapter : adapters) {
                if (matches(adapter.sourceType(), value.getClass())) {
                    try {
                        Object adapted = adapter.adapter().adaptToJson(value);
                        return ReflectionFallback.toJsonValue(adapted, namingStrategy, propertyOrderStrategy, visibilityStrategy, this, includeNullValues, strictIJson, seen);
                    } catch (Exception e) {
                        throw new JsonbException("Cannot adapt JSON-B value for serialization", e);
                    }
                }
            }
            return null;
        }

        @Nullable Object deserialize(@Nullable Object value,
                                     Type targetType,
                                     @Nullable Object namingStrategy,
                                     @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            for (ConfigDeserializer deserializer : deserializers) {
                if (matches(targetType, deserializer.type())) {
                    try (jakarta.json.stream.JsonParser parser = JsonbBridgeSupport.parserFor(mapper, value)) {
                        return deserializer.deserializer().deserialize(parser, new JsonbDeserializationContext(mapper), targetType);
                    } catch (IOException | RuntimeException e) {
                        throw new JsonbException("Cannot deserialize JSON-B value with configured deserializer", e);
                    }
                }
            }
            for (ConfigAdapter adapter : adapters) {
                if (matches(targetType, adapter.sourceType())) {
                    Object adapted = ReflectionFallback.fromJsonValue(value, adapter.targetType(), namingStrategy, visibilityStrategy, this);
                    try {
                        return adapter.adapter().adaptFromJson(adapted);
                    } catch (Exception e) {
                        throw new JsonbException("Cannot adapt JSON-B value for deserialization", e);
                    }
                }
            }
            return null;
        }

        @Nullable Object serializeWith(JsonbSerializer<Object> serializer, @Nullable Object value) {
            if (value == null) {
                return null;
            }
            try {
                return JsonbBridgeSupport.writeWithJsonbSerializer(serializer, value, mapper);
            } catch (IOException e) {
                throw new JsonbException("Cannot serialize JSON-B value with custom serializer", e);
            }
        }

        @Nullable Object deserializeWith(JsonbDeserializer<Object> deserializer, @Nullable Object value, Type targetType) {
            try (jakarta.json.stream.JsonParser parser = JsonbBridgeSupport.parserFor(mapper, value)) {
                if (value instanceof Collection<?>) {
                    parser.next();
                }
                return deserializer.deserialize(parser, new JsonbDeserializationContext(mapper), targetType);
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot deserialize JSON-B value with custom deserializer", e);
            }
        }

        private static <T> List<T> configValues(JsonbConfig config, String key, Class<T> type) {
            Optional<Object> property = config.getProperty(key);
            if (property.isEmpty()) {
                return new ArrayList<>(0);
            }
            Object value = property.get();
            if (type.isInstance(value)) {
                List<T> values = new ArrayList<>(1);
                values.add(type.cast(value));
                return values;
            }
            if (value instanceof Collection<?> collection) {
                List<T> values = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    if (type.isInstance(item)) {
                        values.add(type.cast(item));
                    }
                }
                return values;
            }
            if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                List<T> values = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    Object item = java.lang.reflect.Array.get(value, i);
                    if (type.isInstance(item)) {
                        values.add(type.cast(item));
                    }
                }
                return values;
            }
            return new ArrayList<>(0);
        }

        private static Type adapterType(Class<?> type, int index) {
            Type adapterType = componentInterface(type, JsonbAdapter.class);
            if (adapterType instanceof ParameterizedType parameterizedType) {
                Type[] arguments = parameterizedType.getActualTypeArguments();
                if (arguments.length > index) {
                    return arguments[index];
                }
            }
            return Object.class;
        }

        private static Type componentType(Class<?> type, Class<?> componentInterface) {
            Type genericInterface = componentInterface(type, componentInterface);
            if (genericInterface instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length > 0) {
                return parameterizedType.getActualTypeArguments()[0];
            }
            return Object.class;
        }

        private static @Nullable Type componentInterface(Class<?> type, Class<?> componentInterface) {
            for (Type genericInterface : type.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == componentInterface) {
                    return parameterizedType;
                }
                if (genericInterface instanceof Class<?> interfaceClass) {
                    Type match = componentInterface(interfaceClass, componentInterface);
                    if (match != null) {
                        return match;
                    }
                }
            }
            Type genericSuperclass = type.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> superClass) {
                if (superClass == componentInterface) {
                    return parameterizedType;
                }
                return componentInterface(superClass, componentInterface);
            }
            if (genericSuperclass instanceof Class<?> superClass && superClass != Object.class) {
                return componentInterface(superClass, componentInterface);
            }
            return null;
        }

        private static boolean matches(Type configuredType, Class<?> runtimeType) {
            return erasedType(configuredType).isAssignableFrom(runtimeType);
        }

        private static boolean matches(Type targetType, Type configuredType) {
            return erasedType(targetType).isAssignableFrom(erasedType(configuredType))
                || erasedType(configuredType).isAssignableFrom(erasedType(targetType));
        }

        private static Class<?> erasedType(Type type) {
            return switch (type) {
                case Class<?> clazz -> clazz;
                case
                    ParameterizedType parameterizedType when parameterizedType.getRawType() instanceof Class<?> clazz ->
                    clazz;
                case GenericArrayType genericArrayType ->
                    Array.newInstance(erasedType(genericArrayType.getGenericComponentType()), 0).getClass();
                default -> Object.class;
            };
        }

        private record ConfigAdapter(Type sourceType, Type targetType, JsonbAdapter<Object, Object> adapter) {
            @SuppressWarnings("unchecked")
            static ConfigAdapter of(Type sourceType, Type targetType, JsonbAdapter<?, ?> adapter) {
                return new ConfigAdapter(sourceType, targetType, (JsonbAdapter<Object, Object>) adapter);
            }
        }

        private record ConfigSerializer(Type type, JsonbSerializer<Object> serializer) {
            @SuppressWarnings("unchecked")
            static ConfigSerializer of(Type type, JsonbSerializer<?> serializer) {
                return new ConfigSerializer(type, (JsonbSerializer<Object>) serializer);
            }
        }

        private record ConfigDeserializer(Type type, JsonbDeserializer<Object> deserializer) {
            @SuppressWarnings("unchecked")
            static ConfigDeserializer of(Type type, JsonbDeserializer<?> deserializer) {
                return new ConfigDeserializer(type, (JsonbDeserializer<Object>) deserializer);
            }
        }
    }

    private record JsonbDeserializationContext(
        JsonMapper mapper) implements DeserializationContext {

        @Override
            public <T> T deserialize(Class<T> type, jakarta.json.stream.JsonParser parser) {
                return deserialize((Type) type, parser);
            }

            @Override
            @SuppressWarnings("TypeParameterUnusedInFormals")
            public <T> T deserialize(Type type, jakarta.json.stream.JsonParser parser) {
                try {
                    Object value = JsonbBridgeSupport.parseNext(parser);
                    @SuppressWarnings("unchecked")
                    T result = (T) mapper.readValue(JsonbBridgeSupport.writeJson(mapper, value), Argument.of(type));
                    return Objects.requireNonNull(result, "JSON-B deserialization context result");
                } catch (IOException e) {
                    throw new JsonbException("Cannot deserialize JSON-B context value", e);
                }
            }
        }

    private static final class JsonbTypeInfoSupport {
        private JsonbTypeInfoSupport() {
        }

        static void validateTypeInfoModel(@Nullable Class<?> type) {
            if (type == null || ReflectionFallback.isJsonScalar(type)) {
                return;
            }
            List<Class<?>> annotatedTypes = annotatedTypeInfoTypes(type);
            ensureSingleTypeInfoChain(annotatedTypes, type);
            for (Class<?> annotatedType : annotatedTypes) {
                JsonbTypeInfo typeInfo = annotatedType.getAnnotation(JsonbTypeInfo.class);
                if (typeInfo == null) {
                    continue;
                }
                validateSubtypeAliases(annotatedType, typeInfo);
                validatePropertyName(annotatedType, typeInfo.key());
            }
        }

        static boolean hasTypeInfo(Class<?> type) {
            return !annotatedTypeInfoTypes(type).isEmpty();
        }

        static ByteArrayOutputStream enrichSerializedTypeInfo(JsonMapper mapper, Class<?> type, ByteArrayOutputStream buffer) throws IOException {
            List<TypeInfoProperty> properties = typeInfoProperties(type);
            if (properties.isEmpty()) {
                return buffer;
            }
            Object value = mapper.readValue(new ByteArrayInputStream(buffer.toByteArray()), Argument.of(Object.class));
            if (!(value instanceof Map<?, ?> map)) {
                return buffer;
            }
            LinkedHashMap<String, Object> enriched = new LinkedHashMap<>();
            for (TypeInfoProperty property : properties) {
                enriched.put(property.key(), property.alias());
            }
            for (String propertyName : declaredPropertyOrder(type)) {
                if (map.containsKey(propertyName) && !enriched.containsKey(propertyName)) {
                    enriched.put(propertyName, map.get(propertyName));
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!enriched.containsKey(key)) {
                    enriched.put(key, entry.getValue());
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mapper.writeValue(outputStream, enriched);
            return outputStream;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static <T> Argument<T> resolveDeserializationArgument(JsonMapper mapper, byte[] bytes, Argument<T> argument) throws IOException {
            Class<?> resolved = resolveDeserializationType(mapper, bytes, argument.getType());
            if (resolved == argument.getType()) {
                return argument;
            }
            return (Argument) Argument.of(resolved);
        }

        private static Class<?> resolveDeserializationType(JsonMapper mapper, byte[] bytes, Class<?> declaredType) throws IOException {
            Object value = mapper.readValue(new ByteArrayInputStream(bytes), Argument.of(Object.class));
            if (!(value instanceof Map<?, ?> map)) {
                return declaredType;
            }
            Class<?> currentType = declaredType;
            Set<Class<?>> visited = new HashSet<>();
            while (visited.add(currentType)) {
                JsonbTypeInfo typeInfo = currentType.getAnnotation(JsonbTypeInfo.class);
                if (typeInfo == null) {
                    return currentType;
                }
                Object alias = map.get(typeInfo.key());
                if (!(alias instanceof String aliasValue)) {
                    return currentType;
                }
                Class<?> subtype = subtypeForAlias(typeInfo, aliasValue);
                if (subtype == null) {
                    throw new JsonbException("Unknown JSON-B type alias " + aliasValue + " for type " + currentType.getName());
                }
                currentType = subtype;
            }
            return currentType;
        }

        private static List<TypeInfoProperty> typeInfoProperties(Class<?> type) {
            List<Class<?>> annotatedTypes = annotatedTypeInfoTypes(type);
            ensureSingleTypeInfoChain(annotatedTypes, type);
            List<TypeInfoProperty> properties = new ArrayList<>(annotatedTypes.size());
            for (Class<?> annotatedType : annotatedTypes) {
                JsonbTypeInfo typeInfo = annotatedType.getAnnotation(JsonbTypeInfo.class);
                if (typeInfo == null) {
                    continue;
                }
                String alias = aliasForType(typeInfo, type);
                if (alias != null) {
                    properties.add(new TypeInfoProperty(typeInfo.key(), alias));
                }
            }
            return properties;
        }

        private static List<String> declaredPropertyOrder(Class<?> type) {
            Deque<Class<?>> hierarchy = new ArrayDeque<>();
            Class<?> current = type;
            while (current != null && current != Object.class) {
                hierarchy.addFirst(current);
                current = current.getSuperclass();
            }
            List<String> names = new ArrayList<>();
            for (Class<?> hierarchyType : hierarchy) {
                for (Field field : hierarchyType.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        names.add(field.getName());
                    }
                }
            }
            return names;
        }

        private static @Nullable String aliasForType(JsonbTypeInfo typeInfo, Class<?> type) {
            for (jakarta.json.bind.annotation.JsonbSubtype subtype : typeInfo.value()) {
                if (subtype.type().isAssignableFrom(type)) {
                    return subtype.alias();
                }
            }
            return null;
        }

        private static @Nullable Class<?> subtypeForAlias(JsonbTypeInfo typeInfo, String alias) {
            for (jakarta.json.bind.annotation.JsonbSubtype subtype : typeInfo.value()) {
                if (subtype.alias().equals(alias)) {
                    return subtype.type();
                }
            }
            return null;
        }

        private static void validateSubtypeAliases(Class<?> annotatedType, JsonbTypeInfo typeInfo) {
            for (jakarta.json.bind.annotation.JsonbSubtype subtype : typeInfo.value()) {
                if (!annotatedType.isAssignableFrom(subtype.type())) {
                    throw new JsonbException("JSON-B type alias " + subtype.alias() + " does not point to a subtype of " + annotatedType.getName());
                }
            }
        }

        private static void validatePropertyName(Class<?> annotatedType, String key) {
            if (hasDeclaredProperty(annotatedType, key)) {
                throw new JsonbException("JSON-B type information property collides with property " + key + " on " + annotatedType.getName());
            }
        }

        private static boolean hasDeclaredProperty(Class<?> type, String key) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getName().equals(key)) {
                    return true;
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }
                String methodName = method.getName();
                if ((methodName.startsWith("get") && methodName.length() > 3 && ReflectionFallback.decapitalize(methodName.substring(3)).equals(key))
                    || (methodName.startsWith("is") && methodName.length() > 2 && ReflectionFallback.decapitalize(methodName.substring(2)).equals(key))) {
                    return true;
                }
            }
            return false;
        }

        private static List<Class<?>> annotatedTypeInfoTypes(Class<?> type) {
            Set<Class<?>> types = new LinkedHashSet<>();
            collectHierarchy(type, types);
            List<Class<?>> annotated = new ArrayList<>();
            for (Class<?> candidate : types) {
                if (candidate.isAnnotationPresent(JsonbTypeInfo.class)) {
                    annotated.add(candidate);
                }
            }
            annotated.sort((left, right) -> {
                if (left == right) {
                    return 0;
                }
                if (left.isAssignableFrom(right)) {
                    return -1;
                }
                if (right.isAssignableFrom(left)) {
                    return 1;
                }
                return left.getName().compareTo(right.getName());
            });
            return annotated;
        }

        private static void collectHierarchy(Class<?> type, Set<Class<?>> types) {
            if (type == null || type == Object.class || !types.add(type)) {
                return;
            }
            for (Class<?> anInterface : type.getInterfaces()) {
                collectHierarchy(anInterface, types);
            }
            collectHierarchy(type.getSuperclass(), types);
        }

        private static void ensureSingleTypeInfoChain(List<Class<?>> annotatedTypes, Class<?> type) {
            for (int i = 0; i < annotatedTypes.size(); i++) {
                Class<?> left = annotatedTypes.get(i);
                for (int j = i + 1; j < annotatedTypes.size(); j++) {
                    Class<?> right = annotatedTypes.get(j);
                    if (!left.isAssignableFrom(right) && !right.isAssignableFrom(left)) {
                        throw new JsonbException("JSON-B type information on multiple inheritance paths is not supported for " + type.getName());
                    }
                }
            }
        }

        private record TypeInfoProperty(String key, String alias) {
        }
    }

    private static final class ReflectionFallback {
        private ReflectionFallback() {
        }

        static @Nullable Object toJsonValue(@Nullable Object value,
                                            @Nullable Object namingStrategy,
                                            String propertyOrderStrategy,
                                            @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                            JsonbRuntimeCustomizations customizations,
                                            boolean includeNullValues,
                                            boolean strictIJson) {
            return toJsonValue(value, namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, new IdentityHashMap<>());
        }

        private static @Nullable Object toJsonValue(@Nullable Object value,
                                                    @Nullable Object namingStrategy,
                                                    String propertyOrderStrategy,
                                                    @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                                    JsonbRuntimeCustomizations customizations,
                                                    boolean includeNullValues,
                                                    boolean strictIJson,
                                                    IdentityHashMap<Object, Boolean> seen) {
            if (value instanceof JsonValue jsonValue) {
                return fromJsonpValue(jsonValue);
            }
            if (value == null || isJsonScalar(value.getClass())) {
                if (value instanceof Character character) {
                    return character.toString();
                }
                if (value instanceof Float floatValue) {
                    return new BigDecimal(Float.toString(floatValue));
                }
                if (value instanceof TimeZone timeZone) {
                    return timeZone.getID();
                }
                if (value instanceof URI || value instanceof URL || value instanceof ZoneId) {
                    return value.toString();
                }
                if (value instanceof Date date) {
                    if (strictIJson) {
                        return strictIJsonDateTime(date.toInstant().atZone(ZoneId.of("UTC")));
                    }
                    return DateTimeFormatter.ISO_DATE_TIME.format(date.toInstant().atZone(ZoneId.of("UTC")));
                }
                if (value instanceof Calendar calendar) {
                    if (strictIJson) {
                        return strictIJsonDateTime(calendar);
                    }
                    if (!hasTime(calendar)) {
                        return DateTimeFormatter.ISO_DATE.format(calendar.toInstant().atZone(calendar.getTimeZone().toZoneId()));
                    }
                    return DateTimeFormatter.ISO_DATE_TIME.format(calendar.toInstant().atZone(calendar.getTimeZone().toZoneId()));
                }
                if (value instanceof Instant || value instanceof Duration || value instanceof Period || value instanceof LocalDate || value instanceof LocalTime
                    || value instanceof LocalDateTime || value instanceof ZonedDateTime || value instanceof OffsetDateTime || value instanceof OffsetTime) {
                    return value.toString();
                }
                return value;
            }
            if (seen.containsKey(value)) {
                return null;
            }
            seen.put(value, Boolean.TRUE);
            Object customized = customizations.serialize(value, namingStrategy, propertyOrderStrategy, visibilityStrategy, includeNullValues, strictIJson, seen);
            if (customized != null) {
                return customized;
            }
            switch (value) {
                case Optional<?> optional -> {
                    return optional.map(v -> toJsonValue(v, namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen)).orElse(null);
                }
                case OptionalInt optional -> {
                    return optional.isPresent() ? optional.getAsInt() : null;
                }
                case OptionalLong optional -> {
                    return optional.isPresent() ? optional.getAsLong() : null;
                }
                case OptionalDouble optional -> {
                    return optional.isPresent() ? optional.getAsDouble() : null;
                }
                case Map<?, ?> map -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    map.forEach((key, item) -> {
                        Object jsonValue = toJsonValue(item, namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
                        values.put(String.valueOf(key), jsonValue);
                    });
                    return values;
                }
                case Collection<?> collection -> {
                    List<Object> values = new ArrayList<>(collection.size());
                    collection.forEach(item -> {
                        Object jsonValue = toJsonValue(item, namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
                        values.add(jsonValue);
                    });
                    return values;
                }
                default -> {
                }
            }
            if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                List<Object> values = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    Object jsonValue = toJsonValue(Array.get(value, i), namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
                    values.add(jsonValue);
                }
                return values;
            }
            Map<String, Object> object = new LinkedHashMap<>();
            Set<String> accessorProperties = new HashSet<>();
            Set<String> transientProperties = transientProperties(value.getClass());
            List<Method> getters = new ArrayList<>();
            for (Method method : value.getClass().getMethods()) {
                if (isGetter(method) && isVisible(method, visibilityStrategy)) {
                    getters.add(method);
                }
            }
            getters.sort(propertyComparator(value.getClass(), propertyOrderStrategy, namingStrategy));
            for (Method method : getters) {
                String implicitName = implicitPropertyName(method);
                if (transientProperties.contains(implicitName) || isStaticBackedAccessor(value.getClass(), implicitName)) {
                    continue;
                }
                accessorProperties.add(implicitName);
                try {
                    method.setAccessible(true);
                    Field field = field(value.getClass(), implicitName);
                    Object jsonValue = toJsonPropertyValue(method.invoke(value), method, field, value.getClass(), namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
                    if (jsonValue != null || includeNullValues) {
                        object.put(propertyName(method, namingStrategy), jsonValue);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new JsonbException("Cannot access JSON-B property " + method.getName(), e);
                }
            }
            for (Field field : fields(value.getClass())) {
                String implicitName = field.getName();
                if (!accessorProperties.contains(implicitName) && !transientProperties.contains(implicitName) && isFieldProperty(field) && isVisible(field, visibilityStrategy)) {
                    try {
                        field.setAccessible(true);
                        Object jsonValue = toJsonPropertyValue(field.get(value), null, field, value.getClass(), namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
                        if (jsonValue != null || includeNullValues) {
                            object.put(propertyName(field, namingStrategy), jsonValue);
                        }
                    } catch (IllegalAccessException e) {
                        throw new JsonbException("Cannot access JSON-B field " + field.getName(), e);
                    }
                }
            }
            if (value.getClass().isAnonymousClass() || value.getClass().isLocalClass()) {
                return object;
            }
            return orderProperties(object, propertyOrderStrategy, value.getClass());
        }

        private static @Nullable Object toJsonPropertyValue(@Nullable Object value,
                                                            @Nullable Method accessor,
                                                            @Nullable Field field,
                                                            Class<?> beanType,
                                                            @Nullable Object namingStrategy,
                                                            String propertyOrderStrategy,
                                                            @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                                            JsonbRuntimeCustomizations customizations,
                                                            boolean includeNullValues,
                                                            boolean strictIJson,
                                                            IdentityHashMap<Object, Boolean> seen) {
            if (value == null) {
                return null;
            }
            JsonbTypeAdapter adapterAnnotation = typeAdapter(accessor, field, beanType);
            if (adapterAnnotation != null) {
                try {
                    @SuppressWarnings("unchecked")
                    JsonbAdapter<Object, Object> adapter = (JsonbAdapter<Object, Object>) jsonbComponent(adapterAnnotation.value());
                    return toJsonValue(adapter.adaptToJson(value), namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B property for serialization", e);
                }
            }
            JsonbTypeSerializer serializerAnnotation = typeSerializer(accessor, field, beanType);
            if (serializerAnnotation != null) {
                @SuppressWarnings("unchecked")
                JsonbSerializer<Object> serializer = (JsonbSerializer<Object>) jsonbComponent(serializerAnnotation.value());
                return customizations.serializeWith(serializer, value);
            }
            if (value instanceof Number number) {
                JsonbNumberFormat format = numberFormat(accessor, field, beanType);
                if (format != null) {
                    return formatNumber(number, format);
                }
            }
            if (value instanceof Date date) {
                JsonbDateFormat format = dateFormat(accessor, field, beanType);
                if (format != null) {
                    return dateFormat(format).format(date);
                }
            }
            return toJsonValue(value, namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations, includeNullValues, strictIJson, seen);
        }

        private static @Nullable Object fromJsonValue(@Nullable Object value,
                                                      Type targetType,
                                                      @Nullable Object namingStrategy,
                                                      @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                                      JsonbRuntimeCustomizations customizations) {
            Object customized = customizations.deserialize(value, targetType, namingStrategy, visibilityStrategy);
            if (customized != null) {
                return customized;
            }
            if (targetType instanceof Class<?> type) {
                return fromJsonValue(value, type, targetType, namingStrategy, visibilityStrategy, customizations);
            }
            if (targetType instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClass) {
                    return fromJsonValue(value, rawClass, parameterizedType, namingStrategy, visibilityStrategy, customizations);
                }
            }
            if (targetType instanceof GenericArrayType genericArrayType && (value instanceof Collection<?> || (value != null && value.getClass().isArray()))) {
                Type componentType = genericArrayType.getGenericComponentType();
                List<?> source = value instanceof Collection<?> collection ? new ArrayList<>(collection) : arrayValues(value);
                Class<?> componentClass = erasedType(componentType);
                Object array = java.lang.reflect.Array.newInstance(componentClass, source.size());
                int index = 0;
                for (Object item : source) {
                    java.lang.reflect.Array.set(array, index++, fromJsonValue(item, componentType, namingStrategy, visibilityStrategy, customizations));
                }
                return array;
            }
            return fromJsonValue(value, Object.class, targetType, namingStrategy, visibilityStrategy, customizations);
        }

        private static @Nullable Object fromJsonPropertyValue(@Nullable Object value,
                                                              Type targetType,
                                                              @Nullable Method accessor,
                                                              @Nullable Field field,
                                                              Class<?> beanType,
                                                              @Nullable Object namingStrategy,
                                                              @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                                              JsonbRuntimeCustomizations customizations) {
            JsonbTypeAdapter adapterAnnotation = typeAdapter(accessor, field, beanType);
            if (adapterAnnotation != null) {
                try {
                    Class<? extends JsonbAdapter> adapterClass = adapterAnnotation.value();
                    Object adapted = fromJsonValue(value, JsonbBridgeSupport.adaptedType(adapterClass), namingStrategy, visibilityStrategy, customizations);
                    @SuppressWarnings("unchecked")
                    JsonbAdapter<Object, Object> adapter = (JsonbAdapter<Object, Object>) jsonbComponent(adapterClass);
                    return adapter.adaptFromJson(adapted);
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B property for deserialization", e);
                }
            }
            JsonbTypeDeserializer deserializerAnnotation = typeDeserializer(accessor, field, beanType);
            if (deserializerAnnotation != null) {
                @SuppressWarnings("unchecked")
                JsonbDeserializer<Object> deserializer = (JsonbDeserializer<Object>) jsonbComponent(deserializerAnnotation.value());
                return customizations.deserializeWith(deserializer, value, targetType);
            }
            Class<?> type = erasedType(targetType);
            if (value != null && Number.class.isAssignableFrom(type)) {
                JsonbNumberFormat format = numberFormat(accessor, field, beanType);
                if (format != null) {
                    return fromJsonValue(parseNumber(String.valueOf(value), format), targetType, namingStrategy, visibilityStrategy, customizations);
                }
            }
            if (value != null && Date.class.isAssignableFrom(type)) {
                JsonbDateFormat format = dateFormat(accessor, field, beanType);
                if (format != null) {
                    try {
                        return dateFormat(format).parse(String.valueOf(value));
                    } catch (ParseException e) {
                        throw new JsonbException("Cannot parse JSON-B date value", e);
                    }
                }
            }
            return fromJsonValue(value, targetType, namingStrategy, visibilityStrategy, customizations);
        }

        private static @Nullable Object fromJsonValue(@Nullable Object value,
                                                      Class<?> type,
                                                      Type targetType,
                                                      @Nullable Object namingStrategy,
                                                      @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                                      JsonbRuntimeCustomizations customizations) {
            if (value == null && JsonValue.class.isAssignableFrom(type)) {
                return JsonValue.NULL;
            }
            if (value == null) {
                if (type == Optional.class) {
                    return Optional.empty();
                }
                if (type == OptionalInt.class) {
                    return OptionalInt.empty();
                }
                if (type == OptionalLong.class) {
                    return OptionalLong.empty();
                }
                if (type == OptionalDouble.class) {
                    return OptionalDouble.empty();
                }
                if (type == boolean.class) {
                    return false;
                }
                if (type == char.class) {
                    return '\0';
                }
                if (type.isPrimitive()) {
                    return 0;
                }
                return null;
            }
            if (type == Object.class) {
                return normalizeUntypedValue(value);
            }
            if (JsonValue.class.isAssignableFrom(type)) {
                return toJsonpValue(value, type);
            }
            if (type == String.class) {
                return String.valueOf(value);
            }
            if (type == URI.class) {
                return URI.create(String.valueOf(value));
            }
            if (type == URL.class) {
                try {
                    return URI.create(String.valueOf(value)).toURL();
                } catch (MalformedURLException e) {
                    throw new JsonbException("Cannot convert JSON-B value to URL", e);
                }
            }
            if (type == Date.class) {
                return Date.from(instant(String.valueOf(value)));
            }
            if (Calendar.class.isAssignableFrom(type)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(Date.from(instant(String.valueOf(value))));
                return calendar;
            }
            if (TimeZone.class.isAssignableFrom(type)) {
                return timeZone(String.valueOf(value), type);
            }
            if (type == Instant.class) {
                return Instant.parse(String.valueOf(value));
            }
            if (type == Duration.class) {
                return Duration.parse(String.valueOf(value));
            }
            if (type == Period.class) {
                return Period.parse(String.valueOf(value));
            }
            if (type == LocalDate.class) {
                return LocalDate.parse(String.valueOf(value));
            }
            if (type == LocalTime.class) {
                return LocalTime.parse(String.valueOf(value));
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(String.valueOf(value));
            }
            if (type == ZonedDateTime.class) {
                return ZonedDateTime.parse(String.valueOf(value));
            }
            if (type == OffsetDateTime.class) {
                return OffsetDateTime.parse(String.valueOf(value));
            }
            if (type == OffsetTime.class) {
                return OffsetTime.parse(String.valueOf(value));
            }
            if (type == ZoneId.class) {
                return ZoneId.of(String.valueOf(value));
            }
            if (type == ZoneOffset.class) {
                return ZoneOffset.of(String.valueOf(value));
            }
            if (type == int.class || type == Integer.class) {
                return ((Number) value).intValue();
            }
            if (type == long.class || type == Long.class) {
                return ((Number) value).longValue();
            }
            if (type == double.class || type == Double.class) {
                return ((Number) value).doubleValue();
            }
            if (type == float.class || type == Float.class) {
                return ((Number) value).floatValue();
            }
            if (type == short.class || type == Short.class) {
                return ((Number) value).shortValue();
            }
            if (type == byte.class || type == Byte.class) {
                return ((Number) value).byteValue();
            }
            if (type == boolean.class || type == Boolean.class) {
                return value;
            }
            if (type == char.class || type == Character.class) {
                String string = String.valueOf(value);
                return string.isEmpty() ? '\0' : string.charAt(0);
            }
            if (type == BigInteger.class) {
                return value instanceof BigDecimal decimal ? decimal.toBigInteger() : BigInteger.valueOf(((Number) value).longValue());
            }
            if (type == BigDecimal.class) {
                return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
            }
            if (type == Number.class) {
                return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
            }
            if (type.isEnum()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object enumValue = Enum.valueOf((Class) type, String.valueOf(value));
                return enumValue;
            }
            if (type == Optional.class) {
                Type elementType = optionalElementType(targetType);
                return Optional.ofNullable(fromJsonValue(value, elementType, namingStrategy, visibilityStrategy, customizations));
            }
            if (type == OptionalInt.class) {
                return OptionalInt.of(((Number) value).intValue());
            }
            if (type == OptionalLong.class) {
                return OptionalLong.of(((Number) value).longValue());
            }
            if (type == OptionalDouble.class) {
                return OptionalDouble.of(((Number) value).doubleValue());
            }
            if (type.isArray() && (value instanceof Collection<?> || value.getClass().isArray())) {
                Class<?> componentType = type.getComponentType();
                Type genericComponentType = targetType instanceof GenericArrayType genericArrayType ? genericArrayType.getGenericComponentType() : componentType;
                List<?> source = value instanceof Collection<?> collection ? new ArrayList<>(collection) : arrayValues(value);
                Object array = java.lang.reflect.Array.newInstance(componentType, source.size());
                int index = 0;
                for (Object item : source) {
                    java.lang.reflect.Array.set(array, index++, fromJsonValue(item, genericComponentType, namingStrategy, visibilityStrategy, customizations));
                }
                return array;
            }
            if (Collection.class.isAssignableFrom(type) && (value instanceof Collection<?> || value.getClass().isArray())) {
                Collection<Object> converted = collection(type);
                Type elementType = collectionElementType(targetType);
                for (Object item : value instanceof Collection<?> collection ? collection : arrayValues(value)) {
                    Object jsonValue = fromJsonValue(item, elementType, namingStrategy, visibilityStrategy, customizations);
                    converted.add(jsonValue);
                }
                return converted;
            }
            if (Map.class.isAssignableFrom(type) && value instanceof Map<?, ?> map) {
                Map<Object, Object> converted = map(type);
                Type keyType = mapKeyType(targetType);
                Type valueType = mapValueType(targetType);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = fromJsonValue(entry.getKey(), keyType, namingStrategy, visibilityStrategy, customizations);
                    Object v = fromJsonValue(entry.getValue(), valueType, namingStrategy, visibilityStrategy, customizations);
                    converted.put(
                        Objects.requireNonNull(key),
                        Objects.requireNonNull(v)
                    );
                }
                return converted;
            }
            if (type.isInstance(value)) {
                return value;
            }
            if (value instanceof Map<?, ?> map) {
                return toBean(map, type, targetType, namingStrategy, visibilityStrategy, customizations);
            }
            throw new JsonbException("Cannot convert JSON-B value to " + type.getName());
        }

        private static @Nullable Object normalizeUntypedValue(@Nullable Object value) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), normalizeUntypedValue(entry.getValue()));
                }
                return converted;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> converted = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    converted.add(normalizeUntypedValue(item));
                }
                return converted;
            }
            if (value instanceof Number number && !(value instanceof BigDecimal)) {
                return new BigDecimal(String.valueOf(number));
            }
            return value;
        }

        private static @Nullable Object fromJsonpValue(JsonValue value) {
            return switch (value.getValueType()) {
                case ARRAY -> {
                    JsonArray array = value.asJsonArray();
                    List<Object> values = new ArrayList<>(array.size());
                    for (JsonValue item : array) {
                        values.add(fromJsonpValue(item));
                    }
                    yield values;
                }
                case OBJECT -> {
                    JsonObject object = value.asJsonObject();
                    Map<String, Object> values = new LinkedHashMap<>();
                    object.forEach((key, item) -> values.put(key, fromJsonpValue(item)));
                    yield values;
                }
                case STRING -> ((JsonString) value).getString();
                case NUMBER -> ((JsonNumber) value).bigDecimalValue();
                case TRUE -> true;
                case FALSE -> false;
                case NULL -> null;
            };
        }

        private static Map<String, Object> orderProperties(Map<String, Object> properties, String propertyOrderStrategy, Class<?> type) {
            if (properties.size() < 2 || PropertyOrderStrategy.ANY.equals(propertyOrderStrategy)) {
                return properties;
            }
            List<String> names = new ArrayList<>(properties.keySet());
            JsonbPropertyOrder propertyOrder = propertyOrder(type);
            if (propertyOrder != null && propertyOrder.value().length > 0) {
                List<String> order = List.of(propertyOrder.value());
                names.sort(Comparator.comparingInt(name -> {
                    int index = order.indexOf(name);
                    return index < 0 ? Integer.MAX_VALUE : index;
                }));
            } else {
                names.sort(PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy) ? Collections.reverseOrder() : String::compareTo);
            }
            Map<String, Object> ordered = new LinkedHashMap<>(properties.size());
            for (String name : names) {
                ordered.put(name, properties.get(name));
            }
            return ordered;
        }

        private static Comparator<Method> propertyComparator(Class<?> type, String propertyOrderStrategy, @Nullable Object namingStrategy) {
            JsonbPropertyOrder propertyOrder = propertyOrder(type);
            if (propertyOrder != null && propertyOrder.value().length > 0) {
                List<String> order = List.of(propertyOrder.value());
                return Comparator.comparingInt(method -> {
                    int index = order.indexOf(propertyName(method, namingStrategy));
                    return index < 0 ? Integer.MAX_VALUE : index;
                });
            }
            if (PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy)) {
                return Comparator.comparing((Method method) -> propertyName(method, namingStrategy)).reversed();
            }
            if (PropertyOrderStrategy.ANY.equals(propertyOrderStrategy)) {
                return Comparator.comparingInt(Method::getParameterCount);
            }
            return Comparator
                .comparingInt((Method method) -> hierarchyDistance(type, method.getDeclaringClass()))
                .thenComparing(method -> propertyName(method, namingStrategy));
        }

        private static int hierarchyDistance(Class<?> type, Class<?> declaringType) {
            int distance = 0;
            Class<?> current = type;
            while (current != null && current != declaringType) {
                distance++;
                current = current.getSuperclass();
            }
            return current == null ? Integer.MAX_VALUE : -distance;
        }

        private static @Nullable JsonbPropertyOrder propertyOrder(Class<?> type) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                JsonbPropertyOrder propertyOrder = current.getAnnotation(JsonbPropertyOrder.class);
                if (propertyOrder != null) {
                    return propertyOrder;
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private static JsonValue toJsonpValue(@Nullable Object value, Class<?> type) {
            if (value == null) {
                return JsonValue.NULL;
            }
            JsonProvider provider = JsonProvider.provider();
            if (type == JsonString.class) {
                return provider.createValue(String.valueOf(value));
            }
            if (type == JsonNumber.class) {
                return jsonNumber(provider, value);
            }
            if (type == JsonArray.class) {
                if (value instanceof Collection<?> collection) {
                    return jsonArray(provider, collection);
                }
                if (value.getClass().isArray()) {
                    return jsonArray(provider, arrayValues(value));
                }
                throw new JsonbException("Cannot convert JSON-B value to JsonArray");
            }
            if (type == JsonObject.class) {
                if (value instanceof Map<?, ?> map) {
                    return jsonObject(provider, map);
                }
                throw new JsonbException("Cannot convert JSON-B value to JsonObject");
            }
            if (type == JsonStructure.class) {
                if (value instanceof Map<?, ?> map) {
                    return jsonObject(provider, map);
                }
                if (value instanceof Collection<?> collection) {
                    return jsonArray(provider, collection);
                }
                if (value.getClass().isArray()) {
                    return jsonArray(provider, arrayValues(value));
                }
                throw new JsonbException("Cannot convert JSON-B value to JsonStructure");
            }
            if (value instanceof Map<?, ?> map) {
                return jsonObject(provider, map);
            }
            if (value instanceof Collection<?> collection) {
                return jsonArray(provider, collection);
            }
            if (value.getClass().isArray()) {
                return jsonArray(provider, arrayValues(value));
            }
            return switch (value) {
                case String string -> provider.createValue(string);
                case Number number -> jsonNumber(provider, number);
                case Boolean bool -> bool ? JsonValue.TRUE : JsonValue.FALSE;
                default -> provider.createValue(String.valueOf(value));
            };
        }

        private static JsonNumber jsonNumber(JsonProvider provider, Object value) {
            if (value instanceof BigDecimal decimal) {
                return provider.createValue(decimal);
            }
            if (value instanceof BigInteger integer) {
                return provider.createValue(integer);
            }
            if (value instanceof Float || value instanceof Double) {
                return provider.createValue(((Number) value).doubleValue());
            }
            return provider.createValue(((Number) value).longValue());
        }

        private static JsonObject jsonObject(JsonProvider provider, Map<?, ?> map) {
            JsonObjectBuilder builder = provider.createObjectBuilder();
            map.forEach((key, item) -> builder.add(String.valueOf(key), toJsonpValue(item, JsonValue.class)));
            return builder.build();
        }

        private static JsonArray jsonArray(JsonProvider provider, Collection<?> collection) {
            JsonArrayBuilder builder = provider.createArrayBuilder();
            for (Object item : collection) {
                builder.add(toJsonpValue(item, JsonValue.class));
            }
            return builder.build();
        }

        private static Object toBean(Map<?, ?> map,
                                     Class<?> type,
                                     Type targetType,
                                     @Nullable Object namingStrategy,
                                     @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                     JsonbRuntimeCustomizations customizations) {
            validateObjectModel(type, namingStrategy);
            Object bean = instantiate(type);
            Set<String> transientProperties = transientProperties(type);
            Set<String> mutatorProperties = new HashSet<>();
            for (Method method : type.getMethods()) {
                if (isSetter(method) && isVisibleSetter(method, visibilityStrategy)) {
                    String implicitName = implicitPropertyName(method);
                    if (transientProperties.contains(implicitName) || isStaticBackedAccessor(type, implicitName)) {
                        continue;
                    }
                    mutatorProperties.add(implicitName);
                    String name = propertyName(method, type, namingStrategy);
                    if (map.containsKey(name)) {
                        try {
                            Field field = field(type, implicitName);
                            Type propertyType = resolveType(method.getGenericParameterTypes()[0], targetType, type);
                            method.invoke(bean, fromJsonPropertyValue(map.get(name), propertyType, method, field, type, namingStrategy, visibilityStrategy, customizations));
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new JsonbException("Cannot set JSON-B property " + name, e);
                        }
                    }
                }
            }
            for (Field field : fields(type)) {
                String name = propertyName(field, namingStrategy);
                if (!mutatorProperties.contains(field.getName()) && isFieldProperty(field) && isVisible(field, visibilityStrategy) && !transientProperties.contains(field.getName()) && map.containsKey(name)) {
                    try {
                        field.setAccessible(true);
                        Type propertyType = resolveType(field.getGenericType(), targetType, type);
                        field.set(bean, fromJsonPropertyValue(map.get(name), propertyType, null, field, type, namingStrategy, visibilityStrategy, customizations));
                    } catch (IllegalAccessException e) {
                        throw new JsonbException("Cannot set JSON-B field " + name, e);
                    }
                }
            }
            return bean;
        }

        private static Type resolveType(Type type, Type contextType, Class<?> beanType) {
            if (type instanceof TypeVariable<?> typeVariable && contextType instanceof ParameterizedType parameterizedType) {
                TypeVariable<?>[] typeParameters = beanType.getTypeParameters();
                Type[] actualTypes = parameterizedType.getActualTypeArguments();
                for (int i = 0; i < typeParameters.length && i < actualTypes.length; i++) {
                    if (typeParameters[i].getName().equals(typeVariable.getName())) {
                        return actualTypes[i];
                    }
                }
            }
            return type;
        }

        private static <T> T instantiate(Class<T> type) {
            try {
                Constructor<T> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new JsonbException("No default constructor available for JSON-B fallback type " + type.getName(), e);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new JsonbException("Cannot instantiate JSON-B fallback type " + type.getName(), e);
            }
        }

        private static Collection<Object> collection(Class<?> type) {
            if (type.isInterface()) {
                if (SortedSet.class.isAssignableFrom(type)) {
                    return new TreeSet<>();
                }
                if (Set.class.isAssignableFrom(type)) {
                    return new LinkedHashSet<>();
                }
                if (Queue.class.isAssignableFrom(type) || Deque.class.isAssignableFrom(type)) {
                    return new ArrayDeque<>();
                }
                return new ArrayList<>();
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) constructor.newInstance();
                return collection;
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                if (SortedSet.class.isAssignableFrom(type)) {
                    return new TreeSet<>();
                }
                if (Set.class.isAssignableFrom(type)) {
                    return new LinkedHashSet<>();
                }
                if (Queue.class.isAssignableFrom(type) || Deque.class.isAssignableFrom(type)) {
                    return new ArrayDeque<>();
                }
                return new ArrayList<>();
            }
        }

        private static Map<Object, Object> map(Class<?> type) {
            if (type.isInterface()) {
                return SortedMap.class.isAssignableFrom(type) ? new TreeMap<>() : new LinkedHashMap<>();
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) constructor.newInstance();
                return map;
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                return SortedMap.class.isAssignableFrom(type) ? new TreeMap<>() : new LinkedHashMap<>();
            }
        }

        private static List<Object> arrayValues(Object array) {
            int length = java.lang.reflect.Array.getLength(array);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(java.lang.reflect.Array.get(array, i));
            }
            return values;
        }

        private static Instant instant(String value) {
            if (value.indexOf('T') < 0) {
                return LocalDate.parse(value).atStartOfDay(ZoneId.of("UTC")).toInstant();
            }
            if (value.endsWith("]")) {
                return ZonedDateTime.parse(value).toInstant();
            }
            try {
                return Instant.parse(value);
            } catch (RuntimeException ignored) {
                return LocalDateTime.parse(value).atZone(ZoneId.of("UTC")).toInstant();
            }
        }

        private static TimeZone timeZone(String value, Class<?> type) {
            if (isDeprecatedThreeLetterTimeZone(value)) {
                throw new JsonbException("Deprecated three-letter time zone IDs are not supported by JSON-B: " + value);
            }
            TimeZone zone = TimeZone.getTimeZone(value);
            if (SimpleTimeZone.class.isAssignableFrom(type)) {
                return new SimpleTimeZone(zone.getRawOffset(), zone.getID());
            }
            return zone;
        }

        private static boolean isDeprecatedThreeLetterTimeZone(String value) {
            return value.length() == 3 && !"UTC".equals(value) && !"GMT".equals(value);
        }

        private static Type optionalElementType(Type targetType) {
            if (targetType instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 1) {
                return parameterizedType.getActualTypeArguments()[0];
            }
            return Object.class;
        }

        private static Type collectionElementType(Type targetType) {
            if (targetType instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 1) {
                return parameterizedType.getActualTypeArguments()[0];
            }
            return Object.class;
        }

        private static Type mapKeyType(Type targetType) {
            if (targetType instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 2) {
                return parameterizedType.getActualTypeArguments()[0];
            }
            return Object.class;
        }

        private static Type mapValueType(Type targetType) {
            if (targetType instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 2) {
                return parameterizedType.getActualTypeArguments()[1];
            }
            return Object.class;
        }

        private static Class<?> erasedType(Type type) {
            return switch (type) {
                case Class<?> clazz -> clazz;
                case
                    ParameterizedType parameterizedType when parameterizedType.getRawType() instanceof Class<?> clazz ->
                    clazz;
                case GenericArrayType genericArrayType ->
                    Array.newInstance(erasedType(genericArrayType.getGenericComponentType()), 0).getClass();
                default -> Object.class;
            };
        }

        private static boolean hasTime(Calendar calendar) {
            return calendar.isSet(Calendar.HOUR)
                || calendar.isSet(Calendar.HOUR_OF_DAY)
                || calendar.isSet(Calendar.MINUTE)
                || calendar.isSet(Calendar.SECOND)
                || calendar.isSet(Calendar.MILLISECOND);
        }

        private static String strictIJsonDateTime(Calendar calendar) {
            ZoneId zoneId = calendar.getTimeZone().toZoneId();
            ZonedDateTime zonedDateTime = ZonedDateTime.of(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
                0,
                zoneId
            );
            return strictIJsonDateTime(zonedDateTime);
        }

        private static String strictIJsonDateTime(ZonedDateTime value) {
            return String.format(
                java.util.Locale.ROOT,
                "%04d-%02d-%02dT%02d:%02d:%02dZ%s",
                value.getYear(),
                value.getMonthValue(),
                value.getDayOfMonth(),
                value.getHour(),
                value.getMinute(),
                value.getSecond(),
                offset(value)
            );
        }

        private static String offset(ZonedDateTime value) {
            String offset = value.getOffset().toString();
            return "Z".equals(offset) ? "+00:00" : offset;
        }

        private static List<Field> fields(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            Class<?> current = type;
            while (current != Object.class && current != null) {
                Collections.addAll(fields, current.getDeclaredFields());
                current = current.getSuperclass();
            }
            return fields;
        }

        private static boolean isJsonScalar(Class<?> type) {
            return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Enum.class.isAssignableFrom(type)
                || URI.class.isAssignableFrom(type)
                || URL.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || Calendar.class.isAssignableFrom(type)
                || TimeZone.class.isAssignableFrom(type)
                || Instant.class.isAssignableFrom(type)
                || Duration.class.isAssignableFrom(type)
                || Period.class.isAssignableFrom(type)
                || LocalDate.class.isAssignableFrom(type)
                || LocalTime.class.isAssignableFrom(type)
                || LocalDateTime.class.isAssignableFrom(type)
                || ZonedDateTime.class.isAssignableFrom(type)
                || OffsetDateTime.class.isAssignableFrom(type)
                || OffsetTime.class.isAssignableFrom(type)
                || ZoneId.class.isAssignableFrom(type);
        }

        private static boolean isGetter(Method method) {
            return method.getParameterCount() == 0
                && !method.getReturnType().equals(Void.TYPE)
                && !method.isSynthetic()
                && !method.isBridge()
                && !method.isAnnotationPresent(JsonbTransient.class)
                && method.getDeclaringClass() != Object.class
                && ((method.getName().startsWith("get") && method.getName().length() > 3)
                || (method.getName().startsWith("is") && method.getName().length() > 2 && method.getReturnType() == boolean.class));
        }

        private static boolean isSetter(Method method) {
            return method.getParameterCount() == 1
                && method.getName().startsWith("set")
                && method.getName().length() > 3
                && !method.isSynthetic()
                && !method.isBridge()
                && !method.isAnnotationPresent(JsonbTransient.class);
        }

        private static boolean isFieldProperty(Field field) {
            int modifiers = field.getModifiers();
            return !Modifier.isStatic(modifiers)
                && !Modifier.isTransient(modifiers)
                && !field.isSynthetic()
                && !field.isAnnotationPresent(JsonbTransient.class);
        }

        private static boolean isVisible(Method method, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            return visibilityStrategy == null || visibilityStrategy.isVisible(method);
        }

        private static boolean isVisibleSetter(Method method, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            return visibilityStrategy == null || visibilityStrategy.isVisible(method);
        }

        private static boolean isVisible(Field field, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
            return visibilityStrategy == null || visibilityStrategy.isVisible(field);
        }

        private static String propertyName(Field field, @Nullable Object namingStrategy) {
            JsonbProperty property = field.getAnnotation(JsonbProperty.class);
            if (property != null && !property.value().isEmpty()) {
                return property.value();
            }
            return translateName(field.getName(), namingStrategy);
        }

        private static String propertyName(Method method, @Nullable Object namingStrategy) {
            return propertyName(method, method.getDeclaringClass(), namingStrategy);
        }

        private static String propertyName(Method method, Class<?> beanType, @Nullable Object namingStrategy) {
            JsonbProperty property = method.getAnnotation(JsonbProperty.class);
            if (property != null && !property.value().isEmpty()) {
                return property.value();
            }
            Field field = field(beanType, implicitPropertyName(method));
            if (field != null) {
                property = field.getAnnotation(JsonbProperty.class);
                if (property != null && !property.value().isEmpty()) {
                    return property.value();
                }
            }
            return translateName(implicitPropertyName(method), namingStrategy);
        }

        private static Set<String> transientProperties(Class<?> type) {
            Set<String> properties = new HashSet<>();
            for (Field field : fields(type)) {
                if (Modifier.isTransient(field.getModifiers()) || field.isAnnotationPresent(JsonbTransient.class)) {
                    properties.add(field.getName());
                }
            }
            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(JsonbTransient.class) && (isGetterName(method) || isSetter(method))) {
                    properties.add(implicitPropertyName(method));
                }
            }
            return properties;
        }

        private static boolean hasTransientProperty(Class<?> type) {
            return !transientProperties(type).isEmpty();
        }

        private static boolean hasPropertyCustomization(Class<?> type) {
            for (Field field : fields(type)) {
                if (field.isAnnotationPresent(JsonbTypeAdapter.class)
                    || field.isAnnotationPresent(JsonbTypeSerializer.class)
                    || field.isAnnotationPresent(JsonbTypeDeserializer.class)) {
                    return true;
                }
            }
            for (Method method : type.getMethods()) {
                if ((isGetter(method) || isSetter(method))
                    && (method.isAnnotationPresent(JsonbTypeAdapter.class)
                    || method.isAnnotationPresent(JsonbTypeSerializer.class)
                    || method.isAnnotationPresent(JsonbTypeDeserializer.class))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasAsymmetricAccessorNames(Class<?> type) {
            Map<String, String> getterNames = new LinkedHashMap<>();
            for (Method method : type.getMethods()) {
                if (isGetter(method)) {
                    JsonbProperty property = method.getAnnotation(JsonbProperty.class);
                    if (property != null && !property.value().isEmpty()) {
                        getterNames.put(implicitPropertyName(method), property.value());
                    }
                }
            }
            for (Method method : type.getMethods()) {
                if (isSetter(method)) {
                    JsonbProperty property = method.getAnnotation(JsonbProperty.class);
                    if (property != null && !property.value().isEmpty()) {
                        String getterName = getterNames.get(implicitPropertyName(method));
                        if (getterName != null && !getterName.equals(property.value())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean hasAsymmetricAccessorFormats(Class<?> type) {
            Map<String, String> getterFormats = new LinkedHashMap<>();
            for (Method method : type.getMethods()) {
                if (isGetter(method)) {
                    String format = formatSignature(method);
                    if (format != null) {
                        getterFormats.put(implicitPropertyName(method), format);
                    }
                }
            }
            for (Method method : type.getMethods()) {
                if (isSetter(method)) {
                    String format = formatSignature(method);
                    if (format != null) {
                        String getterFormat = getterFormats.get(implicitPropertyName(method));
                        if (getterFormat != null && !getterFormat.equals(format)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static @Nullable String formatSignature(Method method) {
            JsonbNumberFormat numberFormat = method.getAnnotation(JsonbNumberFormat.class);
            if (numberFormat != null) {
                return "number:" + numberFormat.value() + ':' + numberFormat.locale();
            }
            JsonbDateFormat dateFormat = method.getAnnotation(JsonbDateFormat.class);
            if (dateFormat != null) {
                return "date:" + dateFormat.value() + ':' + dateFormat.locale();
            }
            return null;
        }

        private static void validateObjectModel(Class<?> type, @Nullable Object namingStrategy) {
            if (isJsonScalar(type)) {
                return;
            }
            Map<String, PropertyModel> models = propertyModels(type);
            for (PropertyModel model : models.values()) {
                if (model.hasJsonbTransient() && model.hasJsonbCustomization()) {
                    throw new JsonbException("JsonbTransient cannot be combined with other JSON-B customization annotations on property " + model.implicitName);
                }
            }
            validateNoDuplicateNames(models, namingStrategy, true);
            validateNoDuplicateNames(models, namingStrategy, false);
        }

        private static void validateDefaultConstructorAccess(Class<?> type) {
            if (isJsonScalar(type) || type.isEnum() || type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                return;
            }
            if (hasCreator(type)) {
                return;
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                int modifiers = constructor.getModifiers();
                if (Modifier.isPrivate(modifiers) || (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers))) {
                    throw new JsonbException("JSON-B requires a public or protected default constructor for " + type.getName());
                }
            } catch (NoSuchMethodException ignored) {
                // Creator constructors/factories and generated deserializers handle non-default construction.
            }
        }

        private static void validateCreatorModel(Class<?> type) {
            if (isJsonScalar(type) || type.isEnum() || type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                return;
            }
            int creators = creatorCount(type);
            if (creators > 1) {
                throw new JsonbException("JSON-B supports only one JsonbCreator for " + type.getName());
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(JsonbCreator.class) && !type.isAssignableFrom(method.getReturnType())) {
                    throw new JsonbException("JsonbCreator factory method must return " + type.getName());
                }
            }
        }

        private static boolean hasCreator(Class<?> type) {
            return creatorCount(type) > 0;
        }

        private static int creatorCount(Class<?> type) {
            int count = 0;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (constructor.isAnnotationPresent(JsonbCreator.class)) {
                    count++;
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(JsonbCreator.class)) {
                    count++;
                }
            }
            return count;
        }

        private static boolean hasStaticBackedAccessor(Class<?> type) {
            for (Method method : type.getMethods()) {
                if ((isGetter(method) || isSetter(method)) && isStaticBackedAccessor(type, implicitPropertyName(method))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isStaticBackedAccessor(Class<?> type, String implicitName) {
            Field field = field(type, implicitName);
            return field != null && Modifier.isStatic(field.getModifiers());
        }

        private static void validateNoDuplicateNames(Map<String, PropertyModel> models, @Nullable Object namingStrategy, boolean serialization) {
            Map<String, String> names = new LinkedHashMap<>();
            for (PropertyModel model : models.values()) {
                if (model.isTransient()) {
                    continue;
                }
                String name = serialization ? model.serializationName(namingStrategy) : model.deserializationName(namingStrategy);
                if (name == null) {
                    continue;
                }
                String previous = names.putIfAbsent(name, model.implicitName);
                if (previous != null && !previous.equals(model.implicitName)) {
                    throw new JsonbException("Duplicate JSON-B property name: " + name);
                }
            }
        }

        private static Map<String, PropertyModel> propertyModels(Class<?> type) {
            Map<String, PropertyModel> models = new LinkedHashMap<>();
            for (Field field : fields(type)) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                models.computeIfAbsent(field.getName(), PropertyModel::new).field = field;
            }
            for (Method method : type.getMethods()) {
                if (isGetterName(method) && method.getParameterCount() == 0 && !method.isSynthetic() && !method.isBridge() && method.getDeclaringClass() != Object.class) {
                    models.computeIfAbsent(implicitPropertyName(method), PropertyModel::new).getter = method;
                } else if (method.getName().startsWith("set") && method.getName().length() > 3 && method.getParameterCount() == 1 && !method.isSynthetic() && !method.isBridge()) {
                    models.computeIfAbsent(implicitPropertyName(method), PropertyModel::new).setter = method;
                }
            }
            return models;
        }

        private static @Nullable Field field(Class<?> type, String name) {
            Class<?> current = type;
            while (current != Object.class && current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static @Nullable JsonbTypeAdapter typeAdapter(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
            if (accessor != null) {
                JsonbTypeAdapter annotation = accessor.getAnnotation(JsonbTypeAdapter.class);
                if (annotation != null) {
                    return annotation;
                }
                Field matchingField = field != null ? field : field(beanType, implicitPropertyName(accessor));
                return matchingField == null ? null : matchingField.getAnnotation(JsonbTypeAdapter.class);
            }
            return field == null ? null : field.getAnnotation(JsonbTypeAdapter.class);
        }

        private static @Nullable JsonbTypeSerializer typeSerializer(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
            if (accessor != null) {
                JsonbTypeSerializer annotation = accessor.getAnnotation(JsonbTypeSerializer.class);
                if (annotation != null) {
                    return annotation;
                }
                Field matchingField = field != null ? field : field(beanType, implicitPropertyName(accessor));
                return matchingField == null ? null : matchingField.getAnnotation(JsonbTypeSerializer.class);
            }
            return field == null ? null : field.getAnnotation(JsonbTypeSerializer.class);
        }

        private static @Nullable JsonbTypeDeserializer typeDeserializer(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
            if (accessor != null) {
                JsonbTypeDeserializer annotation = accessor.getAnnotation(JsonbTypeDeserializer.class);
                if (annotation != null) {
                    return annotation;
                }
                Field matchingField = field != null ? field : field(beanType, implicitPropertyName(accessor));
                return matchingField == null ? null : matchingField.getAnnotation(JsonbTypeDeserializer.class);
            }
            return field == null ? null : field.getAnnotation(JsonbTypeDeserializer.class);
        }

        private static <T> T jsonbComponent(Class<T> type) {
            return JsonbBridgeSupport.ComponentFactory.cdiBean(type).orElseGet(() -> instantiate(type));
        }

        private static boolean hasJsonbCustomization(Annotation[] annotations) {
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                Package annotationPackage = annotationType.getPackage();
                if (annotationPackage != null
                    && "jakarta.json.bind.annotation".equals(annotationPackage.getName())
                    && annotationType != JsonbTransient.class) {
                    return true;
                }
            }
            return false;
        }

        private static @Nullable JsonbNumberFormat numberFormat(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
            JsonbNumberFormat format = accessor == null ? null : accessor.getAnnotation(JsonbNumberFormat.class);
            if (format != null) {
                return format;
            }
            format = field == null ? null : field.getAnnotation(JsonbNumberFormat.class);
            if (format != null) {
                return format;
            }
            format = beanType.getAnnotation(JsonbNumberFormat.class);
            if (format != null) {
                return format;
            }
            Package beanPackage = beanType.getPackage();
            return beanPackage == null ? null : beanPackage.getAnnotation(JsonbNumberFormat.class);
        }

        private static @Nullable JsonbDateFormat dateFormat(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
            JsonbDateFormat format = accessor == null ? null : accessor.getAnnotation(JsonbDateFormat.class);
            if (format != null) {
                return format;
            }
            format = field == null ? null : field.getAnnotation(JsonbDateFormat.class);
            if (format != null) {
                return format;
            }
            format = beanType.getAnnotation(JsonbDateFormat.class);
            if (format != null) {
                return format;
            }
            Package beanPackage = beanType.getPackage();
            return beanPackage == null ? null : beanPackage.getAnnotation(JsonbDateFormat.class);
        }

        private static String formatNumber(Number value, JsonbNumberFormat format) {
            return decimalFormat(format).format(value);
        }

        private static Number parseNumber(String value, JsonbNumberFormat format) {
            try {
                return decimalFormat(format).parse(value);
            } catch (ParseException e) {
                throw new JsonbException("Cannot parse JSON-B number value", e);
            }
        }

        private static DecimalFormat decimalFormat(JsonbNumberFormat format) {
            Locale locale = numberLocale(format);
            DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getInstance(locale);
            if ("fr".equals(locale.getLanguage())) {
                DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
                symbols.setGroupingSeparator('\u00A0');
                decimalFormat.setDecimalFormatSymbols(symbols);
            }
            if (!format.value().isEmpty()) {
                decimalFormat.applyPattern(format.value());
            }
            return decimalFormat;
        }

        private static SimpleDateFormat dateFormat(JsonbDateFormat format) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format.value(), annotationLocale(format.locale(), Locale.getDefault()));
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat;
        }

        private static Locale numberLocale(JsonbNumberFormat format) {
            if ("##default".equals(format.locale())) {
                return format.value().isEmpty() ? Locale.getDefault() : Locale.US;
            }
            return annotationLocale(format.locale(), Locale.getDefault());
        }

        private static Locale annotationLocale(String locale, Locale defaultLocale) {
            if (locale.isEmpty() || "##default".equals(locale)) {
                return defaultLocale;
            }
            return Locale.forLanguageTag(locale.replace('_', '-'));
        }

        private static @Nullable String annotationPropertyName(@Nullable Field field) {
            if (field == null) {
                return null;
            }
            JsonbProperty property = field.getAnnotation(JsonbProperty.class);
            return property == null || property.value().isEmpty() ? null : property.value();
        }

        private static @Nullable String annotationPropertyName(@Nullable Method method) {
            if (method == null) {
                return null;
            }
            JsonbProperty property = method.getAnnotation(JsonbProperty.class);
            return property == null || property.value().isEmpty() ? null : property.value();
        }

        private static boolean isGetterName(Method method) {
            return (method.getName().startsWith("get") && method.getName().length() > 3)
                || (method.getName().startsWith("is") && method.getName().length() > 2);
        }

        private static String implicitPropertyName(Method method) {
            String name = method.getName();
            if (name.startsWith("get") || name.startsWith("set")) {
                return decapitalize(name.substring(3));
            }
            return decapitalize(name.substring(2));
        }

        private static String translateName(String name, @Nullable Object namingStrategy) {
            if (namingStrategy instanceof PropertyNamingStrategy strategy) {
                return strategy.translateName(name);
            }
            if (namingStrategy == null || PropertyNamingStrategy.IDENTITY.equals(namingStrategy) || PropertyNamingStrategy.CASE_INSENSITIVE.equals(namingStrategy)) {
                return name;
            }
            if (PropertyNamingStrategy.LOWER_CASE_WITH_DASHES.equals(namingStrategy)) {
                return splitCamelCase(name, "-").toLowerCase(java.util.Locale.ROOT);
            }
            if (PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES.equals(namingStrategy)) {
                return splitCamelCase(name, "_").toLowerCase(java.util.Locale.ROOT);
            }
            if (PropertyNamingStrategy.UPPER_CAMEL_CASE.equals(namingStrategy)) {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            if (PropertyNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES.equals(namingStrategy)) {
                String upperCamel = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                return splitCamelCase(upperCamel, " ");
            }
            return name;
        }

        private static String splitCamelCase(String name, String separator) {
            StringBuilder builder = new StringBuilder(name.length() + 8);
            for (int i = 0; i < name.length(); i++) {
                char character = name.charAt(i);
                if (i > 0 && Character.isUpperCase(character)) {
                    builder.append(separator);
                }
                builder.append(character);
            }
            return builder.toString();
        }

        private static String decapitalize(String name) {
            if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
                return name;
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        private static final class PropertyModel {
            private final String implicitName;
            private @Nullable Field field;
            private @Nullable Method getter;
            private @Nullable Method setter;

            PropertyModel(String implicitName) {
                this.implicitName = implicitName;
            }

            boolean isTransient() {
                return (field != null && Modifier.isTransient(field.getModifiers()))
                    || (field != null && field.isAnnotationPresent(JsonbTransient.class))
                    || (getter != null && getter.isAnnotationPresent(JsonbTransient.class))
                    || (setter != null && setter.isAnnotationPresent(JsonbTransient.class));
            }

            boolean hasJsonbTransient() {
                return (field != null && field.isAnnotationPresent(JsonbTransient.class))
                    || (getter != null && getter.isAnnotationPresent(JsonbTransient.class))
                    || (setter != null && setter.isAnnotationPresent(JsonbTransient.class));
            }

            boolean hasJsonbCustomization() {
                return (field != null && ReflectionFallback.hasJsonbCustomization(field.getAnnotations()))
                    || (getter != null && ReflectionFallback.hasJsonbCustomization(getter.getAnnotations()))
                    || (setter != null && ReflectionFallback.hasJsonbCustomization(setter.getAnnotations()));
            }

            @Nullable String serializationName(@Nullable Object namingStrategy) {
                if (getter == null && field == null) {
                    return null;
                }
                String name = annotationPropertyName(getter);
                if (name == null) {
                    name = annotationPropertyName(field);
                }
                return name == null ? translateName(implicitName, namingStrategy) : name;
            }

            @Nullable String deserializationName(@Nullable Object namingStrategy) {
                if (setter == null && field == null) {
                    return null;
                }
                String name = annotationPropertyName(setter);
                if (name == null) {
                    name = annotationPropertyName(field);
                }
                return name == null ? translateName(implicitName, namingStrategy) : name;
            }
        }
    }
}
