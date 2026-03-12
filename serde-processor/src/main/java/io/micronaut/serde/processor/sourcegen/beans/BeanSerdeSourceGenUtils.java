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
package io.micronaut.serde.processor.sourcegen.beans;

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class BeanSerdeSourceGenUtils {

    static final Method ARGUMENT_OF_METHOD = ReflectionUtils.getRequiredMethod(Argument.class, "of", Class.class);
    static final Method ARGUMENT_OF_WITH_TYPE_PARAMETERS_METHOD = ReflectionUtils.getRequiredMethod(Argument.class, "of", Class.class, Argument[].class);
    static final Method OPTIONAL_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(Optional.class, "empty");
    static final Method OPTIONAL_INT_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(OptionalInt.class, "empty");
    static final Method OPTIONAL_DOUBLE_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(OptionalDouble.class, "empty");
    static final Method OPTIONAL_LONG_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(OptionalLong.class, "empty");

    private BeanSerdeSourceGenUtils() {
    }

    static ExpressionDef argumentExpression(ClassElement classElement) {
        ClassElement argumentType = normalizeArgumentType(classElement);
        List<? extends ClassElement> typeArguments = resolveTypeArguments(argumentType);
        if (!typeArguments.isEmpty()) {
            List<ExpressionDef> typeArgumentExpressions = typeArguments.stream()
                .map(BeanSerdeSourceGenUtils::argumentExpression)
                .toList();
            ExpressionDef typeArgumentArray = TypeDef.array(TypeDef.of(Argument.class)).instantiate(typeArgumentExpressions);
            return ClassTypeDef.of(Argument.class)
                .invokeStatic(
                    ARGUMENT_OF_WITH_TYPE_PARAMETERS_METHOD,
                    ExpressionDef.constant(TypeDef.erasure(argumentType)),
                    typeArgumentArray
                );
        }
        return ClassTypeDef.of(Argument.class)
            .invokeStatic(ARGUMENT_OF_METHOD, ExpressionDef.constant(TypeDef.erasure(argumentType)));
    }

    static ExpressionDef optionalDefaultValueExpression(ClassElement classElement) {
        return switch (classElement.getName()) {
            case "java.util.Optional" -> ClassTypeDef.of(Optional.class).invokeStatic(OPTIONAL_EMPTY_METHOD);
            case "java.util.OptionalInt" -> ClassTypeDef.of(OptionalInt.class).invokeStatic(OPTIONAL_INT_EMPTY_METHOD);
            case "java.util.OptionalDouble" -> ClassTypeDef.of(OptionalDouble.class).invokeStatic(OPTIONAL_DOUBLE_EMPTY_METHOD);
            case "java.util.OptionalLong" -> ClassTypeDef.of(OptionalLong.class).invokeStatic(OPTIONAL_LONG_EMPTY_METHOD);
            default -> ExpressionDef.nullValue();
        };
    }

    static ExpressionDef primitiveDefaultValueExpression(ClassElement classElement) {
        if (!classElement.isPrimitive() || classElement.isArray()) {
            return ExpressionDef.nullValue().cast(TypeDef.erasure(classElement));
        }
        return switch (classElement.getName()) {
            case "boolean" -> ExpressionDef.falseValue();
            case "long" -> ExpressionDef.constant(0L);
            case "float" -> ExpressionDef.constant(0f);
            case "double" -> ExpressionDef.constant(0d);
            case "char" -> ExpressionDef.constant(0).cast(TypeDef.erasure(classElement));
            case "byte", "short", "int" -> ExpressionDef.constant(0).cast(TypeDef.erasure(classElement));
            default -> ExpressionDef.constant(0).cast(TypeDef.erasure(classElement));
        };
    }

    private static List<? extends ClassElement> resolveTypeArguments(ClassElement classElement) {
        Map<String, ClassElement> byName = classElement.getTypeArguments();
        if (!byName.isEmpty()) {
            return new ArrayList<>(byName.values());
        }
        return classElement.getBoundGenericTypes();
    }

    private static ClassElement normalizeArgumentType(ClassElement classElement) {
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
            List<? extends ClassElement> iterableTypeArguments = resolveTypeArguments(classElement);
            if (!iterableTypeArguments.isEmpty()) {
                collectionType = collectionType.withTypeArguments(new ArrayList<>(iterableTypeArguments));
            }
            return collectionType;
        }
        if (classElement instanceof WildcardElement wildcardElement) {
            if (!wildcardElement.getUpperBounds().isEmpty()) {
                return normalizeArgumentType(wildcardElement.getUpperBounds().get(0));
            }
            if (!wildcardElement.getLowerBounds().isEmpty()) {
                return normalizeArgumentType(wildcardElement.getLowerBounds().get(0));
            }
            return ClassElement.of(Object.class);
        }
        if (classElement.isTypeVariable()) {
            List<? extends ClassElement> bounds = classElement.getBoundGenericTypes();
            if (!bounds.isEmpty()) {
                return normalizeArgumentType(bounds.get(0));
            }
            return ClassElement.of(Object.class);
        }
        return classElement;
    }

    static TypeDef deserializedCastType(ClassElement classElement) {
        if (!classElement.isPrimitive() || classElement.isArray()) {
            return TypeDef.erasure(classElement);
        }
        return switch (classElement.getName()) {
            case "boolean" -> TypeDef.of(Boolean.class);
            case "byte" -> TypeDef.of(Byte.class);
            case "short" -> TypeDef.of(Short.class);
            case "char" -> TypeDef.of(Character.class);
            case "int" -> TypeDef.of(Integer.class);
            case "long" -> TypeDef.of(Long.class);
            case "float" -> TypeDef.of(Float.class);
            case "double" -> TypeDef.of(Double.class);
            default -> TypeDef.erasure(classElement);
        };
    }

    static String localName(String prefix, String componentName, int index) {
        return prefix + componentName.replace("$", "_") + index;
    }
}
