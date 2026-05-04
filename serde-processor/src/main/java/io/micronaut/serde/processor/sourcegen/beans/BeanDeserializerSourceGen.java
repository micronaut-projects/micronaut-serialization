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
import io.micronaut.serde.util.GeneratedSerdeExceptionUtil;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates optimized source deserializers for bean types.
 */
public final class BeanDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef NULLABLE_DESERIALIZER_TYPE = DESERIALIZER_TYPE.annotated(AnnotationDef.builder(Nullable.class).build());
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final int STRING_SWITCH_PROPERTY_THRESHOLD = 5;
    private static final String CONTEXT_PARAMETER = "context";
    private static final String VALUE_LOCAL_PREFIX = "value";
    private static final String GENERATED_VALUE_MEMBER = "value";

    private static final Method STRING_EQUALS_METHOD = ReflectionUtils.getRequiredMethod(String.class, "equals", Object.class);
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
    private static final Method DECODE_BOOLEAN_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBoolean");
    private static final Method DECODE_BOOLEAN_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBooleanNullable");
    private static final Method DECODE_BYTE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeByte");
    private static final Method DECODE_BYTE_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeByteNullable");
    private static final Method DECODE_SHORT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeShort");
    private static final Method DECODE_SHORT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeShortNullable");
    private static final Method DECODE_CHAR_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeChar");
    private static final Method DECODE_CHAR_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeCharNullable");
    private static final Method DECODE_INT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeInt");
    private static final Method DECODE_INT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeIntNullable");
    private static final Method DECODE_LONG_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeLong");
    private static final Method DECODE_LONG_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeLongNullable");
    private static final Method DECODE_FLOAT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeFloat");
    private static final Method DECODE_FLOAT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeFloatNullable");
    private static final Method DECODE_DOUBLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeDouble");
    private static final Method DECODE_DOUBLE_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeDoubleNullable");
    private static final Method DECODE_BIG_INTEGER_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBigIntegerNullable");
    private static final Method DECODE_BIG_DECIMAL_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeBigDecimalNullable");
    private static final Method DECODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeNull");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "finishStructure");
    private static final Method DUPLICATE_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "duplicateProperty",
        String.class,
        Argument.class
    );
    private static final Method HANDLE_UNKNOWN_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "handleUnknownProperty",
        Decoder.class,
        Deserializer.DecoderContext.class,
        String.class,
        Argument.class
    );
    private static final Method WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeFallbackUtil.class,
        "withRuntimeObjectFallback",
        Deserializer.class,
        Deserializer.DecoderContext.class,
        Argument.class
    );
    private static final Method WITH_PROPERTY_PATH_THROWABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "withPropertyPath",
        Throwable.class,
        Argument.class,
        String.class,
        Argument.class
    );

    private static String required(Map<String, String> names, String key) {
        return Objects.requireNonNull(names.get(key));
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

    public ClassDef generate(ClassElement element, BeanSerdeShape beanSerdeShape) {
        TypeDef beanTypeDef = TypeDef.of(element);
        ClassTypeDef deserializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
        Map<String, String> keyFieldNames = new LinkedHashMap<>();
        Map<String, String> argumentFieldNames = new LinkedHashMap<>();
        Map<String, String> deserializerFieldNames = new LinkedHashMap<>();
        List<FieldDef> fields = new ArrayList<>();

        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            String keyFieldName = indexedName("KEY", index);
            String argumentFieldName = indexedName("ARGUMENT", index);
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
                String deserializerFieldName = indexedName("DESERIALIZER", index);
                deserializerFieldNames.put(property.name(), deserializerFieldName);
                fields.add(FieldDef.builder(deserializerFieldName, DESERIALIZER_TYPE)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .addAnnotation(Nullable.class)
                    .build());
            }
            index++;
        }

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Singleton.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember(GENERATED_VALUE_MEMBER, "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, beanTypeDef))
            .addFields(fields)
            .addMethod(generateNoArgsConstructor(deserializerFieldNames))
            .addMethod(generateCreateSpecificMethod(beanTypeDef, deserializerClassTypeDef, argumentFieldNames, deserializerFieldNames))
            .addMethod(generateDeserializeMethod(element, beanTypeDef, deserializerClassTypeDef, beanSerdeShape, keyFieldNames, argumentFieldNames, deserializerFieldNames));

        if (beanSerdeShape.properties().size() > STRING_SWITCH_PROPERTY_THRESHOLD) {
            classDefBuilder.addAnnotation(AnnotationDef.builder(SuppressWarnings.class)
                .addMember(GENERATED_VALUE_MEMBER, "FallThrough")
                .build());
        }
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
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter context = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(1);
                if (deserializerFieldNames.isEmpty()) {
                    return ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                        .invokeStatic(WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD, aThis, context, type)
                        .cast(TypeDef.parameterized(Deserializer.class, beanTypeDef))
                        .returning();
                }
                List<StatementDef> statements = new ArrayList<>();
                List<ExpressionDef> deserializerValues = new ArrayList<>();
                List<TypeDef> constructorParameterTypes = new ArrayList<>();
                int index = 0;
                for (Map.Entry<String, String> deserializerFieldEntry : deserializerFieldNames.entrySet()) {
                    String propertyName = deserializerFieldEntry.getKey();
                    String argumentFieldName = required(argumentFieldNames, propertyName);
                    ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(argumentFieldName, ARGUMENT_TYPE);
                    StatementDef.DefineAndAssign deserializerDef = context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression)
                        .newLocal(BeanSerdeSourceGenUtils.localName("deserializer", index));
                    statements.add(deserializerDef);
                    deserializerValues.add(deserializerDef.variable());
                    constructorParameterTypes.add(DESERIALIZER_TYPE);
                    index++;
                }
                statements.add(ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                    .invokeStatic(
                        WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD,
                        deserializerClassTypeDef.instantiate(constructorParameterTypes, deserializerValues),
                        context,
                        type
                    )
                    .cast(TypeDef.parameterized(Deserializer.class, beanTypeDef))
                    .returning());
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
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
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

                List<BeanSerdeShape.BeanProperty> properties = beanSerdeShape.properties();
                List<VariableDef.Local> seenPropertyVariables = new ArrayList<>(properties.size());
                for (int i = 0; i < properties.size(); i++) {
                    StatementDef.DefineAndAssign seenPropertyDef = ExpressionDef.falseValue()
                        .newLocal(BeanSerdeSourceGenUtils.localName("seenProperty", i));
                    statements.add(seenPropertyDef);
                    seenPropertyVariables.add(seenPropertyDef.variable());
                }
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
                StatementDef switchStatement = buildPropertyDispatchStatement(
                    deserializerClassTypeDef,
                    objectDecoder,
                    context,
                    type,
                    keyVariable,
                    properties,
                    keyFieldNames,
                    seenPropertyVariables,
                    propertyDeserializers
                );
                statements.add(keyVariable.isNonNull().whileLoop(
                    StatementDef.multi(
                        switchStatement,
                        keyVariable.assign(objectDecoder.invoke(DECODE_KEY_METHOD))
                    )
                ));
                statements.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                statements.add(beanVariable.returning());

                return StatementDef.multi(statements);
            });
    }

    private StatementDef buildPropertyDispatchStatement(ClassTypeDef deserializerClassTypeDef,
                                                        VariableDef objectDecoder,
                                                        VariableDef.MethodParameter context,
                                                        VariableDef.MethodParameter type,
                                                        VariableDef keyVariable,
                                                        List<BeanSerdeShape.BeanProperty> properties,
                                                        Map<String, String> keyFieldNames,
                                                        List<VariableDef.Local> seenPropertyVariables,
                                                        List<StatementDef> propertyDeserializers) {
        StatementDef unknownPropertyStatement = ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
            .invokeStatic(HANDLE_UNKNOWN_PROPERTY_METHOD, objectDecoder, context, keyVariable, type);
        if (properties.isEmpty()) {
            return unknownPropertyStatement;
        }
        if (properties.size() > STRING_SWITCH_PROPERTY_THRESHOLD) {
            StatementDef.DefineAndAssign handledPropertyDef = ExpressionDef.falseValue().newLocal("handledProperty");
            VariableDef.Local handledPropertyVariable = handledPropertyDef.variable();
            return StatementDef.multi(
                handledPropertyDef,
                keyVariable.asStatementSwitch(
                    STRING_TYPE,
                    buildSwitchCases(deserializerClassTypeDef, properties, keyFieldNames, seenPropertyVariables, propertyDeserializers, handledPropertyVariable, type),
                    handledPropertyVariable.ifFalse(unknownPropertyStatement)
                )
            );
        }
        StatementDef switchStatement = unknownPropertyStatement;
        for (int i = properties.size() - 1; i >= 0; i--) {
            BeanSerdeShape.BeanProperty property = properties.get(i);
            ExpressionDef propertyNameExpression = deserializerClassTypeDef.getStaticField(required(keyFieldNames, property.name()), STRING_TYPE);
            StatementDef duplicatePropertyStatement = ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                .invokeStatic(DUPLICATE_PROPERTY_METHOD, propertyNameExpression, type)
                .doThrow();
            VariableDef.Local seenPropertyVariable = seenPropertyVariables.get(i);
            switchStatement = keyVariable.invoke(STRING_EQUALS_METHOD, propertyNameExpression).ifTrue(
                seenPropertyVariable.ifTrue(
                    duplicatePropertyStatement,
                    StatementDef.multi(
                        seenPropertyVariable.assign(ExpressionDef.trueValue()),
                        propertyDeserializers.get(i)
                    )
                ),
                switchStatement
            );
        }
        return switchStatement;
    }

    private Map<ExpressionDef.Constant, StatementDef> buildSwitchCases(ClassTypeDef deserializerClassTypeDef,
                                                                       List<BeanSerdeShape.BeanProperty> properties,
                                                                       Map<String, String> keyFieldNames,
                                                                       List<VariableDef.Local> seenPropertyVariables,
                                                                       List<StatementDef> propertyDeserializers,
                                                                       VariableDef.Local handledPropertyVariable,
                                                                       VariableDef.MethodParameter type) {
        Map<ExpressionDef.Constant, StatementDef> switchCases = new LinkedHashMap<>();
        for (int i = 0; i < properties.size(); i++) {
            BeanSerdeShape.BeanProperty property = properties.get(i);
            ExpressionDef propertyNameExpression = deserializerClassTypeDef.getStaticField(required(keyFieldNames, property.name()), STRING_TYPE);
            StatementDef duplicatePropertyStatement = ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                .invokeStatic(DUPLICATE_PROPERTY_METHOD, propertyNameExpression, type)
                .doThrow();
            VariableDef.Local seenPropertyVariable = seenPropertyVariables.get(i);
            switchCases.put(ExpressionDef.constant(property.name()),
                handledPropertyVariable.ifFalse(
                    seenPropertyVariable.ifTrue(
                        duplicatePropertyStatement,
                        StatementDef.multi(
                            handledPropertyVariable.assign(ExpressionDef.trueValue()),
                            seenPropertyVariable.assign(ExpressionDef.trueValue()),
                            propertyDeserializers.get(i)
                        )
                    )
                )
            );
        }
        return switchCases;
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
        ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, property.name()), ARGUMENT_TYPE);
        ExpressionDef propertyNameExpression = deserializerClassTypeDef.getStaticField(required(keyFieldNames, property.name()), STRING_TYPE);
        Method scalarDecodeMethod = scalarDecoderMethod(property.deserializationType());
        boolean primitiveScalar = scalarDecodeMethod != null && property.deserializationType().isPrimitive() && !property.deserializationType().isArray();
        StatementDef.DefineAndAssign deserializedValueDef;
        StatementDef deserializeAndAssign;
        if (scalarDecodeMethod != null) {
            if (primitiveScalar) {
                deserializedValueDef = BeanSerdeSourceGenUtils.primitiveDefaultValueExpression(property.deserializationType())
                    .newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
                deserializeAndAssign = StatementDef.multi(
                    deserializedValueDef,
                    objectDecoder.invoke(DECODE_NULL_METHOD).ifFalse(
                        deserializedValueDef.variable().assign(objectDecoder.invoke(scalarDecodeMethod))
                    )
                );
            } else {
                deserializedValueDef = objectDecoder.invoke(scalarDecodeMethod)
                    .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
                    .newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
                deserializeAndAssign = deserializedValueDef;
            }
        } else {
            String deserializerFieldName = required(deserializerFieldNames, property.name());
            StatementDef.DefineAndAssign deserializerDef = new StatementDef.DefineAndAssign(
                new VariableDef.Local(BeanSerdeSourceGenUtils.localName("deserializer", index), NULLABLE_DESERIALIZER_TYPE),
                aThis.field(deserializerFieldName, DESERIALIZER_TYPE)
            );
            StatementDef initializeDeserializerStatement = deserializerDef.variable().isNull().ifTrue(
                deserializerDef.variable().assign(context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression).invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression))
            );
            VariableDef deserializerVariable = deserializerDef.variable();
            deserializedValueDef = deserializerVariable.invoke(
                DESERIALIZE_NULLABLE_METHOD,
                objectDecoder,
                context,
                argumentExpression
            ).cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType())).newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
            deserializeAndAssign = StatementDef.multi(
                deserializerDef,
                initializeDeserializerStatement,
                deserializedValueDef
            );
        }
        StatementDef assignStatement = beanVariable.invoke(property.writeMethod(), deserializedValueDef.variable());
        StatementDef propertyAssignment;
        if (primitiveScalar) {
            propertyAssignment = assignStatement;
        } else if (property.deserializationType().isPrimitive() || property.nullable()) {
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
                ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
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

    private String indexedName(String prefix, int index) {
        return prefix + "_" + index;
    }

    private @Nullable Method scalarDecoderMethod(ClassElement type) {
        if (type.isArray()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean" -> DECODE_BOOLEAN_METHOD;
            case "java.lang.Boolean" -> DECODE_BOOLEAN_NULLABLE_METHOD;
            case "byte" -> DECODE_BYTE_METHOD;
            case "java.lang.Byte" -> DECODE_BYTE_NULLABLE_METHOD;
            case "short" -> DECODE_SHORT_METHOD;
            case "java.lang.Short" -> DECODE_SHORT_NULLABLE_METHOD;
            case "char" -> DECODE_CHAR_METHOD;
            case "java.lang.Character" -> DECODE_CHAR_NULLABLE_METHOD;
            case "int" -> DECODE_INT_METHOD;
            case "java.lang.Integer" -> DECODE_INT_NULLABLE_METHOD;
            case "long" -> DECODE_LONG_METHOD;
            case "java.lang.Long" -> DECODE_LONG_NULLABLE_METHOD;
            case "float" -> DECODE_FLOAT_METHOD;
            case "java.lang.Float" -> DECODE_FLOAT_NULLABLE_METHOD;
            case "double" -> DECODE_DOUBLE_METHOD;
            case "java.lang.Double" -> DECODE_DOUBLE_NULLABLE_METHOD;
            case "java.lang.String" -> DECODE_STRING_NULLABLE_METHOD;
            case "java.math.BigInteger" -> DECODE_BIG_INTEGER_NULLABLE_METHOD;
            case "java.math.BigDecimal" -> DECODE_BIG_DECIMAL_NULLABLE_METHOD;
            default -> null;
        };
    }
}
