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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeErrorHandler;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import javax.annotation.processing.Generated;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.inject.Singleton;

/**
 * Generates optimized source deserializers for bean types.
 */
public final class BeanDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final String VALUE_LOCAL_PREFIX = "value";

    private static final Method FIND_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(Deserializer.DecoderContext.class, "findDeserializer", Argument.class);
    private static final Method CREATE_SPECIFIC_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        Deserializer.class,
        "createSpecific",
        Deserializer.DecoderContext.class,
        Argument.class
    );
    private static final Method DESERIALIZE_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(
        Deserializer.class,
        "deserializeNullable",
        Decoder.class,
        Deserializer.DecoderContext.class,
        Argument.class
    );
    private static final Method DECODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeObject", Argument.class);
    private static final Method DECODE_KEY_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeKey");
    private static final Method DECODE_STRING_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeStringNullable");
    private static final Method DECODE_BOOLEAN_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBooleanNullable");
    private static final Method DECODE_BYTE_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeByteNullable");
    private static final Method DECODE_SHORT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeShortNullable");
    private static final Method DECODE_CHAR_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeCharNullable");
    private static final Method DECODE_INT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeIntNullable");
    private static final Method DECODE_LONG_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeLongNullable");
    private static final Method DECODE_FLOAT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeFloatNullable");
    private static final Method DECODE_DOUBLE_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeDoubleNullable");
    private static final Method DECODE_BIG_INTEGER_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBigIntegerNullable");
    private static final Method DECODE_BIG_DECIMAL_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBigDecimalNullable");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "finishStructure");
    private static final Method DUPLICATE_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "duplicateProperty",
        String.class,
        Argument.class
    );
    private static final Method HANDLE_UNKNOWN_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "handleUnknownProperty",
        Decoder.class,
        Deserializer.DecoderContext.class,
        String.class,
        Argument.class
    );
    private static final Method WITH_PROPERTY_PATH_THROWABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "withPropertyPath",
        Throwable.class,
        Argument.class,
        String.class,
        Argument.class
    );
    private static final Method SET_ADD_METHOD = ReflectionUtils.getRequiredMethod(Set.class, "add", Object.class);

    public ClassDef generate(ClassElement element, BeanSerdeShape beanSerdeShape) {
        TypeDef beanTypeDef = TypeDef.of(element);
        ClassTypeDef deserializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
        Map<String, String> keyFieldNames = new LinkedHashMap<>();
        Map<String, String> argumentFieldNames = new LinkedHashMap<>();
        Map<String, String> deserializerFieldNames = new LinkedHashMap<>();
        List<FieldDef> fields = new ArrayList<>();

        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            String keyFieldName = constantFieldName("KEY", property.name(), index);
            String argumentFieldName = constantFieldName("ARGUMENT", property.name(), index);
            keyFieldNames.put(property.name(), keyFieldName);
            argumentFieldNames.put(property.name(), argumentFieldName);

            ClassElement lookupType = resolveLookupType(property.deserializationType());
            fields.add(FieldDef.builder(keyFieldName, STRING_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(property.name()))
                .build());
            fields.add(FieldDef.builder(argumentFieldName, ARGUMENT_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(BeanSerdeSourceGenUtils.argumentExpression(lookupType))
                .build());
            if (scalarDecoderMethod(property.deserializationType()) == null) {
                String deserializerFieldName = constantFieldName("DESERIALIZER", property.name(), index);
                deserializerFieldNames.put(property.name(), deserializerFieldName);
                fields.add(FieldDef.builder(deserializerFieldName, DESERIALIZER_TYPE)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            }
            index++;
        }

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Singleton.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, beanTypeDef))
            .addFields(fields)
            .addMethod(generateNoArgsConstructor(deserializerFieldNames))
            .addMethod(generateCreateSpecificMethod(beanTypeDef, deserializerClassTypeDef, argumentFieldNames, deserializerFieldNames))
            .addMethod(generateDeserializeMethod(element, beanTypeDef, deserializerClassTypeDef, beanSerdeShape, keyFieldNames, argumentFieldNames, deserializerFieldNames));

        if (!deserializerFieldNames.isEmpty()) {
            classDefBuilder.addMethod(generateSpecializedConstructor(deserializerFieldNames));
        }
        return classDefBuilder.build();
    }

    private MethodDef generateNoArgsConstructor(Map<String, String> deserializerFieldNames) {
        return MethodDef.constructor()
            .addModifiers(Modifier.PUBLIC)
            .build((aThis, methodParameters) -> {
                List<StatementDef> statements = new ArrayList<>();
                for (String deserializerFieldName : deserializerFieldNames.values()) {
                    statements.add(aThis.field(deserializerFieldName, DESERIALIZER_TYPE).put(ExpressionDef.nullValue().cast(DESERIALIZER_TYPE)));
                }
                return StatementDef.multi(statements);
            });
    }

    private MethodDef generateSpecializedConstructor(Map<String, String> deserializerFieldNames) {
        MethodDef.MethodDefBuilder constructorBuilder = MethodDef.constructor()
            .addModifiers(Modifier.PRIVATE);
        for (String deserializerFieldName : deserializerFieldNames.values()) {
            constructorBuilder.addParameter(deserializerFieldName, DESERIALIZER_TYPE);
        }
        return constructorBuilder.build((aThis, methodParameters) -> {
            List<StatementDef> statements = new ArrayList<>();
            int index = 0;
            for (String deserializerFieldName : deserializerFieldNames.values()) {
                statements.add(aThis.field(deserializerFieldName, DESERIALIZER_TYPE).put(methodParameters.get(index++)));
            }
            return StatementDef.multi(statements);
        });
    }

    private MethodDef generateCreateSpecificMethod(TypeDef beanTypeDef,
                                                   ClassTypeDef deserializerClassTypeDef,
                                                   Map<String, String> argumentFieldNames,
                                                   Map<String, String> deserializerFieldNames) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Deserializer.class, beanTypeDef))
            .addParameter("context", TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> {
                if (deserializerFieldNames.isEmpty()) {
                    return aThis.returning();
                }
                VariableDef.MethodParameter context = methodParameters.get(0);
                List<StatementDef> statements = new ArrayList<>();
                List<ExpressionDef> deserializerValues = new ArrayList<>();
                List<TypeDef> constructorParameterTypes = new ArrayList<>();
                int index = 0;
                for (Map.Entry<String, String> deserializerFieldEntry : deserializerFieldNames.entrySet()) {
                    String propertyName = deserializerFieldEntry.getKey();
                    String argumentFieldName = argumentFieldNames.get(propertyName);
                    ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(argumentFieldName, ARGUMENT_TYPE);
                    StatementDef.DefineAndAssign deserializerDef = context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression)
                        .newLocal(BeanSerdeSourceGenUtils.localName("deserializer", propertyName, index));
                    statements.add(deserializerDef);
                    deserializerValues.add(deserializerDef.variable());
                    constructorParameterTypes.add(DESERIALIZER_TYPE);
                    index++;
                }
                statements.add(deserializerClassTypeDef.instantiate(constructorParameterTypes, deserializerValues).returning());
                return StatementDef.multi(statements);
            });
    }

    private MethodDef generateDeserializeMethod(ClassElement element,
                                                TypeDef beanTypeDef,
                                                ClassTypeDef deserializerClassTypeDef,
                                                BeanSerdeShape beanSerdeShape,
                                                Map<String, String> keyFieldNames,
                                                Map<String, String> argumentFieldNames,
                                                Map<String, String> deserializerFieldNames) {
        return MethodDef.builder("deserialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(beanTypeDef)
            .addParameter("decoder", TypeDef.of(Decoder.class))
            .addParameter("context", TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter decoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);

                List<StatementDef> statements = new ArrayList<>();
                StatementDef.DefineAndAssign objectDecoderDef = decoder.invoke(DECODE_OBJECT_METHOD, type).newLocal("objectDecoder");
                statements.add(objectDecoderDef);
                VariableDef objectDecoder = objectDecoderDef.variable();

                StatementDef.DefineAndAssign beanDef = ClassTypeDef.of(element).instantiate().newLocal("bean");
                statements.add(beanDef);
                VariableDef beanVariable = beanDef.variable();

                StatementDef.DefineAndAssign seenPropertiesDef = ClassTypeDef.of(HashSet.class).instantiate().newLocal("seenProperties");
                statements.add(seenPropertiesDef);
                VariableDef seenProperties = seenPropertiesDef.variable();

                List<BeanSerdeShape.BeanProperty> properties = beanSerdeShape.properties();
                int index = 0;
                for (BeanSerdeShape.BeanProperty property : properties) {
                    if (!property.deserializationType().isOptional()) {
                        continue;
                    }
                    statements.add(beanVariable.invoke(property.writeMethod(), BeanSerdeSourceGenUtils.optionalDefaultValueExpression(property.deserializationType())));
                    index++;
                }

                List<StatementDef> propertyDeserializers = new ArrayList<>(properties.size());
                index = 0;
                for (BeanSerdeShape.BeanProperty property : properties) {
                    propertyDeserializers.add(deserializeAndAssignProperty(
                        aThis,
                        deserializerClassTypeDef,
                        objectDecoder,
                        context,
                        type,
                        beanVariable,
                        property,
                        index,
                        keyFieldNames,
                        argumentFieldNames,
                        deserializerFieldNames
                    ));
                    index++;
                }

                StatementDef.DefineAndAssign keyDef = objectDecoder.invoke(DECODE_KEY_METHOD).newLocal("key");
                statements.add(keyDef);
                VariableDef keyVariable = keyDef.variable();

                StatementDef switchStatement = ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                    .invokeStatic(HANDLE_UNKNOWN_PROPERTY_METHOD, objectDecoder, context, keyVariable, type);
                for (int i = properties.size() - 1; i >= 0; i--) {
                    BeanSerdeShape.BeanProperty property = properties.get(i);
                    ExpressionDef propertyNameExpression = deserializerClassTypeDef.getStaticField(keyFieldNames.get(property.name()), STRING_TYPE);
                    StatementDef duplicatePropertyStatement = ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                        .invokeStatic(DUPLICATE_PROPERTY_METHOD, propertyNameExpression, type)
                        .doThrow();
                    switchStatement = keyVariable.equalsStructurally(propertyNameExpression).ifTrue(
                        seenProperties.invoke(SET_ADD_METHOD, propertyNameExpression).ifTrue(
                            propertyDeserializers.get(i),
                            duplicatePropertyStatement
                        ),
                        switchStatement
                    );
                }
                if (properties.isEmpty()) {
                    statements.add(keyVariable.isNonNull().whileLoop(switchStatement));
                } else {
                    statements.add(keyVariable.isNonNull().whileLoop(
                        StatementDef.multi(
                            switchStatement,
                            keyVariable.assign(objectDecoder.invoke(DECODE_KEY_METHOD))
                        )
                    ));
                }
                statements.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                statements.add(beanVariable.returning());

                return StatementDef.multi(statements);
            });
    }

    @SuppressWarnings("java:S107")
    private StatementDef deserializeAndAssignProperty(VariableDef.This aThis,
                                                      ClassTypeDef deserializerClassTypeDef,
                                                      VariableDef objectDecoder,
                                                      VariableDef.MethodParameter context,
                                                      VariableDef.MethodParameter type,
                                                      VariableDef beanVariable,
                                                      BeanSerdeShape.BeanProperty property,
                                                      int index,
                                                      Map<String, String> keyFieldNames,
                                                      Map<String, String> argumentFieldNames,
                                                      Map<String, String> deserializerFieldNames) {
        ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(argumentFieldNames.get(property.name()), ARGUMENT_TYPE);
        ExpressionDef propertyNameExpression = deserializerClassTypeDef.getStaticField(keyFieldNames.get(property.name()), STRING_TYPE);
        Method scalarDecodeMethod = scalarDecoderMethod(property.deserializationType());
        StatementDef.DefineAndAssign deserializedValueDef;
        StatementDef deserializeAndAssign;
        if (scalarDecodeMethod != null) {
            deserializedValueDef = objectDecoder.invoke(scalarDecodeMethod)
                .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
                .newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, property.name(), index));
            deserializeAndAssign = deserializedValueDef;
        } else {
            String deserializerFieldName = deserializerFieldNames.get(property.name());
            StatementDef.DefineAndAssign deserializerDef = aThis.field(deserializerFieldName, DESERIALIZER_TYPE)
                .newLocal(BeanSerdeSourceGenUtils.localName("deserializer", property.name(), index));
            StatementDef initializeDeserializerStatement = deserializerDef.variable().isNull().ifTrue(
                deserializerDef.variable().assign(context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression).invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression))
            );
            VariableDef deserializerVariable = deserializerDef.variable();
            deserializedValueDef = deserializerVariable.invoke(
                DESERIALIZE_NULLABLE_METHOD,
                objectDecoder,
                context,
                argumentExpression
            ).cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType())).newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, property.name(), index));
            deserializeAndAssign = StatementDef.multi(
                deserializerDef,
                initializeDeserializerStatement,
                deserializedValueDef
            );
        }
        StatementDef assignStatement = beanVariable.invoke(property.writeMethod(), deserializedValueDef.variable());
        StatementDef propertyAssignment;
        if (property.deserializationType().isPrimitive() || property.nullable()) {
            if (property.deserializationType().isPrimitive() && !property.deserializationType().isArray()) {
                propertyAssignment = deserializedValueDef.variable().isNull().ifTrue(
                    beanVariable.invoke(property.writeMethod(), BeanSerdeSourceGenUtils.primitiveDefaultValueExpression(property.deserializationType())),
                    assignStatement
                );
            } else {
                propertyAssignment = assignStatement;
            }
        } else {
            propertyAssignment = deserializedValueDef.variable().isNull().ifTrue(
                StatementDef.multi(),
                assignStatement
            );
        }
        deserializeAndAssign = StatementDef.multi(
            deserializeAndAssign,
            propertyAssignment
        );
        return StatementDef.doTry(deserializeAndAssign)
            .doCatch(ClassTypeDef.of(Throwable.class), exceptionVariable ->
                ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                    .invokeStatic(
                        WITH_PROPERTY_PATH_THROWABLE_METHOD,
                        exceptionVariable,
                        type,
                        propertyNameExpression,
                        argumentExpression
                    )
                    .doThrow()
            );
    }

    private String constantFieldName(String prefix, String propertyName, int index) {
        return prefix + "_" + propertyName.replaceAll("[^A-Za-z0-9]", "_").toUpperCase() + "_" + index;
    }

    private static ClassElement resolveLookupType(ClassElement type) {
        if ("java.lang.Iterable".equals(type.getName())) {
            ClassElement collectionType = ClassElement.of(Collection.class);
            Map<String, ClassElement> byName = type.getTypeArguments();
            if (!byName.isEmpty()) {
                return collectionType.withTypeArguments(new ArrayList<>(byName.values()));
            }
            List<? extends ClassElement> boundGenericTypes = type.getBoundGenericTypes();
            if (!boundGenericTypes.isEmpty()) {
                return collectionType.withTypeArguments(new ArrayList<>(boundGenericTypes));
            }
            return collectionType;
        }
        return type;
    }

    private Method scalarDecoderMethod(ClassElement type) {
        if (type.isArray()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean", "java.lang.Boolean" -> DECODE_BOOLEAN_NULLABLE_METHOD;
            case "byte", "java.lang.Byte" -> DECODE_BYTE_NULLABLE_METHOD;
            case "short", "java.lang.Short" -> DECODE_SHORT_NULLABLE_METHOD;
            case "char", "java.lang.Character" -> DECODE_CHAR_NULLABLE_METHOD;
            case "int", "java.lang.Integer" -> DECODE_INT_NULLABLE_METHOD;
            case "long", "java.lang.Long" -> DECODE_LONG_NULLABLE_METHOD;
            case "float", "java.lang.Float" -> DECODE_FLOAT_NULLABLE_METHOD;
            case "double", "java.lang.Double" -> DECODE_DOUBLE_NULLABLE_METHOD;
            case "java.lang.String" -> DECODE_STRING_NULLABLE_METHOD;
            case "java.math.BigInteger" -> DECODE_BIG_INTEGER_NULLABLE_METHOD;
            case "java.math.BigDecimal" -> DECODE_BIG_DECIMAL_NULLABLE_METHOD;
            default -> null;
        };
    }
}
