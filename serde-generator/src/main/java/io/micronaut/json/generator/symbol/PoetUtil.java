/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.generator.symbol;

import com.squareup.javapoet.*;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.WildcardElement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Internal
public final class PoetUtil {
    private PoetUtil() {
    }

    public static TypeName toTypeName(GeneratorType clazz) {
        return clazz.toPoetName();
    }

    public static TypeName toTypeName(ClassElement clazz) {
        TypeName className;
        if (clazz.isGenericPlaceholder()) {
            className = TypeVariableName.get(((GenericPlaceholderElement) clazz).getVariableName());
        } else if (clazz.isWildcard()) {
            List<TypeName> lower = ((WildcardElement) clazz).getLowerBounds().stream().map(PoetUtil::toTypeName).collect(Collectors.toList());
            List<TypeName> upper = ((WildcardElement) clazz).getUpperBounds().stream().map(PoetUtil::toTypeName).collect(Collectors.toList());
            if (!lower.isEmpty()) {
                if (lower.size() != 1) {
                    throw new UnsupportedOperationException("Cannot emit lower wildcard bound with multiple types");
                }
                if (upper.size() != 1 || !upper.get(0).equals(ClassName.OBJECT)) {
                    throw new UnsupportedOperationException("Cannot emit lower and upper wildcard bound at the same time");
                }
                className = WildcardTypeName.supertypeOf(lower.get(0));
            } else {
                if (upper.size() != 1) {
                    throw new UnsupportedOperationException("Cannot emit upper wildcard bound with multiple types");
                }
                className = WildcardTypeName.subtypeOf(upper.get(0));
            }
        } else if (clazz.isPrimitive()) {
            if (clazz.equals(PrimitiveElement.BYTE)) {
                className = TypeName.BYTE;
            } else if (clazz.equals(PrimitiveElement.SHORT)) {
                className = TypeName.SHORT;
            } else if (clazz.equals(PrimitiveElement.CHAR)) {
                className = TypeName.CHAR;
            } else if (clazz.equals(PrimitiveElement.INT)) {
                className = TypeName.INT;
            } else if (clazz.equals(PrimitiveElement.LONG)) {
                className = TypeName.LONG;
            } else if (clazz.equals(PrimitiveElement.FLOAT)) {
                className = TypeName.FLOAT;
            } else if (clazz.equals(PrimitiveElement.DOUBLE)) {
                className = TypeName.DOUBLE;
            } else if (clazz.equals(PrimitiveElement.BOOLEAN)) {
                className = TypeName.BOOLEAN;
            } else if (clazz.equals(PrimitiveElement.VOID)) {
                className = TypeName.VOID;
            } else {
                throw new AssertionError("unknown primitive type " + clazz);
            }
        } else {
            // split for nested classes
            String[] simpleNameParts = clazz.getSimpleName().split("\\$");
            className = ClassName.get(clazz.getPackageName(), simpleNameParts[0], Arrays.copyOfRange(simpleNameParts, 1, simpleNameParts.length));
            if (clazz.getName().equals("<any>")) {
                throw new IllegalArgumentException("Type resolution error?");
            }
            List<? extends ClassElement> typeArguments = clazz.getBoundGenericTypes();
            if (!typeArguments.isEmpty() && !clazz.isArray() && !clazz.isPrimitive()) {
                className = ParameterizedTypeName.get(
                        (ClassName) className,
                        typeArguments.stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new));
            }
        }
        for (int i = 0; i < clazz.getArrayDimensions(); i++) {
            className = ArrayTypeName.of(className);
        }
        return className;
    }

    static TypeName toTypeNameRaw(ClassElement clazz) {
        if (clazz.isArray()) {
            return ArrayTypeName.of(toTypeNameRaw(clazz.fromArray()));
        }
        if (clazz.isPrimitive()) {
            if (clazz.equals(PrimitiveElement.BYTE)) {
                return TypeName.BYTE;
            } else if (clazz.equals(PrimitiveElement.SHORT)) {
                return TypeName.SHORT;
            } else if (clazz.equals(PrimitiveElement.CHAR)) {
                return TypeName.CHAR;
            } else if (clazz.equals(PrimitiveElement.INT)) {
                return TypeName.INT;
            } else if (clazz.equals(PrimitiveElement.LONG)) {
                return TypeName.LONG;
            } else if (clazz.equals(PrimitiveElement.FLOAT)) {
                return TypeName.FLOAT;
            } else if (clazz.equals(PrimitiveElement.DOUBLE)) {
                return TypeName.DOUBLE;
            } else if (clazz.equals(PrimitiveElement.BOOLEAN)) {
                return TypeName.BOOLEAN;
            } else if (clazz.equals(PrimitiveElement.VOID)) {
                return TypeName.VOID;
            } else {
                throw new AssertionError("unknown primitive type " + clazz);
            }
        }
        // split for nested classes
        String[] simpleNameParts = clazz.getSimpleName().split("\\$");
        ClassName className = ClassName.get(clazz.getPackageName(), simpleNameParts[0], Arrays.copyOfRange(simpleNameParts, 1, simpleNameParts.length));
        if (clazz.getName().equals("<any>")) {
            throw new IllegalArgumentException("Type resolution error?");
        }
        return className;
    }

    static String toStringRelative(TypeName typeName, String pkg) {
        if (typeName instanceof ArrayTypeName) {
            return toStringRelative(((ArrayTypeName) typeName).componentType, pkg) + "[]";
        } else if (typeName instanceof ParameterizedTypeName) {
            // we ignore the enclosed type
            return toStringRelative(((ParameterizedTypeName) typeName).rawType, pkg) +
                    ((ParameterizedTypeName) typeName).typeArguments.stream().map(t -> toStringRelative(t, pkg)).collect(Collectors.joining(", ", "<", ">"));
        } else if (typeName instanceof WildcardTypeName) {
            List<TypeName> lowerBounds = ((WildcardTypeName) typeName).lowerBounds;
            if (!lowerBounds.isEmpty()) {
                return "? super " + toStringRelative(lowerBounds.get(0), pkg);
            } else {
                List<TypeName> upperBounds = ((WildcardTypeName) typeName).upperBounds;
                if (upperBounds.get(0).equals(TypeName.OBJECT)) {
                    return "?";
                } else {
                    return "? extends " + toStringRelative(upperBounds.get(0), pkg);
                }
            }
        } else if (typeName instanceof ClassName) {
            if (((ClassName) typeName).packageName().equals(pkg)) {
                return ((ClassName) typeName).canonicalName().substring(pkg.length() + 1);
            } else {
                return ((ClassName) typeName).canonicalName();
            }
        } else {
            return typeName.toString();
        }
    }
}
