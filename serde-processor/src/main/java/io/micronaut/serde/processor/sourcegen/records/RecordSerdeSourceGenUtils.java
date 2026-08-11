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
package io.micronaut.serde.processor.sourcegen.records;

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class RecordSerdeSourceGenUtils {
    private static final String EMPTY_METHOD = "empty";
    private static final String BOOLEAN_TYPE = "boolean";
    private static final String FLOAT_TYPE = "float";
    private static final String DOUBLE_TYPE = "double";
    private static final String SHORT_TYPE = "short";

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final ClassTypeDef SERDE_ARGUMENT_CONSTANTS = ClassTypeDef.of("io.micronaut.serde.util.SerdeArgumentConstants");
    private static final ClassTypeDef SERDE_CONFIG_TYPE = ClassTypeDef.of(SerdeConfig.class);

    private static final Method ARGUMENT_OF_METHOD = ReflectionUtils.getRequiredMethod(Argument.class, "of", Class.class);
    private static final Method ARGUMENT_OF_WITH_TYPE_PARAMETERS_METHOD = ReflectionUtils.getRequiredMethod(Argument.class, "of", Class.class, Argument[].class);
    private static final Method ARGUMENT_WITH_NAME_METHOD = ReflectionUtils.getRequiredMethod(Argument.class, "withName", String.class);
    private static final Method OPTIONAL_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(Optional.class, EMPTY_METHOD);
    private static final Method OPTIONAL_INT_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(OptionalInt.class, EMPTY_METHOD);
    private static final Method OPTIONAL_DOUBLE_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(OptionalDouble.class, EMPTY_METHOD);
    private static final Method OPTIONAL_LONG_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(OptionalLong.class, EMPTY_METHOD);
    private static final Field META_ANNOTATION_PROPERTY_FIELD = ReflectionUtils.getRequiredField(SerdeConfig.class, "META_ANNOTATION_PROPERTY");
    private static final Field WRAPPER_PROPERTY_FIELD = ReflectionUtils.getRequiredField(SerdeConfig.class, "WRAPPER_PROPERTY");
    private static final Field XML_ATTRIBUTE_PROPERTY_FIELD = ReflectionUtils.getRequiredField(SerdeConfig.class, "XML_ATTRIBUTE_PROPERTY");
    private static final Field XML_NAMESPACE_FIELD = ReflectionUtils.getRequiredField(SerdeConfig.class, "XML_NAMESPACE");

    private RecordSerdeSourceGenUtils() {
    }

    static ExpressionDef argumentExpression(ClassElement classElement) {
        ClassElement argumentType = normalizeArgumentType(classElement);
        List<? extends ClassElement> typeArguments = resolveTypeArguments(argumentType);
        if (!typeArguments.isEmpty()) {
            List<ExpressionDef> typeArgumentExpressions = typeArguments.stream()
                .map(RecordSerdeSourceGenUtils::argumentExpression)
                .toList();
            ExpressionDef typeArgumentArray = TypeDef.array(TypeDef.of(Argument.class)).instantiate(typeArgumentExpressions);
            return ClassTypeDef.of(Argument.class)
                .invokeStatic(
                    ARGUMENT_OF_WITH_TYPE_PARAMETERS_METHOD,
                    ExpressionDef.constant(TypeDef.erasure(argumentType)),
                    typeArgumentArray
                );
        }
        ExpressionDef constantArgumentExpression = simpleArgumentConstantExpression(argumentType);
        if (constantArgumentExpression != null) {
            return constantArgumentExpression;
        }
        return ClassTypeDef.of(Argument.class)
            .invokeStatic(ARGUMENT_OF_METHOD, ExpressionDef.constant(TypeDef.erasure(argumentType)));
    }

    static ExpressionDef argumentExpression(ClassElement classElement, ExpressionDef name) {
        return argumentExpression(classElement)
            .invoke(ARGUMENT_WITH_NAME_METHOD, name);
    }

    private static @Nullable ExpressionDef simpleArgumentConstantExpression(ClassElement argumentType) {
        if (argumentType.isArray()) {
            return null;
        }
        return switch (argumentType.getName()) {
            case "java.lang.String" -> ClassTypeDef.of(Argument.class).getStaticField("STRING", ARGUMENT_TYPE);
            case "java.lang.Boolean" -> ClassTypeDef.of(Argument.class).getStaticField("BOOLEAN", ARGUMENT_TYPE);
            case "java.lang.Byte" -> ClassTypeDef.of(Argument.class).getStaticField("BYTE", ARGUMENT_TYPE);
            case "java.lang.Short" -> ClassTypeDef.of(Argument.class).getStaticField("SHORT", ARGUMENT_TYPE);
            case "java.lang.Character" -> ClassTypeDef.of(Argument.class).getStaticField("CHAR", ARGUMENT_TYPE);
            case "java.lang.Integer" -> ClassTypeDef.of(Argument.class).getStaticField("INT", ARGUMENT_TYPE);
            case "java.lang.Long" -> ClassTypeDef.of(Argument.class).getStaticField("LONG", ARGUMENT_TYPE);
            case "java.lang.Float" -> ClassTypeDef.of(Argument.class).getStaticField("FLOAT", ARGUMENT_TYPE);
            case "java.lang.Double" -> ClassTypeDef.of(Argument.class).getStaticField("DOUBLE", ARGUMENT_TYPE);
            case "java.lang.Object" -> ClassTypeDef.of(Argument.class).getStaticField("OBJECT_ARGUMENT", ARGUMENT_TYPE);
            case "java.math.BigInteger" -> SERDE_ARGUMENT_CONSTANTS.getStaticField("BIG_INTEGER", ARGUMENT_TYPE);
            case "java.math.BigDecimal" -> SERDE_ARGUMENT_CONSTANTS.getStaticField("BIG_DECIMAL", ARGUMENT_TYPE);
            default -> null;
        };
    }

    static ExpressionDef defaultValueExpression(ClassElement classElement, boolean nonNullCollectionDefault) {
        switch (classElement.getName()) {
            case "java.util.Optional":
                return ClassTypeDef.of(Optional.class).invokeStatic(OPTIONAL_EMPTY_METHOD);
            case "java.util.OptionalInt":
                return ClassTypeDef.of(OptionalInt.class).invokeStatic(OPTIONAL_INT_EMPTY_METHOD);
            case "java.util.OptionalDouble":
                return ClassTypeDef.of(OptionalDouble.class).invokeStatic(OPTIONAL_DOUBLE_EMPTY_METHOD);
            case "java.util.OptionalLong":
                return ClassTypeDef.of(OptionalLong.class).invokeStatic(OPTIONAL_LONG_EMPTY_METHOD);
            default:
        }
        if (nonNullCollectionDefault) {
            if (classElement.isAssignable(List.class) || classElement.isAssignable(Collection.class)) {
                return ClassTypeDef.of(ArrayList.class).instantiate().cast(TypeDef.erasure(classElement));
            }
            if (classElement.isAssignable(java.util.Set.class)) {
                return ClassTypeDef.of(java.util.LinkedHashSet.class).instantiate().cast(TypeDef.erasure(classElement));
            }
            if (classElement.isAssignable(Map.class)) {
                return ClassTypeDef.of(java.util.LinkedHashMap.class).instantiate().cast(TypeDef.erasure(classElement));
            }
        }
        if (!classElement.isPrimitive() || classElement.isArray()) {
            return ExpressionDef.nullValue().cast(TypeDef.erasure(classElement));
        }
        String name = classElement.getName();
        return switch (name) {
            case BOOLEAN_TYPE -> ExpressionDef.falseValue();
            case "long" -> ExpressionDef.constant(0L);
            case FLOAT_TYPE -> ExpressionDef.constant(0f);
            case DOUBLE_TYPE -> ExpressionDef.constant(0d);
            case "char" -> ExpressionDef.constant(0).cast(TypeDef.erasure(classElement));
            case "byte", SHORT_TYPE, "int" -> ExpressionDef.constant(0).cast(TypeDef.erasure(classElement));
            default -> ExpressionDef.constant(0).cast(TypeDef.erasure(classElement));
        };
    }

    static TypeDef deserializedCastType(ClassElement classElement) {
        if (!classElement.isPrimitive() || classElement.isArray()) {
            return TypeDef.erasure(classElement);
        }
        return switch (classElement.getName()) {
            case BOOLEAN_TYPE -> TypeDef.of(Boolean.class);
            case "byte" -> TypeDef.of(Byte.class);
            case SHORT_TYPE -> TypeDef.of(Short.class);
            case "char" -> TypeDef.of(Character.class);
            case "int" -> TypeDef.of(Integer.class);
            case "long" -> TypeDef.of(Long.class);
            case FLOAT_TYPE -> TypeDef.of(Float.class);
            case DOUBLE_TYPE -> TypeDef.of(Double.class);
            default -> TypeDef.erasure(classElement);
        };
    }

    static String localName(String prefix, int index) {
        return prefix + index;
    }

    static ExpressionDef keyMetadataPropertyExpression(String property) {
        Field field = switch (property) {
            case SerdeConfig.META_ANNOTATION_PROPERTY -> META_ANNOTATION_PROPERTY_FIELD;
            case SerdeConfig.WRAPPER_PROPERTY -> WRAPPER_PROPERTY_FIELD;
            case SerdeConfig.XML_ATTRIBUTE_PROPERTY -> XML_ATTRIBUTE_PROPERTY_FIELD;
            case SerdeConfig.XML_NAMESPACE -> XML_NAMESPACE_FIELD;
            default -> throw new IllegalArgumentException("Unsupported key metadata property: " + property);
        };
        return SERDE_CONFIG_TYPE.getStaticField(field);
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
                case BOOLEAN_TYPE -> ClassElement.of(Boolean.class);
                case "byte" -> ClassElement.of(Byte.class);
                case SHORT_TYPE -> ClassElement.of(Short.class);
                case "char" -> ClassElement.of(Character.class);
                case "int" -> ClassElement.of(Integer.class);
                case "long" -> ClassElement.of(Long.class);
                case FLOAT_TYPE -> ClassElement.of(Float.class);
                case DOUBLE_TYPE -> ClassElement.of(Double.class);
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
}
