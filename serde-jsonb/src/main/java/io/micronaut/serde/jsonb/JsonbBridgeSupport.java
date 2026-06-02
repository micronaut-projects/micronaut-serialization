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

final class JsonbBridgeSupport {
    private JsonbBridgeSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbAdapter> adapterClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "adapter")
            .map(type -> type.asSubclass(JsonbAdapter.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B adapter metadata"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbSerializer> serializerClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "serializer")
            .map(type -> type.asSubclass(JsonbSerializer.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B serializer metadata"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbDeserializer> deserializerClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "deserializer")
            .map(type -> type.asSubclass(JsonbDeserializer.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B deserializer metadata"));
    }

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

    static final class ComponentFactory {
        private final BeanContext beanContext;

        ComponentFactory(BeanContext beanContext) {
            this.beanContext = beanContext;
        }

        <T> T get(Class<T> type) {
            return beanContext.findBean(type)
                .or(() -> cdiBean(type))
                .orElseGet(() -> instantiate(type));
        }

        static <T> T instantiate(Class<T> type) {
            try {
                Constructor<T> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new JsonbException("Cannot instantiate JSON-B component " + type.getName(), e);
            }
        }

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
