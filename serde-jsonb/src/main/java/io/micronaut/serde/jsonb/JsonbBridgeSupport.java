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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Optional;

/**
 * Shared reflection and component helpers used by JSON-B annotation bridges.
 * <p>
 * These methods translate JSON-B annotation metadata into concrete callback
 * classes and Serde {@link Argument} instances. They should stay independent of
 * actual value conversion, which belongs in the codec bridges.
 */
final class JsonbBridgeSupport {
    private JsonbBridgeSupport() {
    }

    /**
     * Reads the adapter class encoded in {@link JsonbSerdeConfig} metadata.
     *
     * @param annotationMetadata The runtime or generated annotation metadata
     * @return The configured adapter class
     * @throws SerdeException If the metadata is missing
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbAdapter> adapterClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "adapter")
            .map(type -> type.asSubclass(JsonbAdapter.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B adapter metadata"));
    }

    /**
     * Reads the serializer class encoded in {@link JsonbSerdeConfig} metadata.
     *
     * @param annotationMetadata The runtime or generated annotation metadata
     * @return The configured serializer class
     * @throws SerdeException If the metadata is missing
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbSerializer> serializerClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "serializer")
            .map(type -> type.asSubclass(JsonbSerializer.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B serializer metadata"));
    }

    /**
     * Reads the deserializer class encoded in {@link JsonbSerdeConfig} metadata.
     *
     * @param annotationMetadata The runtime or generated annotation metadata
     * @return The configured deserializer class
     * @throws SerdeException If the metadata is missing
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbDeserializer> deserializerClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "deserializer")
            .map(type -> type.asSubclass(JsonbDeserializer.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B deserializer metadata"));
    }

    /**
     * Resolves the adapted JSON representation type from a JSON-B adapter's
     * generic signature.
     *
     * @param adapterClass The adapter class
     * @return The adapted target type, or {@code Object.class} when unresolved
     */
    @SuppressWarnings({"rawtypes"})
    static Type adaptedType(Class<? extends JsonbAdapter> adapterClass) {
        Type type = findAdapterType(adapterClass);
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 2) {
            return parameterizedType.getActualTypeArguments()[1];
        }
        return Object.class;
    }

    private static @Nullable Type findAdapterType(Class<?> type) {
        for (Type genericInterface : type.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() == JsonbAdapter.class) {
                return parameterizedType;
            }
            if (genericInterface instanceof Class<?> interfaceClass) {
                Type adapterType = findAdapterType(interfaceClass);
                if (adapterType != null) {
                    return adapterType;
                }
            }
        }
        Type genericSuperclass = type.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType parameterizedType
            && parameterizedType.getRawType() instanceof Class<?> superClass) {
            if (superClass == JsonbAdapter.class) {
                return parameterizedType;
            }
            return findAdapterType(superClass);
        }
        if (genericSuperclass instanceof Class<?> superClass && superClass != Object.class) {
            return findAdapterType(superClass);
        }
        return null;
    }

    /**
     * Converts a reflection {@link Type} into a Serde {@link Argument}. This
     * keeps JSON-B runtime adapters/deserializers on the same generic type model
     * used by generated Serde code.
     *
     * @param runtimeType The reflection type
     * @return The corresponding Serde argument
     */
    static Argument<?> argument(Type runtimeType) {
        switch (runtimeType) {
            case TypeVariable<?> typeVariable -> {
                Type[] bounds = typeVariable.getBounds();
                return bounds.length == 0 || bounds[0] == Object.class ? Argument.OBJECT_ARGUMENT : argument(bounds[0]);
            }
            case WildcardType wildcardType -> {
                Type[] upperBounds = wildcardType.getUpperBounds();
                return upperBounds.length == 0 || upperBounds[0] == Object.class ? Argument.OBJECT_ARGUMENT : argument(upperBounds[0]);
            }
            case GenericArrayType genericArrayType -> {
                Argument<?> component = argument(genericArrayType.getGenericComponentType());
                Class<?> arrayType = Array.newInstance(component.getType(), 0).getClass();
                return Argument.of(arrayType, component);
            }
            case
                ParameterizedType parameterizedType when parameterizedType.getRawType() instanceof Class<?> rawType -> {
                Type[] actualTypes = parameterizedType.getActualTypeArguments();
                Argument<?>[] typeArguments = new Argument<?>[actualTypes.length];
                for (int i = 0; i < actualTypes.length; i++) {
                    typeArguments[i] = argument(actualTypes[i]);
                }
                return Argument.of(rawType, typeArguments);
            }
            default -> {
            }
        }
        return Argument.of(runtimeType);
    }

    /**
     * Resolves JSON-B callback components from Micronaut beans, CDI beans, or
     * reflective construction, in that order.
     */
    static final class ComponentFactory {
        private final BeanContext beanContext;

        /**
         * @param beanContext The Micronaut bean context used for callback lookup
         */
        ComponentFactory(BeanContext beanContext) {
            this.beanContext = beanContext;
        }

        /**
         * Resolves or creates a JSON-B callback component.
         *
         * @param type The component type
         * @param <T> The component type
         * @return The component instance
         */
        <T> T get(Class<T> type) {
            return beanContext.findBean(type)
                .or(() -> cdiBean(type))
                .orElseGet(() -> instantiate(type));
        }

        /**
         * Reflectively instantiates a JSON-B component as the final fallback.
         *
         * @param type The component type
         * @param <T> The component type
         * @return The component instance
         */
        static <T> T instantiate(Class<T> type) {
            try {
                Constructor<T> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new JsonbException("Cannot instantiate JSON-B component " + type.getName(), e);
            }
        }

        /**
         * Attempts CDI lookup without taking a compile-time dependency on CDI.
         *
         * @param type The component type
         * @param <T> The component type
         * @return The CDI component, if CDI is available and has a bean
         */
        static <T> Optional<T> cdiBean(Class<T> type) {
            try {
                Class<?> cdiType = Class.forName("jakarta.enterprise.inject.spi.CDI");
                Object cdi = cdiType.getMethod("current").invoke(null);
                Method select = cdi.getClass().getMethod("select", Class.class, Annotation[].class);
                Object instance = select.invoke(cdi, type, new Annotation[0]);
                return Optional.of(type.cast(instance.getClass().getMethod("get").invoke(instance)));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return Optional.empty();
            }
        }
    }

}
