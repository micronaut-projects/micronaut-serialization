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

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reflection metadata and validation helpers for JSON-B fallback discovery.
 * <p>
 * This utility deliberately stops at metadata discovery, naming, visibility,
 * and JSON-B validation. Value conversion belongs in Serde codecs and
 * JSON-B-specific encoder/decoder bridges.
 */
final class JsonbReflectionUtil {
    private JsonbReflectionUtil() {
    }

    /**
     * Instantiates a type through its no-arg constructor for runtime fallback
     * models that do not have a creator constructor.
     *
     * @param type The type to instantiate
     * @param <T> The bean type
     * @return The new instance
     */
    static <T> T instantiate(Class<T> type) {
        Constructor<T> constructor = ReflectionUtils.findConstructor(type)
            .orElseThrow(() -> new JsonbException("No default constructor available for JSON-B fallback type " + type.getName()));
        if (!constructor.trySetAccessible()) {
            throw new JsonbException("Cannot access default constructor for JSON-B fallback type " + type.getName());
        }
        return InstantiationUtils.tryInstantiate(constructor)
            .orElseThrow(() -> new JsonbException("Cannot instantiate JSON-B fallback type " + type.getName()));
    }

    /**
     * Returns declared fields from the full class hierarchy in superclass-first
     * order. Runtime property discovery depends on this order for stable
     * fallback property ordering.
     *
     * @param type The type to inspect
     * @return The hierarchy fields
     */
    static List<Field> fields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        List<Class<?>> hierarchy = resolveHierarchy(type);
        for (Class<?> hierarchyType : hierarchy) {
            Collections.addAll(fields, hierarchyType.getDeclaredFields());
        }
        return fields;
    }

    /**
     * Tests whether a type should be treated as a scalar by JSON-B model
     * discovery. Scalar values are handled by registered Serde codecs instead of
     * runtime bean introspection.
     *
     * @param type The type to test
     * @return Whether the type is scalar for JSON-B fallback purposes
     */
    static boolean isJsonScalar(Class<?> type) {
        return JsonbScalarTypes.isJsonScalar(type);
    }

    /**
     * Tests whether a method is a JSON-B visible getter before an optional
     * {@link PropertyVisibilityStrategy} is applied.
     *
     * @param method The method to inspect
     * @return Whether the method is a getter candidate
     */
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

    /**
     * Tests whether a method is a JSON-B visible setter before an optional
     * {@link PropertyVisibilityStrategy} is applied.
     *
     * @param method The method to inspect
     * @return Whether the method is a setter candidate
     */
    static boolean isSetter(Method method) {
        return method.getParameterCount() == 1
            && NameUtils.isSetterName(method.getName())
            && !method.isSynthetic()
            && !method.isBridge()
            && !method.isAnnotationPresent(JsonbTransient.class);
    }

    /**
     * Tests whether a field is eligible to become a JSON-B property before an
     * optional {@link PropertyVisibilityStrategy} is applied.
     *
     * @param field The field to inspect
     * @return Whether the field is a property candidate
     */
    static boolean isFieldProperty(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
            && !Modifier.isTransient(modifiers)
            && !field.isSynthetic()
            && !field.isAnnotationPresent(JsonbTransient.class);
    }

    /**
     * Applies a JSON-B visibility strategy to a getter candidate.
     *
     * @param method The getter candidate
     * @param visibilityStrategy The configured strategy, if any
     * @return Whether the getter is visible
     */
    static boolean isVisible(Method method, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        return visibilityStrategy == null || visibilityStrategy.isVisible(method);
    }

    /**
     * Applies a JSON-B visibility strategy to a setter candidate.
     *
     * @param method The setter candidate
     * @param visibilityStrategy The configured strategy, if any
     * @return Whether the setter is visible
     */
    static boolean isVisibleSetter(Method method, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        return visibilityStrategy == null || visibilityStrategy.isVisible(method);
    }

    /**
     * Applies a JSON-B visibility strategy to a field candidate.
     *
     * @param field The field candidate
     * @param visibilityStrategy The configured strategy, if any
     * @return Whether the field is visible
     */
    static boolean isVisible(Field field, @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        return visibilityStrategy == null || visibilityStrategy.isVisible(field);
    }

    /**
     * Resolves Java and JSON-B transient properties for a type. The returned
     * names are implicit JavaBean names, not naming-strategy translated JSON
     * names.
     *
     * @param type The type to inspect
     * @return The transient property names
     */
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

    /**
     * Validates the JSON-B default-constructor rule for runtime deserialization.
     * Generated deserializers and explicit creators are allowed to bypass this
     * check; ordinary fallback beans are not.
     *
     * @param type The bean type to validate
     */
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

    /**
     * Validates the JSON-B creator constraints represented by the runtime
     * constructor adapter.
     *
     * @param type The bean type to validate
     */
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

    /**
     * Detects accessors whose implicit property name points at a static field.
     * JSON-B can see such methods, but generated Serde property metadata cannot
     * safely model that mixed static/instance shape.
     *
     * @param type The declaring type
     * @param implicitName The JavaBean property name
     * @return Whether a static field backs the accessor name
     */
    static boolean isStaticBackedAccessor(Class<?> type, String implicitName) {
        Field field = field(type, implicitName).orElse(null);
        return field != null && Modifier.isStatic(field.getModifiers());
    }

    /**
     * Finds the nearest JSON-B property order annotation in the class hierarchy.
     *
     * @param type The type to inspect
     * @return The property order annotation, if any
     */
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

    /**
     * Resolves the effective JSON-B number format for a property, including
     * accessor, field, type, and package scopes.
     *
     * @param accessor The accessor member, if any
     * @param field The field member, if any
     * @param beanType The owning bean type
     * @return The effective number format, if any
     */
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

    /**
     * Resolves the effective JSON-B date format for a property, including
     * accessor, field, type, and package scopes.
     *
     * @param accessor The accessor member, if any
     * @param field The field member, if any
     * @param beanType The owning bean type
     * @return The effective date format, if any
     */
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

    /**
     * Tests only the JavaBean getter name shape. This is used when validation
     * needs to inspect members even if JSON-B visibility would later exclude
     * them from the active property set.
     *
     * @param method The method to inspect
     * @return Whether the method has a getter-style name
     */
    static boolean isGetterName(Method method) {
        return NameUtils.isGetterName(method.getName());
    }

    /**
     * Derives the implicit JavaBean property name from a getter or setter.
     *
     * @param method The accessor method
     * @return The implicit property name
     */
    static String implicitPropertyName(Method method) {
        if (isGetterName(method)) {
            return NameUtils.getPropertyNameForGetter(method.getName());
        } else {
            return NameUtils.getPropertyNameForSetter(method.getName());
        }
    }

    /**
     * Applies the configured JSON-B property naming strategy to an implicit
     * JavaBean property name.
     *
     * @param name The implicit property name
     * @param namingStrategy The configured naming strategy, if any
     * @return The translated JSON property name
     */
    static String translateName(String name, @Nullable Object namingStrategy) {
        if (namingStrategy instanceof PropertyNamingStrategy strategy) {
            return strategy.translateName(name);
        }
        if (namingStrategy == null || PropertyNamingStrategy.IDENTITY.equals(namingStrategy) || PropertyNamingStrategy.CASE_INSENSITIVE.equals(namingStrategy)) {
            return name;
        }
        if (PropertyNamingStrategy.LOWER_CASE_WITH_DASHES.equals(namingStrategy)) {
            return NameUtils.hyphenate(name, true);
        }
        if (PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES.equals(namingStrategy)) {
            return NameUtils.underscoreSeparate(name, true);
        }
        if (PropertyNamingStrategy.UPPER_CAMEL_CASE.equals(namingStrategy)) {
            return NameUtils.capitalize(name);
        }
        if (PropertyNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES.equals(namingStrategy)) {
            String upperCamel = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return splitCamelCase(upperCamel);
        }
        return name;
    }

    /**
     * JSON-B-compatible JavaBean decapitalization. Leading acronyms are left
     * intact to match the JavaBeans convention.
     *
     * @param name The accessor suffix
     * @return The decapitalized property name
     */
    static String decapitalize(String name) {
        return Objects.requireNonNull(NameUtils.decapitalize(name));
    }

    private static Optional<Field> field(Class<?> type, String name) {
        return ReflectionUtils.findField(type, name);
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
        return NameUtils.isSetterName(method.getName());
    }

    private static String splitCamelCase(String name) {
        StringBuilder builder = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (i > 0 && Character.isUpperCase(character)) {
                builder.append(" ");
            }
            builder.append(character);
        }
        return builder.toString();
    }

    static List<Class<?>> resolveHierarchy(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy);
        return hierarchy;
    }
}
