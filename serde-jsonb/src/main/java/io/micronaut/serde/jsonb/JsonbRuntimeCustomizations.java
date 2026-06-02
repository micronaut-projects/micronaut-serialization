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

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime JSON-B customizations that are not part of Micronaut Serialization's generated metadata.
 * <p>
 * This package-private support object is shared by runtime introspections and the remaining reflection
 * fallback glue. It precomputes configured adapters, serializers, deserializers, and global date format
 * state once per mapper so the hot object paths do not rediscover JSON-B configuration repeatedly.
 * The object is registered by id because runtime Serde metadata can carry
 * annotation values, but not object references.
 */
final class JsonbRuntimeCustomizations {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ConcurrentMap<String, JsonbRuntimeCustomizations> CONFIGURED = new ConcurrentHashMap<>();

    private final String id;
    private final List<ConfigAdapter> adapters;
    private final List<ConfigSerializer> serializers;
    private final List<ConfigDeserializer> deserializers;

    private JsonbRuntimeCustomizations(List<ConfigAdapter> adapters,
                                       List<ConfigSerializer> serializers,
                                       List<ConfigDeserializer> deserializers) {
        this.id = "jsonb-config-" + IDS.incrementAndGet();
        this.adapters = adapters;
        this.serializers = serializers;
        this.deserializers = deserializers;
        if (!adapters.isEmpty() || !serializers.isEmpty() || !deserializers.isEmpty()) {
            CONFIGURED.put(id, this);
        }
    }

    /**
     * Extracts configured JSON-B adapters, serializers, and deserializers from
     * a mapper configuration.
     *
     * @param config The JSON-B configuration
     * @return The runtime customization registry for the mapper
     */
    static JsonbRuntimeCustomizations of(JsonbConfig config) {
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
        return new JsonbRuntimeCustomizations(adapters, serializers, deserializers);
    }

    /**
     * Resolves a customization registry referenced from runtime Serde metadata.
     *
     * @param id The metadata id
     * @return The registered customization registry
     */
    static JsonbRuntimeCustomizations get(String id) {
        JsonbRuntimeCustomizations customizations = CONFIGURED.get(id);
        if (customizations == null) {
            throw new JsonbException("JSON-B configured customization is no longer available");
        }
        return customizations;
    }

    /**
     * Returns whether configured serializers or adapters may affect writes.
     *
     * @return Whether write customizations are present
     */
    boolean hasSerializers() {
        return !adapters.isEmpty() || !serializers.isEmpty();
    }

    /**
     * Returns whether configured deserializers or adapters may affect reads.
     *
     * @return Whether read customizations are present
     */
    boolean hasDeserializers() {
        return !adapters.isEmpty() || !deserializers.isEmpty();
    }

    /**
     * Applies bridge Serde metadata to a runtime property argument when JSON-B
     * configuration contains a matching adapter, serializer, or deserializer.
     *
     * @param metadata The mutable runtime annotation metadata
     * @param type The property or element type being modeled
     */
    void applySerdeMetadata(MutableAnnotationMetadata metadata, Type type) {
        boolean hasSerializer = hasSerializer(type);
        boolean hasDeserializer = hasDeserializer(type);
        if (hasSerializer || hasDeserializer) {
            Map<CharSequence, Object> serdeValues = new java.util.LinkedHashMap<>();
            if (hasSerializer) {
                serdeValues.put(io.micronaut.serde.config.annotation.SerdeConfig.SERIALIZER_CLASS, new io.micronaut.core.annotation.AnnotationClassValue<>(JsonbConfiguredSerde.class));
            }
            if (hasDeserializer) {
                serdeValues.put(io.micronaut.serde.config.annotation.SerdeConfig.DESERIALIZER_CLASS, new io.micronaut.core.annotation.AnnotationClassValue<>(JsonbConfiguredSerde.class));
            }
            metadata.addAnnotation(io.micronaut.serde.config.annotation.SerdeConfig.class.getName(), serdeValues);
            metadata.addAnnotation(JsonbSerdeConfig.class.getName(), Map.of("customization", id));
        }
    }

    private boolean hasSerializer(Type type) {
        for (ConfigSerializer serializer : serializers) {
            if (matches(type, serializer.type())) {
                return true;
            }
        }
        for (ConfigAdapter adapter : adapters) {
            if (matches(type, adapter.sourceType())) {
                return true;
            }
        }
        if (type instanceof ParameterizedType parameterizedType) {
            for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
                if (hasSerializer(typeArgument)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasDeserializer(Type type) {
        for (ConfigDeserializer deserializer : deserializers) {
            if (matches(type, deserializer.type())) {
                return true;
            }
        }
        for (ConfigAdapter adapter : adapters) {
            if (matches(type, adapter.sourceType())) {
                return true;
            }
        }
        if (type instanceof ParameterizedType parameterizedType) {
            for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
                if (hasDeserializer(typeArgument)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Applies a configured serializer or adapter to a top-level fallback value.
     *
     * @param value The value being serialized
     * @param codec The bounded Serde codec bridge
     * @return The customized JSON tree, or {@code null} when no customization matched
     */
    @Nullable JsonNode serialize(@Nullable Object value, JsonbFallbackCodec codec) {
        if (value == null) {
            return null;
        }
        for (ConfigSerializer serializer : serializers) {
            if (matches(serializer.type(), value.getClass())) {
                try {
                    return JsonbJsonpBridge.writeWithJsonbSerializer(serializer.serializer(), value, codec);
                } catch (IOException e) {
                    throw new JsonbException("Cannot serialize JSON-B value with configured serializer", e);
                }
            }
        }
        for (ConfigAdapter adapter : adapters) {
            if (matches(adapter.sourceType(), value.getClass())) {
                try {
                    Object adapted = adapter.adapter().adaptToJson(value);
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Argument<Object> argument = adapted == null ? Argument.OBJECT_ARGUMENT : (Argument) Argument.of(adapted.getClass());
                    return codec.writeValueToTree(argument, adapted);
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B value for serialization", e);
                }
            }
        }
        return null;
    }

    /**
     * Applies a configured deserializer or adapter to a top-level fallback tree.
     *
     * @param value The tree being deserialized
     * @param targetType The requested target type
     * @param codec The bounded Serde codec bridge
     * @return The customized value, or {@code null} when no customization matched
     */
    @Nullable Object deserialize(JsonNode value,
                                 Type targetType,
                                 JsonbFallbackCodec codec) {
        for (ConfigDeserializer deserializer : deserializers) {
            if (matches(targetType, deserializer.type())) {
                try (jakarta.json.stream.JsonParser parser = JsonbJsonpBridge.parserForDeserializer(value)) {
                    return deserializer.deserializer().deserialize(parser, new JsonbDeserializationContext(codec), targetType);
                } catch (RuntimeException e) {
                    throw new JsonbException("Cannot deserialize JSON-B value with configured deserializer", e);
                }
            }
        }
        for (ConfigAdapter adapter : adapters) {
            if (matches(targetType, adapter.sourceType())) {
                Object adapted;
                try {
                    adapted = codec.readValue(value, adapter.targetType());
                } catch (IOException e) {
                    throw new JsonbException("Cannot adapt JSON-B value for deserialization", e);
                }
                try {
                    return adapter.adapter().adaptFromJson(adapted);
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B value for deserialization", e);
                }
            }
        }
        return null;
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
            int length = Array.getLength(value);
            List<T> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
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
            case ParameterizedType parameterizedType when parameterizedType.getRawType() instanceof Class<?> clazz -> clazz;
            case GenericArrayType genericArrayType -> Array.newInstance(erasedType(genericArrayType.getGenericComponentType()), 0).getClass();
            default -> Object.class;
        };
    }

    /**
     * Finds a configured JSON-B serializer for a type.
     *
     * @param type The requested type
     * @return The serializer configuration, if any
     */
    @Nullable ConfigSerializer serializer(Type type) {
        for (ConfigSerializer serializer : serializers) {
            if (matches(type, serializer.type())) {
                return serializer;
            }
        }
        return null;
    }

    /**
     * Finds a configured JSON-B deserializer for a type.
     *
     * @param type The requested type
     * @return The deserializer configuration, if any
     */
    @Nullable ConfigDeserializer deserializer(Type type) {
        for (ConfigDeserializer deserializer : deserializers) {
            if (matches(type, deserializer.type())) {
                return deserializer;
            }
        }
        return null;
    }

    /**
     * Finds a configured JSON-B adapter for a source type.
     *
     * @param type The requested source type
     * @return The adapter configuration, if any
     */
    @Nullable ConfigAdapter adapter(Type type) {
        for (ConfigAdapter adapter : adapters) {
            if (matches(type, adapter.sourceType())) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * Configured JSON-B adapter plus the source and adapted target types
     * discovered from its generic signature.
     *
     * @param sourceType The JSON-B model type
     * @param targetType The adapted JSON representation type
     * @param adapter The adapter instance
     */
    record ConfigAdapter(Type sourceType, Type targetType, JsonbAdapter<Object, Object> adapter) {
        /**
         * Creates a typed adapter configuration from a user-supplied adapter.
         *
         * @param sourceType The JSON-B model type
         * @param targetType The adapted JSON representation type
         * @param adapter The adapter instance
         * @return The adapter configuration
         */
        @SuppressWarnings("unchecked")
        static ConfigAdapter of(Type sourceType, Type targetType, JsonbAdapter<?, ?> adapter) {
            return new ConfigAdapter(sourceType, targetType, (JsonbAdapter<Object, Object>) adapter);
        }
    }

    /**
     * Configured JSON-B serializer plus the type it handles.
     *
     * @param type The handled type
     * @param serializer The serializer instance
     */
    record ConfigSerializer(Type type, JsonbSerializer<Object> serializer) {
        /**
         * Creates a typed serializer configuration from a user-supplied serializer.
         *
         * @param type The handled type
         * @param serializer The serializer instance
         * @return The serializer configuration
         */
        @SuppressWarnings("unchecked")
        static ConfigSerializer of(Type type, JsonbSerializer<?> serializer) {
            return new ConfigSerializer(type, (JsonbSerializer<Object>) serializer);
        }
    }

    /**
     * Configured JSON-B deserializer plus the type it handles.
     *
     * @param type The handled type
     * @param deserializer The deserializer instance
     */
    record ConfigDeserializer(Type type, JsonbDeserializer<Object> deserializer) {
        /**
         * Creates a typed deserializer configuration from a user-supplied deserializer.
         *
         * @param type The handled type
         * @param deserializer The deserializer instance
         * @return The deserializer configuration
         */
        @SuppressWarnings("unchecked")
        static ConfigDeserializer of(Type type, JsonbDeserializer<?> deserializer) {
            return new ConfigDeserializer(type, (JsonbDeserializer<Object>) deserializer);
        }
    }
}
