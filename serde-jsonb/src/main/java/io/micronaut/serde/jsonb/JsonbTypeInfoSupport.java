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
import jakarta.json.bind.annotation.JsonbTypeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * JSON-B type-information helper shared by runtime introspection metadata and reflection fallback serialization.
 * <p>
 * The code centralizes validation and discriminator-property synthesis so array, generated,
 * and fallback object paths agree on the JSON-B subtype model without reintroducing buffered writes.
 * It does not serialize values directly; it only supplies metadata and synthetic
 * discriminator properties consumed by the normal Serde object pipeline.
 */
final class JsonbTypeInfoSupport {
    private JsonbTypeInfoSupport() {
    }

    /**
     * Returns whether the type hierarchy contains JSON-B type information.
     *
     * @param type The type to inspect
     * @return Whether the type hierarchy contains JSON-B type information.
     */
    static boolean hasTypeInfo(Class<?> type) {
        return !annotatedTypeInfoTypes(type).isEmpty();
    }

    /**
     * Builds the synthetic discriminator properties that must be appended to the
     * runtime Serde metadata for a concrete subtype.
     *
     * @param type The concrete type being serialized or modeled
     * @return A map from discriminator property name to alias value
     */
    static Map<String, Object> typeInfoProperties(Class<?> type) {
        List<Class<?>> annotatedTypes = annotatedTypeInfoTypes(type);
        if (annotatedTypes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        ensureSingleTypeInfoChain(annotatedTypes, type);
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Class<?> annotatedType : annotatedTypes) {
            JsonbTypeInfo typeInfo = annotatedType.getAnnotation(JsonbTypeInfo.class);
            if (typeInfo == null) {
                continue;
            }
            validateSubtypeAliases(annotatedType, typeInfo);
            validatePropertyName(annotatedType, typeInfo.key());
            for (jakarta.json.bind.annotation.JsonbSubtype subtype : typeInfo.value()) {
                if (subtype.type().isAssignableFrom(type)) {
                    properties.put(typeInfo.key(), subtype.alias());
                    break;
                }
            }
        }
        return properties;
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
            if ((methodName.startsWith("get") && methodName.length() > 3 && JsonbReflectionUtil.decapitalize(methodName.substring(3)).equals(key))
                || (methodName.startsWith("is") && methodName.length() > 2 && JsonbReflectionUtil.decapitalize(methodName.substring(2)).equals(key))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds all JSON-B type-info annotations visible from the class, interface,
     * and superclass hierarchy in deterministic base-to-leaf order.
     *
     * @param type The concrete type to inspect
     * @return Annotated hierarchy types
     */
    static List<Class<?>> annotatedTypeInfoTypes(Class<?> type) {
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

    /**
     * Validates that discovered JSON-B type-info annotations form one
     * inheritance chain. Multiple unrelated annotated interfaces are ambiguous
     * because JSON-B has no deterministic discriminator merge rule.
     *
     * @param annotatedTypes The annotated types discovered for the hierarchy
     * @param type The concrete type being modeled
     */
    static void ensureSingleTypeInfoChain(List<Class<?>> annotatedTypes, Class<?> type) {
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
}
