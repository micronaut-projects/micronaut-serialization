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
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.WildcardElement;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects property types that can refer back to the type a serde is generated for.
 *
 * <p>Generated serdes resolve their property serdes on construction, and the registry creates a
 * new generated serde for every specific lookup. A property whose type reaches the owning type
 * again, such as {@code List<Self>} or a property of another type that refers back to the owner,
 * would construct the owning serde again without end, so such properties resolve their serde
 * lazily.</p>
 */
@Internal
public final class SerdeSourceGenRecursion {

    private final ClassElement owner;
    /**
     * Types whose graph was fully explored without reaching the owner, shared across the
     * properties of the owner so that each type is explored once.
     */
    private final Set<String> notRecursive = new HashSet<>();

    /**
     * @param owner The type the serde is generated for
     */
    public SerdeSourceGenRecursion(ClassElement owner) {
        this.owner = owner;
    }

    /**
     * Whether the property type is the owning type itself, so the generated serde can reuse itself.
     *
     * @param owner The type the serde is generated for
     * @param propertyType The property type
     * @return {@code true} if the property type is the owning type
     */
    public static boolean isDirectlyRecursive(ClassElement owner, ClassElement propertyType) {
        return !propertyType.isArray() && propertyType.getName().equals(owner.getName());
    }

    /**
     * Whether the property type can refer back to the owning type, other than being the owning
     * type itself, which the generated serdes handle by reusing themselves.
     *
     * @param propertyType The property type
     * @return {@code true} if the property serde has to be resolved lazily
     */
    public boolean isIndirectlyRecursive(ClassElement propertyType) {
        if (isDirectlyRecursive(owner, propertyType)) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        if (reaches(propertyType, visited)) {
            return true;
        }
        // The search returned as soon as it reached the owner, so every type it visited without
        // reaching the owner is known not to lead back to it
        notRecursive.addAll(visited);
        return false;
    }

    /**
     * Kotlin declares {@code List<Self>} inside a collection as {@code ? extends List<Self>}, so the
     * bounds are what the property can hold.
     */
    private boolean boundsReach(WildcardElement wildcardElement, Set<String> visited) {
        return wildcardElement.getUpperBounds().stream().anyMatch(bound -> reaches(bound, visited))
            || wildcardElement.getLowerBounds().stream().anyMatch(bound -> reaches(bound, visited));
    }

    /**
     * An unresolved type variable is described by its first bound, as SerdeSourceGenArgumentTypes.normalize
     * does, so the bound is what the property can hold: {@code Iterable<T extends Self>} used raw holds Self.
     * A type argument resolved from a type variable, such as the bound of {@code ? extends List<Self>}, also
     * reports isTypeVariable() and is followed as a regular type.
     */
    private boolean boundReaches(GenericPlaceholderElement placeholderElement, @Nullable String declaredVariable, Set<String> visited) {
        List<? extends ClassElement> bounds = placeholderElement.getBounds();
        if (bounds.isEmpty()) {
            return false;
        }
        ClassElement bound = bounds.get(0);
        // a bound such as T extends Comparable<T> refers to the variable again, so a variable is followed once. Two
        // types commonly name their variables the same, and their bounds can share an erasure, so a variable of a
        // type argument is identified with the type declaring it, and any other with its bound
        if (!visited.add(declaredVariable != null ? declaredVariable : "<" + placeholderElement.getVariableName() + " extends " + bound.getName() + ">")) {
            return false;
        }
        return reaches(bound, visited);
    }

    private boolean reaches(ClassElement type, Set<String> visited) {
        if (type instanceof WildcardElement wildcardElement) {
            return boundsReach(wildcardElement, visited);
        }
        if (type.isPrimitive()) {
            return false;
        }
        if (type instanceof GenericPlaceholderElement placeholderElement) {
            return boundReaches(placeholderElement, null, visited);
        }
        if (type.isArray()) {
            return reaches(type.fromArray(), visited);
        }
        String name = type.getName();
        if (name.equals(owner.getName())) {
            return true;
        }
        if (typeArgumentsReach(type, name, visited)) {
            return true;
        }
        if (type.isEnum() || isPlatformType(name) || notRecursive.contains(name) || !visited.add(name)) {
            return false;
        }
        List<PropertyElement> properties;
        try {
            properties = type.getBeanProperties();
        } catch (RuntimeException e) {
            // The properties of a type that isn't itself introspected, such as a precompiled
            // Jackson model with non-public @JsonSetter methods, may not be resolvable with the
            // default introspection rules. Its serde is resolved at runtime, so assume it can
            // reach the owner and resolve the property serde lazily.
            return true;
        }
        for (PropertyElement property : properties) {
            if (reaches(property.getType(), visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean typeArgumentsReach(ClassElement type, String name, Set<String> visited) {
        for (Map.Entry<String, ClassElement> typeArgument : type.getTypeArguments().entrySet()) {
            // an unresolved variable of a raw type is identified with the type and the variable it declares
            boolean reached = typeArgument.getValue() instanceof GenericPlaceholderElement placeholderElement
                ? boundReaches(placeholderElement, "<" + name + "." + typeArgument.getKey() + ">", visited)
                : reaches(typeArgument.getValue(), visited);
            if (reached) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlatformType(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("kotlin.");
    }
}
