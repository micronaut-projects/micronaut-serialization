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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolves the property types the generated bean and record serdes describe with an
 * {@link io.micronaut.core.type.Argument}.
 */
@Internal
public final class SerdeSourceGenArgumentTypes {

    private SerdeSourceGenArgumentTypes() {
    }

    /**
     * Returns the type arguments of the given type, in declaration order.
     *
     * @param classElement The type
     * @return The type arguments
     */
    public static List<? extends ClassElement> typeArguments(ClassElement classElement) {
        Map<String, ClassElement> byName = classElement.getTypeArguments();
        if (!byName.isEmpty()) {
            return new ArrayList<>(byName.values());
        }
        return classElement.getBoundGenericTypes();
    }

    /**
     * Normalizes a property type to the type its argument is built for: primitives are boxed,
     * {@link Iterable} becomes {@link Collection}, and wildcards and unresolved type variables are
     * replaced by their bound.
     *
     * @param classElement The property type
     * @return The normalized type
     */
    public static ClassElement normalize(ClassElement classElement) {
        if (classElement.isPrimitive() && !classElement.isArray()) {
            return switch (classElement.getName()) {
                case "boolean" -> ClassElement.of(Boolean.class);
                case "byte" -> ClassElement.of(Byte.class);
                case "short" -> ClassElement.of(Short.class);
                case "char" -> ClassElement.of(Character.class);
                case "int" -> ClassElement.of(Integer.class);
                case "long" -> ClassElement.of(Long.class);
                case "float" -> ClassElement.of(Float.class);
                case "double" -> ClassElement.of(Double.class);
                default -> classElement;
            };
        }
        if ("java.lang.Iterable".equals(classElement.getName())) {
            ClassElement collectionType = ClassElement.of(Collection.class);
            List<? extends ClassElement> iterableTypeArguments = typeArguments(classElement);
            if (!iterableTypeArguments.isEmpty()) {
                collectionType = collectionType.withTypeArguments(new ArrayList<>(iterableTypeArguments));
            }
            return collectionType;
        }
        if (classElement instanceof WildcardElement wildcardElement) {
            if (!wildcardElement.getUpperBounds().isEmpty()) {
                return normalize(wildcardElement.getUpperBounds().get(0));
            }
            if (!wildcardElement.getLowerBounds().isEmpty()) {
                return normalize(wildcardElement.getLowerBounds().get(0));
            }
            return ClassElement.of(Object.class);
        }
        // Only an unresolved type variable is replaced by its bound. A type argument resolved from a type
        // variable, such as the upper bound of the wildcard in Map<String, ? extends Map<String, String>>
        // that Kotlin emits for nested collections, also reports isTypeVariable() and must be kept as is:
        // its bound generic types are its own type arguments, not bounds.
        if (classElement instanceof GenericPlaceholderElement placeholderElement) {
            List<? extends ClassElement> bounds = placeholderElement.getBounds();
            if (!bounds.isEmpty()) {
                return normalize(bounds.get(0));
            }
            return ClassElement.of(Object.class);
        }
        return classElement;
    }
}
