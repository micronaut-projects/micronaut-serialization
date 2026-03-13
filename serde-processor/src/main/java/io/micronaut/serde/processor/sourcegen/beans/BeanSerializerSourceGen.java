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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeErrorHandler;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BeanSerializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);

    private static final Method SERIALIZE_METHOD = ReflectionUtils.getRequiredMethod(
        Serializer.class,
        "serialize",
        Encoder.class,
        Serializer.EncoderContext.class,
        Argument.class,
        Object.class
    );
    private static final Method FIND_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(Serializer.EncoderContext.class, "findSerializer", Argument.class);
    private static final Method CREATE_SPECIFIC_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        Serializer.class,
        "createSpecific",
        Serializer.EncoderContext.class,
        Argument.class
    );
    private static final Method ENCODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeObject", Argument.class);
    private static final Method ENCODE_KEY_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeKey", String.class);
    private static final Method ENCODE_STRING_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeString", String.class);
    private static final Method ENCODE_BOOLEAN_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeBoolean", boolean.class);
    private static final Method ENCODE_BYTE_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeByte", byte.class);
    private static final Method ENCODE_SHORT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeShort", short.class);
    private static final Method ENCODE_CHAR_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeChar", char.class);
    private static final Method ENCODE_INT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeInt", int.class);
    private static final Method ENCODE_LONG_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeLong", long.class);
    private static final Method ENCODE_FLOAT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeFloat", float.class);
    private static final Method ENCODE_DOUBLE_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeDouble", double.class);
    private static final Method ENCODE_BIG_INTEGER_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeBigInteger", java.math.BigInteger.class);
    private static final Method ENCODE_BIG_DECIMAL_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeBigDecimal", java.math.BigDecimal.class);
    private static final Method ENCODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeNull");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "finishStructure");
    private static final Method WITH_PROPERTY_PATH_THROWABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "withPropertyPath",
        Throwable.class,
        Argument.class,
        String.class,
        Argument.class
    );

    public ClassDef generate(ClassElement element, BeanSerdeShape beanSerdeShape) {
        TypeDef beanTypeDef = TypeDef.of(element);
        Map<String, String> keyFieldNames = new LinkedHashMap<>();
        Map<String, String> argumentFieldNames = new LinkedHashMap<>();
        List<FieldDef> fields = new ArrayList<>();

        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            String keyFieldName = constantFieldName("KEY", property.name(), index);
            String argumentFieldName = constantFieldName("ARGUMENT", property.name(), index);
            keyFieldNames.put(property.name(), keyFieldName);
            argumentFieldNames.put(property.name(), argumentFieldName);

            fields.add(FieldDef.builder(keyFieldName, STRING_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(property.name()))
                .build());
            fields.add(FieldDef.builder(argumentFieldName, ARGUMENT_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(BeanSerdeSourceGenUtils.argumentExpression(property.serializationType()))
                .build());
            index++;
        }

        return ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(TypeDef.parameterized(Serializer.class, beanTypeDef))
            .addSuperinterface(TypeDef.parameterized(ObjectSerializer.class, beanTypeDef))
            .addFields(fields)
            .addMethod(generateCreateSpecificMethod(beanTypeDef))
            .addMethod(generateSerializeMethod(beanTypeDef, beanSerdeShape, keyFieldNames, argumentFieldNames))
            .addMethod(generateSerializeIntoMethod(beanTypeDef, beanSerdeShape, keyFieldNames, argumentFieldNames))
            .build();
    }

    private MethodDef generateCreateSpecificMethod(TypeDef beanTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Serializer.class, beanTypeDef))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.parameterized(Argument.class, TypeDef.wildcardSubtypeOf(beanTypeDef)))
            .build((aThis, methodParameters) -> aThis.returning());
    }

    private MethodDef generateSerializeMethod(TypeDef beanTypeDef,
                                              BeanSerdeShape beanSerdeShape,
                                              Map<String, String> keyFieldNames,
                                              Map<String, String> argumentFieldNames) {
        return MethodDef.builder("serialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter("value", beanTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);
                VariableDef.MethodParameter value = methodParameters.get(3);

                List<StatementDef> objectStatements = new ArrayList<>();
                StatementDef.DefineAndAssign objectEncoderDef = encoder.invoke(ENCODE_OBJECT_METHOD, type).newLocal("objectEncoder");
                objectStatements.add(objectEncoderDef);
                objectStatements.addAll(serializeIntoStatements(aThis, objectEncoderDef.variable(), context, type, value, beanSerdeShape, keyFieldNames, argumentFieldNames));
                objectStatements.add(objectEncoderDef.variable().invoke(FINISH_STRUCTURE_METHOD));

                return value.isNull().ifTrue(
                    encoder.invoke(ENCODE_NULL_METHOD),
                    StatementDef.multi(objectStatements)
                );
            });
    }

    private MethodDef generateSerializeIntoMethod(TypeDef beanTypeDef,
                                                  BeanSerdeShape beanSerdeShape,
                                                  Map<String, String> keyFieldNames,
                                                  Map<String, String> argumentFieldNames) {
        return MethodDef.builder("serializeInto")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter("value", beanTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> StatementDef.multi(
                serializeIntoStatements(aThis, methodParameters.get(0), methodParameters.get(1), methodParameters.get(2), methodParameters.get(3), beanSerdeShape, keyFieldNames, argumentFieldNames)
            ));
    }

    private List<StatementDef> serializeIntoStatements(VariableDef.This aThis,
                                                       VariableDef encoder,
                                                       VariableDef.MethodParameter context,
                                                       VariableDef.MethodParameter type,
                                                       VariableDef.MethodParameter value,
                                                       BeanSerdeShape beanSerdeShape,
                                                       Map<String, String> keyFieldNames,
                                                       Map<String, String> argumentFieldNames) {
        List<StatementDef> statements = new ArrayList<>();
        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            statements.add(encoder.invoke(ENCODE_KEY_METHOD, aThis.field(keyFieldNames.get(property.name()), STRING_TYPE)));
            statements.add(serializeProperty(aThis, encoder, context, type, value, property, index++, argumentFieldNames));
        }
        return statements;
    }

    private StatementDef serializeProperty(VariableDef.This aThis,
                                           VariableDef objectEncoder,
                                           VariableDef.MethodParameter context,
                                           VariableDef.MethodParameter type,
                                           VariableDef.MethodParameter value,
                                           BeanSerdeShape.BeanProperty property,
                                           int index,
                                           Map<String, String> argumentFieldNames) {
        ExpressionDef argumentExpression = aThis.field(argumentFieldNames.get(property.name()), ARGUMENT_TYPE);
        Method scalarMethod = scalarEncoderMethod(property.serializationType());
        ExpressionDef propertyValue = value.invoke(property.readMethod());
        if (scalarMethod != null) {
            StatementDef scalarStatement;
            if (property.serializationType().isPrimitive() && !property.serializationType().isArray()) {
                scalarStatement = objectEncoder.invoke(scalarMethod, propertyValue);
            } else {
                StatementDef.DefineAndAssign propertyValueDef = propertyValue.newLocal(BeanSerdeSourceGenUtils.localName("value", property.name(), index));
                scalarStatement = StatementDef.multi(
                    propertyValueDef,
                    propertyValueDef.variable().isNull().ifTrue(
                        objectEncoder.invoke(ENCODE_NULL_METHOD),
                        StatementDef.multi(objectEncoder.invoke(scalarMethod, propertyValueDef.variable()))
                    )
                );
            }
            return wrapWithPropertyPath(scalarStatement, type, property.name(), argumentExpression);
        }

        StatementDef.DefineAndAssign serializerLookupDef = context.invoke(FIND_SERIALIZER_METHOD, argumentExpression)
            .newLocal(BeanSerdeSourceGenUtils.localName("serializerLookup", property.name(), index));
        StatementDef.DefineAndAssign serializerDef = serializerLookupDef.variable().invoke(CREATE_SPECIFIC_SERIALIZER_METHOD, context, argumentExpression)
            .newLocal(BeanSerdeSourceGenUtils.localName("serializer", property.name(), index));
        VariableDef serializer = serializerDef.variable();

        StatementDef serializeStatement = StatementDef.multi(
            serializerLookupDef,
            serializerDef,
            serializer.invoke(
                SERIALIZE_METHOD,
                objectEncoder,
                context,
                argumentExpression,
                propertyValue.cast(TypeDef.OBJECT)
            )
        );
        if (property.serializationType().isPrimitive() && !property.serializationType().isArray()) {
            return wrapWithPropertyPath(serializeStatement, type, property.name(), argumentExpression);
        }
        StatementDef.DefineAndAssign propertyValueDef = propertyValue.newLocal(BeanSerdeSourceGenUtils.localName("value", property.name(), index));
        return wrapWithPropertyPath(StatementDef.multi(
            propertyValueDef,
            propertyValueDef.variable().isNull().ifTrue(
                objectEncoder.invoke(ENCODE_NULL_METHOD),
                StatementDef.multi(
                    serializerLookupDef,
                    serializerDef,
                    serializer.invoke(
                        SERIALIZE_METHOD,
                        objectEncoder,
                        context,
                        argumentExpression,
                        propertyValueDef.variable().cast(TypeDef.OBJECT)
                    )
                )
            )
        ), type, property.name(), argumentExpression);
    }

    private String constantFieldName(String prefix, String propertyName, int index) {
        return prefix + "_" + propertyName.replaceAll("[^A-Za-z0-9]", "_").toUpperCase() + "_" + index;
    }

    private Method scalarEncoderMethod(ClassElement type) {
        return switch (type.getName()) {
            case "boolean", "java.lang.Boolean" -> type.isArray() ? null : ENCODE_BOOLEAN_METHOD;
            case "byte", "java.lang.Byte" -> type.isArray() ? null : ENCODE_BYTE_METHOD;
            case "short", "java.lang.Short" -> type.isArray() ? null : ENCODE_SHORT_METHOD;
            case "char", "java.lang.Character" -> type.isArray() ? null : ENCODE_CHAR_METHOD;
            case "int", "java.lang.Integer" -> type.isArray() ? null : ENCODE_INT_METHOD;
            case "long", "java.lang.Long" -> type.isArray() ? null : ENCODE_LONG_METHOD;
            case "float", "java.lang.Float" -> type.isArray() ? null : ENCODE_FLOAT_METHOD;
            case "double", "java.lang.Double" -> type.isArray() ? null : ENCODE_DOUBLE_METHOD;
            case "java.lang.String" -> type.isArray() ? null : ENCODE_STRING_METHOD;
            case "java.math.BigInteger" -> type.isArray() ? null : ENCODE_BIG_INTEGER_METHOD;
            case "java.math.BigDecimal" -> type.isArray() ? null : ENCODE_BIG_DECIMAL_METHOD;
            default -> null;
        };
    }

    private StatementDef wrapWithPropertyPath(StatementDef statement,
                                              VariableDef.MethodParameter type,
                                              String propertyName,
                                              ExpressionDef argumentExpression) {
        return StatementDef.doTry(statement)
            .doCatch(ClassTypeDef.of(Throwable.class), exceptionVariable ->
                ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                    .invokeStatic(
                        WITH_PROPERTY_PATH_THROWABLE_METHOD,
                        exceptionVariable,
                        type,
                        ExpressionDef.constant(propertyName),
                        argumentExpression
                    )
                    .doThrow()
            );
    }
}
