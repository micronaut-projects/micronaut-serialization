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

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Reflection metadata and validation helpers for JSON-B fallback discovery.
 */
final class ReflectionFallback {
    private ReflectionFallback() {
    }

    static <T> T instantiate(Class<T> type) {
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

    static List<Field> fields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = type;
        while (current != Object.class && current != null) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy);
        for (Class<?> hierarchyType : hierarchy) {
            Collections.addAll(fields, hierarchyType.getDeclaredFields());
        }
        return fields;
    }

    static boolean isJsonScalar(Class<?> type) {
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

    static boolean isGetter(Method method) {
        return method.getParameterCount() == 0
            && !method.getReturnType().equals(Void.TYPE)
            && !method.isSynthetic()
            && !method.isBridge()
            && !method.isAnnotationPresent(JsonbTransient.class)
            && method.getDeclaringClass() != Object.class
            && ((method.getName().startsWith("get") && method.getName().length() > 3)
            || (method.getName().startsWith("is") && method.getName().length() > 2 && method.getReturnType() == boolean.class));
    }

    static boolean isSetter(Method method) {
        return method.getParameterCount() == 1
            && method.getName().startsWith("set")
            && method.getName().length() > 3
            && !method.isSynthetic()
            && !method.isBridge()
            && !method.isAnnotationPresent(JsonbTransient.class);
    }

    static boolean isFieldProperty(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
            && !Modifier.isTransient(modifiers)
            && !field.isSynthetic()
            && !field.isAnnotationPresent(JsonbTransient.class);
    }

    static boolean isVisible(Method method, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        return visibilityStrategy == null || visibilityStrategy.isVisible(method);
    }

    static boolean isVisibleSetter(Method method, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        return visibilityStrategy == null || visibilityStrategy.isVisible(method);
    }

    static boolean isVisible(Field field, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        return visibilityStrategy == null || visibilityStrategy.isVisible(field);
    }

    static Set<String> transientProperties(Class<?> type) {
        Set<String> properties = new HashSet<>();
        for (Field field : fields(type)) {
            if (Modifier.isTransient(field.getModifiers()) || field.isAnnotationPresent(JsonbTransient.class)) {
                properties.add(field.getName());
            }
        }
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(JsonbTransient.class)
                && (isGetterName(method) || (isSetterName(method) && method.getParameterCount() == 1))) {
                properties.add(implicitPropertyName(method));
            }
        }
        return properties;
    }

    static void validateDefaultConstructorAccess(Class<?> type) {
        if (isJsonScalar(type) || type.isEnum() || type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return;
        }
        if (creatorCount(type) > 0) {
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

    static void validateCreatorModel(Class<?> type) {
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

    static boolean isStaticBackedAccessor(Class<?> type, String implicitName) {
        Field field = field(type, implicitName);
        return field != null && Modifier.isStatic(field.getModifiers());
    }

    static @Nullable JsonbPropertyOrder propertyOrder(Class<?> type) {
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

    static @Nullable JsonbNumberFormat numberFormat(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
        JsonbNumberFormat format = accessor == null ? null : accessor.getAnnotation(JsonbNumberFormat.class);
        if (format != null) {
            return format;
        }
        format = field == null ? null : field.getAnnotation(JsonbNumberFormat.class);
        if (format != null) {
            return format;
        }
        Class<?> current = beanType;
        while (current != null && current != Object.class) {
            format = current.getAnnotation(JsonbNumberFormat.class);
            if (format != null) {
                return format;
            }
            current = current.getSuperclass();
        }
        current = beanType;
        while (current != null && current != Object.class) {
            Package beanPackage = current.getPackage();
            format = beanPackage == null ? null : beanPackage.getAnnotation(JsonbNumberFormat.class);
            if (format != null) {
                return format;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    static @Nullable JsonbDateFormat dateFormat(@Nullable Method accessor, @Nullable Field field, Class<?> beanType) {
        JsonbDateFormat format = accessor == null ? null : accessor.getAnnotation(JsonbDateFormat.class);
        if (format != null) {
            return format;
        }
        format = field == null ? null : field.getAnnotation(JsonbDateFormat.class);
        if (format != null) {
            return format;
        }
        Class<?> current = beanType;
        while (current != null && current != Object.class) {
            format = current.getAnnotation(JsonbDateFormat.class);
            if (format != null) {
                return format;
            }
            current = current.getSuperclass();
        }
        current = beanType;
        while (current != null && current != Object.class) {
            Package beanPackage = current.getPackage();
            format = beanPackage == null ? null : beanPackage.getAnnotation(JsonbDateFormat.class);
            if (format != null) {
                return format;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    static boolean isGetterName(Method method) {
        return (method.getName().startsWith("get") && method.getName().length() > 3)
            || (method.getName().startsWith("is") && method.getName().length() > 2);
    }

    static String implicitPropertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") || name.startsWith("set")) {
            return decapitalize(name.substring(3));
        }
        return decapitalize(name.substring(2));
    }

    static String translateName(String name, @Nullable Object namingStrategy) {
        if (namingStrategy instanceof PropertyNamingStrategy strategy) {
            return strategy.translateName(name);
        }
        if (namingStrategy == null || PropertyNamingStrategy.IDENTITY.equals(namingStrategy) || PropertyNamingStrategy.CASE_INSENSITIVE.equals(namingStrategy)) {
            return name;
        }
        if (PropertyNamingStrategy.LOWER_CASE_WITH_DASHES.equals(namingStrategy)) {
            return splitCamelCase(name, "-").toLowerCase(Locale.ROOT);
        }
        if (PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES.equals(namingStrategy)) {
            return splitCamelCase(name, "_").toLowerCase(Locale.ROOT);
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

    static String decapitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
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

    private static boolean isSetterName(Method method) {
        return method.getName().startsWith("set") && method.getName().length() > 3;
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
}
