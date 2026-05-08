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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
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
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates optimized source deserializers for records.
 */
public final class RecordDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef BOOLEAN_TYPE = TypeDef.primitive(boolean.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final ClassTypeDef DISPATCH_RESULT_TYPE = ClassTypeDef.of(GeneratedSerdeExceptionUtil.PropertyDispatchResult.class);
    private static final int STRING_SWITCH_PROPERTY_THRESHOLD = 2;
    private static final String CONTEXT_PARAMETER = "context";
    private static final String FAIL_ON_NULL_FOR_PRIMITIVES_FIELD = "failOnNullForPrimitives";
    private static final String IGNORE_UNKNOWN_FIELD = "ignoreUnknown";
    private static final String STRICT_NULLABLE_FIELD = "strictNullable";
    private static final String GENERATED_VALUE_MEMBER = "value";
    private static final String HANDLED_DISPATCH_RESULT = "HANDLED";
    private static final String UNKNOWN_DISPATCH_RESULT = "UNKNOWN";
    private static final String DUPLICATE_DISPATCH_RESULT = "DUPLICATE";
    private static final String NULL_DISPATCH_RESULT = "NULL";

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
    private static final Method DECODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeNull");
    private static final Method SKIP_VALUE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "skipValue");
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
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "finishStructure");
    private static final Field OBJECT_ARGUMENT_FIELD = ReflectionUtils.getRequiredField(Argument.class, "OBJECT_ARGUMENT");
    private static final Method ARGUMENT_WITH_NAME_METHOD = ReflectionUtils.getRequiredMethod(Argument.class, "withName", String.class);
    private static final Method UNKNOWN_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "unknownProperty",
        Argument.class,
        Argument.class
    );
    private static final Method DUPLICATE_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "duplicateProperty",
        Argument.class,
        Argument.class
    );
    private static final Method NULL_VALUE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "nullValue",
        Argument.class,
        Argument.class
    );
    private static final Method FAIL_ON_NULL_FOR_PRIMITIVES_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "failOnNullForPrimitives",
        Deserializer.DecoderContext.class
    );
    private static final Method IGNORE_UNKNOWN_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "ignoreUnknown",
        Deserializer.DecoderContext.class
    );
    private static final Method STRICT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "strictNullable",
        Deserializer.DecoderContext.class
    );
    private static final Method STRICT_NULLABLE_CONSTRUCTOR_PARAMETER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "strictNullableConstructorParameter",
        Argument.class,
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
        Argument.class
    );

    private static ExpressionDef dispatchResult(String name) {
        return DISPATCH_RESULT_TYPE.getStaticField(name, DISPATCH_RESULT_TYPE);
    }

    private static ExpressionDef.Constant dispatchResultConstant(String name) {
        return new ExpressionDef.Constant(DISPATCH_RESULT_TYPE, name);
    }

    private static ExpressionDef dynamicPropertyArgument(ExpressionDef propertyNameExpression) {
        return ClassTypeDef.of(Argument.class)
            .getStaticField(OBJECT_ARGUMENT_FIELD)
            .invoke(ARGUMENT_WITH_NAME_METHOD, propertyNameExpression);
    }

    public ClassDef generate(ClassElement element, RecordSerdeShape recordSerdeShape) {
        TypeDef recordTypeDef = TypeDef.of(element);
        ClassTypeDef deserializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
        Map<String, String> keyFieldNames = new LinkedHashMap<>();
        Map<String, String> argumentFieldNames = new LinkedHashMap<>();
        Map<String, String> deserializerFieldNames = new LinkedHashMap<>();
        List<FieldDef> fields = new ArrayList<>();
        boolean failOnNullForPrimitives = requiresFailOnNullForPrimitives(recordSerdeShape);
        boolean strictNullable = requiresStrictNullableCheck(recordSerdeShape);

        int index = 0;
        for (RecordSerdeShape.RecordComponent component : recordSerdeShape.components()) {
            String keyFieldName = indexedName("KEY", index);
            String argumentFieldName = indexedName("ARGUMENT", index);
            keyFieldNames.put(component.name(), keyFieldName);
            argumentFieldNames.put(component.name(), argumentFieldName);

            ClassElement lookupType = resolveLookupType(component.type());
            fields.add(FieldDef.builder(keyFieldName, STRING_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(component.name()))
                .build());
            fields.add(FieldDef.builder(argumentFieldName, ARGUMENT_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(RecordSerdeSourceGenUtils.argumentExpression(
                    lookupType,
                    deserializerClassTypeDef.getStaticField(keyFieldName, STRING_TYPE)
                ))
                .build());
            if (scalarDecoderMethod(component.type()) == null) {
                String deserializerFieldName = RecordSerdeSourceGenUtils.localName("deserializer", index);
                deserializerFieldNames.put(component.name(), deserializerFieldName);
                fields.add(FieldDef.builder(deserializerFieldName, DESERIALIZER_TYPE)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            }
            index++;
        }
        if (failOnNullForPrimitives) {
            fields.add(FieldDef.builder(FAIL_ON_NULL_FOR_PRIMITIVES_FIELD, BOOLEAN_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());
        }
        fields.add(FieldDef.builder(IGNORE_UNKNOWN_FIELD, BOOLEAN_TYPE)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());
        if (strictNullable) {
            fields.add(FieldDef.builder(STRICT_NULLABLE_FIELD, BOOLEAN_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());
        }

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Prototype.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember(GENERATED_VALUE_MEMBER, "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, recordTypeDef))
            .addFields(fields)
            .addMethod(generateCreateSpecificMethod(recordTypeDef))
            .addMethod(generateDeserializeMethod(element, recordTypeDef, deserializerClassTypeDef, recordSerdeShape, keyFieldNames, argumentFieldNames, deserializerFieldNames));
        classDefBuilder.addMethod(generateConstructor(deserializerClassTypeDef, argumentFieldNames, deserializerFieldNames, failOnNullForPrimitives, strictNullable));

        return classDefBuilder.build();
    }

    private MethodDef generateConstructor(ClassTypeDef deserializerClassTypeDef,
                                          Map<String, String> argumentFieldNames,
                                          Map<String, String> deserializerFieldNames,
                                          boolean failOnNullForPrimitives,
                                          boolean strictNullable) {
        MethodDef.MethodDefBuilder constructorBuilder = MethodDef.constructor()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(parameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class)))
            .addParameter(parameter("type", TypeDef.of(Argument.class)))
            .addThrows(TypeDef.of(SerdeException.class));
        return constructorBuilder.build((aThis, methodParameters) -> {
            List<StatementDef> statements = new ArrayList<>();
            VariableDef.MethodParameter context = methodParameters.get(0);
            if (failOnNullForPrimitives) {
                statements.add(aThis.field(FAIL_ON_NULL_FOR_PRIMITIVES_FIELD, BOOLEAN_TYPE).put(
                    ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                        .invokeStatic(FAIL_ON_NULL_FOR_PRIMITIVES_METHOD, context)
                ));
            }
            statements.add(aThis.field(IGNORE_UNKNOWN_FIELD, BOOLEAN_TYPE).put(
                ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                    .invokeStatic(IGNORE_UNKNOWN_METHOD, context)
            ));
            if (strictNullable) {
                statements.add(aThis.field(STRICT_NULLABLE_FIELD, BOOLEAN_TYPE).put(
                    ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                        .invokeStatic(STRICT_NULLABLE_METHOD, context)
                ));
            }
            for (Map.Entry<String, String> deserializerFieldEntry : deserializerFieldNames.entrySet()) {
                String componentName = deserializerFieldEntry.getKey();
                String deserializerFieldName = deserializerFieldEntry.getValue();
                ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, componentName), ARGUMENT_TYPE);
                statements.add(aThis.field(deserializerFieldName, DESERIALIZER_TYPE).put(
                    context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression)
                ));
            }
            return StatementDef.multi(statements);
        });
    }

    private MethodDef generateCreateSpecificMethod(TypeDef recordTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Deserializer.class, recordTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter context = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(1);
                return ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                    .invokeStatic(
                        WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD,
                        aThis,
                        context,
                        type
                    )
                    .cast(TypeDef.parameterized(Deserializer.class, recordTypeDef))
                    .returning();
            });
    }

    private MethodDef generateDeserializeMethod(ClassElement element,
                                                TypeDef recordTypeDef,
                                                ClassTypeDef deserializerClassTypeDef,
                                                RecordSerdeShape recordSerdeShape,
                                                Map<String, String> keyFieldNames,
                                                Map<String, String> argumentFieldNames,
                                                Map<String, String> deserializerFieldNames) {
        return MethodDef.builder("deserialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(recordTypeDef)
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

                List<RecordSerdeShape.RecordComponent> components = recordSerdeShape.components();
                List<VariableDef.Local> seenPropertyVariables = new ArrayList<>(components.size());
                for (int i = 0; i < components.size(); i++) {
                    StatementDef.DefineAndAssign seenPropertyDef = ExpressionDef.falseValue()
                        .newLocal(RecordSerdeSourceGenUtils.localName("seenProperty", i));
                    statements.add(seenPropertyDef);
                    seenPropertyVariables.add(seenPropertyDef.variable());
                }

                List<ExpressionDef> constructorValues = new ArrayList<>(recordSerdeShape.components().size());
                List<VariableDef> valueVariables = new ArrayList<>(components.size());
                List<StatementDef> componentDeserializers = new ArrayList<>(components.size());
                int index = 0;
                for (RecordSerdeShape.RecordComponent component : components) {
                    boolean nonNull = component.propertyElement().isNonNull();
                    StatementDef.DefineAndAssign valueDef = RecordSerdeSourceGenUtils.defaultValueExpression(component.type(), nonNull)
                        .newLocal(RecordSerdeSourceGenUtils.localName("propertyValue", index));
                    statements.add(valueDef);
                    VariableDef valueVariable = valueDef.variable();
                    valueVariables.add(valueVariable);
                    constructorValues.add(valueVariable);
                    if (components.size() < STRING_SWITCH_PROPERTY_THRESHOLD) {
                        componentDeserializers.add(deserializeComponent(
                            aThis,
                            deserializerClassTypeDef,
                            objectDecoder,
                            context,
                            type,
                            valueVariable,
                            component,
                            index,
                            argumentFieldNames,
                            deserializerFieldNames,
                            null
                        ));
                    }
                    index++;
                }

                StatementDef.DefineAndAssign keyDef = objectDecoder.invoke(DECODE_KEY_METHOD).newLocal("key");
                VariableDef keyVariable = keyDef.variable();
                RecordDispatchInfo dispatchInfo = new RecordDispatchInfo(
                    components,
                    keyFieldNames,
                    argumentFieldNames,
                    deserializerFieldNames,
                    seenPropertyVariables,
                    valueVariables,
                    componentDeserializers
                );
                StatementDef switchStatement = buildComponentDispatchStatement(
                    aThis,
                    deserializerClassTypeDef,
                    objectDecoder,
                    context,
                    type,
                    keyVariable,
                    dispatchInfo
                );
                List<StatementDef> finishStatements = new ArrayList<>();
                finishStatements.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                for (int i = 0; i < components.size(); i++) {
                    RecordSerdeShape.RecordComponent component = components.get(i);
                    if (requiresStrictNullableCheck(component)) {
                        finishStatements.add(aThis.field(STRICT_NULLABLE_FIELD, BOOLEAN_TYPE).ifTrue(
                            valueVariables.get(i).isNull().ifTrue(
                                ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                                    .invokeStatic(
                                        STRICT_NULLABLE_CONSTRUCTOR_PARAMETER_METHOD,
                                        type,
                                        deserializerClassTypeDef.getStaticField(required(argumentFieldNames, component.name()), ARGUMENT_TYPE)
                                    )
                                    .doThrow()
                            )
                        ));
                    }
                }
                finishStatements.add(ClassTypeDef.of(element).instantiate(recordSerdeShape.canonicalConstructor(), constructorValues).returning());
                statements.add(ExpressionDef.trueValue().whileLoop(
                    StatementDef.multi(
                        keyDef,
                        keyVariable.isNull().ifTrue(StatementDef.multi(finishStatements)),
                        switchStatement
                    )
                ));
                return StatementDef.multi(statements);
            });
    }

    private StatementDef buildComponentDispatchStatement(VariableDef.This aThis,
                                                         ClassTypeDef deserializerClassTypeDef,
                                                         VariableDef objectDecoder,
                                                         VariableDef.MethodParameter context,
                                                         VariableDef.MethodParameter type,
                                                         VariableDef keyVariable,
                                                         RecordDispatchInfo dispatchInfo) {
        StatementDef unknownPropertyStatement = unknownPropertyStatement(aThis, objectDecoder, dynamicPropertyArgument(keyVariable), type);
        if (dispatchInfo.components().isEmpty()) {
            return unknownPropertyStatement;
        }
        if (dispatchInfo.components().size() >= STRING_SWITCH_PROPERTY_THRESHOLD) {
            return keyVariable.asExpressionSwitch(
                    DISPATCH_RESULT_TYPE,
                    buildSwitchCases(
                        aThis,
                        deserializerClassTypeDef,
                        objectDecoder,
                        context,
                        type,
                        dispatchInfo.components(),
                        dispatchInfo.argumentFieldNames(),
                        dispatchInfo.deserializerFieldNames(),
                        dispatchInfo.seenPropertyVariables(),
                        dispatchInfo.valueVariables()
                    ),
                    dispatchResult(UNKNOWN_DISPATCH_RESULT)
                )
                .newLocal("propertyDispatchResult", dispatchResultVariable -> dispatchResultStatement(
                    aThis,
                    objectDecoder,
                    keyVariable,
                    type,
                    dispatchResultVariable
                )
            );
        }
        StatementDef switchStatement = unknownPropertyStatement;
        for (int i = dispatchInfo.components().size() - 1; i >= 0; i--) {
            RecordSerdeShape.RecordComponent component = dispatchInfo.components().get(i);
            ExpressionDef componentNameExpression = deserializerClassTypeDef.getStaticField(required(dispatchInfo.keyFieldNames(), component.name()), STRING_TYPE);
            ExpressionDef componentArgumentExpression = deserializerClassTypeDef.getStaticField(required(dispatchInfo.argumentFieldNames(), component.name()), ARGUMENT_TYPE);
            VariableDef.Local seenPropertyVariable = dispatchInfo.seenPropertyVariables().get(i);
            switchStatement = keyVariable.invoke(STRING_EQUALS_METHOD, componentNameExpression).ifTrue(
                seenPropertyVariable.ifTrue(
                    duplicatePropertyStatement(componentArgumentExpression, type),
                    StatementDef.multi(
                        seenPropertyVariable.assign(ExpressionDef.trueValue()),
                        dispatchInfo.componentDeserializers().get(i)
                    )
                ),
                switchStatement
            );
        }
        return switchStatement;
    }

    @SuppressWarnings("java:S107")
    private Map<ExpressionDef.Constant, ExpressionDef> buildSwitchCases(VariableDef.This aThis,
                                                                        ClassTypeDef deserializerClassTypeDef,
                                                                        VariableDef objectDecoder,
                                                                        VariableDef.MethodParameter context,
                                                                        VariableDef.MethodParameter type,
                                                                        List<RecordSerdeShape.RecordComponent> components,
                                                                        Map<String, String> argumentFieldNames,
                                                                        Map<String, String> deserializerFieldNames,
                                                                        List<VariableDef.Local> seenPropertyVariables,
                                                                        List<VariableDef> valueVariables) {
        Map<ExpressionDef.Constant, ExpressionDef> switchCases = new LinkedHashMap<>();
        for (int i = 0; i < components.size(); i++) {
            RecordSerdeShape.RecordComponent component = components.get(i);
            VariableDef.Local seenPropertyVariable = seenPropertyVariables.get(i);
            StatementDef.DefineAndAssign dispatchResultDef = dispatchResult(HANDLED_DISPATCH_RESULT).newLocal("dispatchResult");
            VariableDef.Local dispatchResultVariable = dispatchResultDef.variable();
            switchCases.put(ExpressionDef.constant(component.name()),
                new ExpressionDef.SwitchYieldCase(
                    DISPATCH_RESULT_TYPE,
                    StatementDef.multi(
                        dispatchResultDef,
                        seenPropertyVariable.ifTrue(
                            dispatchResultVariable.assign(dispatchResult(DUPLICATE_DISPATCH_RESULT)),
                            StatementDef.multi(
                                seenPropertyVariable.assign(ExpressionDef.trueValue()),
                                deserializeComponent(
                                    aThis,
                                    deserializerClassTypeDef,
                                    objectDecoder,
                                    context,
                                    type,
                                    valueVariables.get(i),
                                    component,
                                    i,
                                    argumentFieldNames,
                                    deserializerFieldNames,
                                    dispatchResultVariable
                                )
                            )
                        ),
                        dispatchResultVariable.returning()
                    )
                )
            );
        }
        return switchCases;
    }

    private StatementDef dispatchResultStatement(VariableDef.This aThis,
                                                 VariableDef objectDecoder,
                                                 VariableDef keyVariable,
                                                 VariableDef.MethodParameter type,
                                                 VariableDef dispatchResultVariable) {
        StatementDef unknownStatement = unknownPropertyStatement(aThis, objectDecoder, dynamicPropertyArgument(keyVariable), type);
        StatementDef duplicateStatement = duplicatePropertyStatement(dynamicPropertyArgument(keyVariable), type);
        StatementDef nullStatement = nullPropertyStatement(type, dynamicPropertyArgument(keyVariable));
        Map<ExpressionDef.Constant, StatementDef> cases = new LinkedHashMap<>();
        cases.put(dispatchResultConstant(UNKNOWN_DISPATCH_RESULT), unknownStatement);
        cases.put(dispatchResultConstant(DUPLICATE_DISPATCH_RESULT), duplicateStatement);
        cases.put(dispatchResultConstant(NULL_DISPATCH_RESULT), nullStatement);
        cases.put(dispatchResultConstant(HANDLED_DISPATCH_RESULT), StatementDef.multi());
        return dispatchResultVariable.asStatementSwitch(DISPATCH_RESULT_TYPE, cases);
    }

    private StatementDef unknownPropertyStatement(VariableDef.This aThis,
                                                  VariableDef objectDecoder,
                                                  ExpressionDef propertyArgumentExpression,
                                                  VariableDef.MethodParameter type) {
        return aThis.field(IGNORE_UNKNOWN_FIELD, BOOLEAN_TYPE).ifTrue(
            objectDecoder.invoke(SKIP_VALUE_METHOD),
            ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                .invokeStatic(UNKNOWN_PROPERTY_METHOD, type, propertyArgumentExpression)
                .doThrow()
        );
    }

    private StatementDef duplicatePropertyStatement(ExpressionDef propertyArgumentExpression,
                                                   VariableDef.MethodParameter type) {
        return ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
            .invokeStatic(DUPLICATE_PROPERTY_METHOD, type, propertyArgumentExpression)
            .doThrow();
    }

    private StatementDef nullPropertyStatement(VariableDef.MethodParameter type,
                                               ExpressionDef propertyArgumentExpression) {
        return ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
            .invokeStatic(
                WITH_PROPERTY_PATH_THROWABLE_METHOD,
                ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                    .invokeStatic(NULL_VALUE_METHOD, type, propertyArgumentExpression),
                type,
                propertyArgumentExpression
            )
            .doThrow();
    }

    @SuppressWarnings("java:S107")
    private StatementDef deserializeComponent(VariableDef.This aThis,
                                              ClassTypeDef deserializerClassTypeDef,
                                              VariableDef objectDecoder,
                                              VariableDef.MethodParameter context,
                                              VariableDef.MethodParameter type,
                                              VariableDef valueVariable,
                                              RecordSerdeShape.RecordComponent component,
                                              int index,
                                              Map<String, String> argumentFieldNames,
                                              Map<String, String> deserializerFieldNames,
                                              @Nullable VariableDef dispatchResultVariable) {
        ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, component.name()), ARGUMENT_TYPE);
        Method scalarDecodeMethod = scalarDecoderMethod(component.type());
        StatementDef deserializeAndAssign;
        if (scalarDecodeMethod != null) {
            if (component.type().isPrimitive() && !component.type().isArray()) {
                Method nullableScalarDecodeMethod = Objects.requireNonNull(scalarDecoderMethod(component.type(), true));
                Method primitiveScalarDecodeMethod = Objects.requireNonNull(scalarDecoderMethod(component.type(), false));
                StatementDef.DefineAndAssign nullableValueDef = objectDecoder.invoke(nullableScalarDecodeMethod)
                    .cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()))
                    .newLocal(RecordSerdeSourceGenUtils.localName("decodedValue", index));
                StatementDef failOnNullForPrimitivesStatement = StatementDef.multi(
                    nullableValueDef,
                    nullableValueDef.variable().isNull().ifTrue(
                        nullValueOrDispatchStatement(type, argumentExpression, dispatchResultVariable),
                        valueVariable.assign(nullableValueDef.variable())
                    )
                );
                StatementDef usePrimitiveDefaultStatement = objectDecoder.invoke(DECODE_NULL_METHOD).ifTrue(
                    StatementDef.multi(),
                    valueVariable.assign(objectDecoder.invoke(primitiveScalarDecodeMethod))
                );
                deserializeAndAssign = aThis.field(FAIL_ON_NULL_FOR_PRIMITIVES_FIELD, BOOLEAN_TYPE)
                    .ifTrue(failOnNullForPrimitivesStatement, usePrimitiveDefaultStatement);
            } else {
                ExpressionDef decodedValue = objectDecoder.invoke(scalarDecodeMethod);
                decodedValue = decodedValue.cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()));
                deserializeAndAssign = valueVariable.assign(decodedValue);
            }
        } else {
            String deserializerFieldName = required(deserializerFieldNames, component.name());
            ExpressionDef deserializedValueExpression = aThis.field(deserializerFieldName, DESERIALIZER_TYPE).invoke(
                DESERIALIZE_NULLABLE_METHOD,
                objectDecoder,
                context,
                argumentExpression
            ).cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()));

            deserializeAndAssign = valueVariable.assign(deserializedValueExpression);
        }
        if ((!component.type().isPrimitive() || component.type().isArray())
            && component.propertyElement().isNonNull()
            && !component.propertyElement().isNullable()) {
            deserializeAndAssign = StatementDef.multi(
                deserializeAndAssign,
                valueVariable.isNull().ifTrue(
                    nullValueOrDispatchStatement(type, argumentExpression, dispatchResultVariable)
                )
            );
        }
        return StatementDef.doTry(deserializeAndAssign)
            .doCatch(ClassTypeDef.of(Throwable.class), exceptionVariable ->
                ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                    .invokeStatic(
                        WITH_PROPERTY_PATH_THROWABLE_METHOD,
                        exceptionVariable,
                        type,
                        argumentExpression
                    )
                    .doThrow()
            );
    }

    private StatementDef nullValueOrDispatchStatement(VariableDef.MethodParameter type,
                                                      ExpressionDef propertyArgumentExpression,
                                                      @Nullable VariableDef dispatchResultVariable) {
        if (dispatchResultVariable == null) {
            return ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                .invokeStatic(NULL_VALUE_METHOD, type, propertyArgumentExpression)
                .doThrow();
        }
        return dispatchResultVariable.assign(dispatchResult(NULL_DISPATCH_RESULT));
    }

    private String indexedName(String prefix, int index) {
        return prefix + "_" + index;
    }

    private boolean requiresStrictNullableCheck(RecordSerdeShape.RecordComponent component) {
        return component.propertyElement().isNonNull()
            && !component.propertyElement().isNullable()
            && (!component.type().isPrimitive() || component.type().isArray());
    }

    private boolean requiresStrictNullableCheck(RecordSerdeShape recordSerdeShape) {
        for (RecordSerdeShape.RecordComponent component : recordSerdeShape.components()) {
            if (requiresStrictNullableCheck(component)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresFailOnNullForPrimitives(RecordSerdeShape recordSerdeShape) {
        for (RecordSerdeShape.RecordComponent component : recordSerdeShape.components()) {
            if (component.type().isPrimitive() && !component.type().isArray()) {
                return true;
            }
        }
        return false;
    }

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

    private @Nullable Method scalarDecoderMethod(ClassElement type) {
        return scalarDecoderMethod(type, true);
    }

    private @Nullable Method scalarDecoderMethod(ClassElement type, boolean nullablePrimitive) {
        if (type.isArray()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean" -> nullablePrimitive ? DECODE_BOOLEAN_NULLABLE_METHOD : DECODE_BOOLEAN_METHOD;
            case "java.lang.Boolean" -> DECODE_BOOLEAN_NULLABLE_METHOD;
            case "byte" -> nullablePrimitive ? DECODE_BYTE_NULLABLE_METHOD : DECODE_BYTE_METHOD;
            case "java.lang.Byte" -> DECODE_BYTE_NULLABLE_METHOD;
            case "short" -> nullablePrimitive ? DECODE_SHORT_NULLABLE_METHOD : DECODE_SHORT_METHOD;
            case "java.lang.Short" -> DECODE_SHORT_NULLABLE_METHOD;
            case "char" -> nullablePrimitive ? DECODE_CHAR_NULLABLE_METHOD : DECODE_CHAR_METHOD;
            case "java.lang.Character" -> DECODE_CHAR_NULLABLE_METHOD;
            case "int" -> nullablePrimitive ? DECODE_INT_NULLABLE_METHOD : DECODE_INT_METHOD;
            case "java.lang.Integer" -> DECODE_INT_NULLABLE_METHOD;
            case "long" -> nullablePrimitive ? DECODE_LONG_NULLABLE_METHOD : DECODE_LONG_METHOD;
            case "java.lang.Long" -> DECODE_LONG_NULLABLE_METHOD;
            case "float" -> nullablePrimitive ? DECODE_FLOAT_NULLABLE_METHOD : DECODE_FLOAT_METHOD;
            case "java.lang.Float" -> DECODE_FLOAT_NULLABLE_METHOD;
            case "double" -> nullablePrimitive ? DECODE_DOUBLE_NULLABLE_METHOD : DECODE_DOUBLE_METHOD;
            case "java.lang.Double" -> DECODE_DOUBLE_NULLABLE_METHOD;
            case "java.lang.String" -> DECODE_STRING_NULLABLE_METHOD;
            case "java.math.BigInteger" -> DECODE_BIG_INTEGER_NULLABLE_METHOD;
            case "java.math.BigDecimal" -> DECODE_BIG_DECIMAL_NULLABLE_METHOD;
            default -> null;
        };
    }

    private ParameterDef parameter(String name, TypeDef type) {
        return ParameterDef.builder(name, type)
            .addAnnotation(Parameter.class)
            .build();
    }

    private record RecordDispatchInfo(List<RecordSerdeShape.RecordComponent> components,
                                      Map<String, String> keyFieldNames,
                                      Map<String, String> argumentFieldNames,
                                      Map<String, String> deserializerFieldNames,
                                      List<VariableDef.Local> seenPropertyVariables,
                                      List<VariableDef> valueVariables,
                                      List<StatementDef> componentDeserializers) {
    }
}
