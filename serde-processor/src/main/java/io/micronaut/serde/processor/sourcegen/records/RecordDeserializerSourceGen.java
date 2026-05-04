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
 * Generates optimized source deserializers for records.
 */
public final class RecordDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final int STRING_SWITCH_PROPERTY_THRESHOLD = 5;
    private static final String CONTEXT_PARAMETER = "context";
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
    private static final Method DECODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeNull");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "finishStructure");
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

    public ClassDef generate(ClassElement element, RecordSerdeShape recordSerdeShape) {
        TypeDef recordTypeDef = TypeDef.of(element);
        ClassTypeDef deserializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
        Map<String, String> keyFieldNames = new LinkedHashMap<>();
        Map<String, String> argumentFieldNames = new LinkedHashMap<>();
        Map<String, String> deserializerFieldNames = new LinkedHashMap<>();
        List<FieldDef> fields = new ArrayList<>();

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
                .initializer(RecordSerdeSourceGenUtils.argumentExpression(lookupType))
                .build());
            if (scalarDecoderMethod(component.type()) == null) {
                String deserializerFieldName = indexedName("DESERIALIZER", index);
                deserializerFieldNames.put(component.name(), deserializerFieldName);
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
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, recordTypeDef))
            .addFields(fields)
            .addMethod(generateNoArgsConstructor(deserializerFieldNames))
            .addMethod(generateCreateSpecificMethod(recordTypeDef, deserializerClassTypeDef, argumentFieldNames, deserializerFieldNames))
            .addMethod(generateDeserializeMethod(element, recordTypeDef, deserializerClassTypeDef, recordSerdeShape, keyFieldNames, argumentFieldNames, deserializerFieldNames));

        if (recordSerdeShape.components().size() > STRING_SWITCH_PROPERTY_THRESHOLD) {
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

    private MethodDef generateCreateSpecificMethod(TypeDef recordTypeDef,
                                                   ClassTypeDef deserializerClassTypeDef,
                                                   Map<String, String> argumentFieldNames,
                                                   Map<String, String> deserializerFieldNames) {
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
                if (deserializerFieldNames.isEmpty()) {
                    return ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                        .invokeStatic(WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD, aThis, context, type)
                        .cast(TypeDef.parameterized(Deserializer.class, recordTypeDef))
                        .returning();
                }
                List<StatementDef> statements = new ArrayList<>();
                List<ExpressionDef> deserializerValues = new ArrayList<>();
                List<TypeDef> constructorParameterTypes = new ArrayList<>();
                int index = 0;
                for (Map.Entry<String, String> deserializerFieldEntry : deserializerFieldNames.entrySet()) {
                    String componentName = deserializerFieldEntry.getKey();
                    String argumentFieldName = required(argumentFieldNames, componentName);
                    ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(argumentFieldName, ARGUMENT_TYPE);
                    StatementDef.DefineAndAssign deserializerDef = context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression)
                        .newLocal(RecordSerdeSourceGenUtils.localName("deserializer", index));
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
                    .cast(TypeDef.parameterized(Deserializer.class, recordTypeDef))
                    .returning());
                return StatementDef.multi(statements);
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
                    componentDeserializers.add(deserializeComponent(
                        aThis,
                        deserializerClassTypeDef,
                        objectDecoder,
                        context,
                        type,
                        valueVariable,
                        component,
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
                StatementDef switchStatement = buildComponentDispatchStatement(
                    deserializerClassTypeDef,
                    objectDecoder,
                    context,
                    type,
                    keyVariable,
                    components,
                    keyFieldNames,
                    seenPropertyVariables,
                    componentDeserializers
                );
                statements.add(keyVariable.isNonNull().whileLoop(
                    StatementDef.multi(
                        switchStatement,
                        keyVariable.assign(objectDecoder.invoke(DECODE_KEY_METHOD))
                    )
                ));
                statements.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                for (int i = 0; i < components.size(); i++) {
                    RecordSerdeShape.RecordComponent component = components.get(i);
                    boolean nonNull = component.propertyElement().isNonNull();
                    statements.add(seenPropertyVariables.get(i).ifFalse(
                        valueVariables.get(i).assign(RecordSerdeSourceGenUtils.defaultValueExpression(component.type(), nonNull))
                    ));
                }
                statements.add(ClassTypeDef.of(element).instantiate(recordSerdeShape.canonicalConstructor(), constructorValues).returning());
                return StatementDef.multi(statements);
            });
    }

    private StatementDef buildComponentDispatchStatement(ClassTypeDef deserializerClassTypeDef,
                                                         VariableDef objectDecoder,
                                                         VariableDef.MethodParameter context,
                                                         VariableDef.MethodParameter type,
                                                         VariableDef keyVariable,
                                                         List<RecordSerdeShape.RecordComponent> components,
                                                         Map<String, String> keyFieldNames,
                                                         List<VariableDef.Local> seenPropertyVariables,
                                                         List<StatementDef> componentDeserializers) {
        StatementDef unknownPropertyStatement = ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
            .invokeStatic(HANDLE_UNKNOWN_PROPERTY_METHOD, objectDecoder, context, keyVariable, type);
        if (components.isEmpty()) {
            return unknownPropertyStatement;
        }
        if (components.size() > STRING_SWITCH_PROPERTY_THRESHOLD) {
            StatementDef.DefineAndAssign handledPropertyDef = ExpressionDef.falseValue().newLocal("handledProperty");
            VariableDef.Local handledPropertyVariable = handledPropertyDef.variable();
            return StatementDef.multi(
                handledPropertyDef,
                keyVariable.asStatementSwitch(
                    STRING_TYPE,
                    buildSwitchCases(objectDecoder, components, seenPropertyVariables, componentDeserializers, handledPropertyVariable),
                    handledPropertyVariable.ifFalse(unknownPropertyStatement)
                )
            );
        }
        StatementDef switchStatement = unknownPropertyStatement;
        for (int i = components.size() - 1; i >= 0; i--) {
            RecordSerdeShape.RecordComponent component = components.get(i);
            ExpressionDef componentNameExpression = deserializerClassTypeDef.getStaticField(required(keyFieldNames, component.name()), STRING_TYPE);
            VariableDef.Local seenPropertyVariable = seenPropertyVariables.get(i);
            switchStatement = keyVariable.invoke(STRING_EQUALS_METHOD, componentNameExpression).ifTrue(
                seenPropertyVariable.ifTrue(
                    objectDecoder.invoke(SKIP_VALUE_METHOD),
                    StatementDef.multi(
                        seenPropertyVariable.assign(ExpressionDef.trueValue()),
                        componentDeserializers.get(i)
                    )
                ),
                switchStatement
            );
        }
        return switchStatement;
    }

    private Map<ExpressionDef.Constant, StatementDef> buildSwitchCases(VariableDef objectDecoder,
                                                                       List<RecordSerdeShape.RecordComponent> components,
                                                                       List<VariableDef.Local> seenPropertyVariables,
                                                                       List<StatementDef> componentDeserializers,
                                                                       VariableDef.Local handledPropertyVariable) {
        Map<ExpressionDef.Constant, StatementDef> switchCases = new LinkedHashMap<>();
        for (int i = 0; i < components.size(); i++) {
            RecordSerdeShape.RecordComponent component = components.get(i);
            VariableDef.Local seenPropertyVariable = seenPropertyVariables.get(i);
            switchCases.put(ExpressionDef.constant(component.name()),
                handledPropertyVariable.ifFalse(
                    StatementDef.multi(
                        handledPropertyVariable.assign(ExpressionDef.trueValue()),
                        seenPropertyVariable.ifTrue(
                            objectDecoder.invoke(SKIP_VALUE_METHOD),
                            StatementDef.multi(
                                seenPropertyVariable.assign(ExpressionDef.trueValue()),
                                componentDeserializers.get(i)
                            )
                        )
                    )
                )
            );
        }
        return switchCases;
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
                                              Map<String, String> keyFieldNames,
                                              Map<String, String> argumentFieldNames,
                                              Map<String, String> deserializerFieldNames) {
        ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, component.name()), ARGUMENT_TYPE);
        ExpressionDef componentNameExpression = deserializerClassTypeDef.getStaticField(required(keyFieldNames, component.name()), STRING_TYPE);
        Method scalarDecodeMethod = scalarDecoderMethod(component.type());
        StatementDef deserializeAndAssign;
        if (scalarDecodeMethod != null) {
            if (component.type().isPrimitive() && !component.type().isArray()) {
                deserializeAndAssign = objectDecoder.invoke(DECODE_NULL_METHOD).ifTrue(
                    valueVariable.assign(RecordSerdeSourceGenUtils.defaultValueExpression(component.type(), component.propertyElement().isNonNull())),
                    valueVariable.assign(objectDecoder.invoke(scalarDecodeMethod))
                );
            } else {
                ExpressionDef decodedValue = objectDecoder.invoke(scalarDecodeMethod);
                if (!component.type().isPrimitive() || component.type().isArray()) {
                    decodedValue = decodedValue.cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()));
                }
                deserializeAndAssign = valueVariable.assign(decodedValue);
            }
        } else {
            String deserializerFieldName = required(deserializerFieldNames, component.name());
            StatementDef.DefineAndAssign deserializerDef = aThis.field(deserializerFieldName, DESERIALIZER_TYPE)
                .newLocal(RecordSerdeSourceGenUtils.localName("deserializer", index));
            StatementDef initializeDeserializerStatement = deserializerDef.variable().isNull().ifTrue(
                deserializerDef.variable().assign(context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression).invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression))
            );
            VariableDef deserializerVariable = deserializerDef.variable();

            ExpressionDef deserializedValueExpression = deserializerVariable.invoke(
                DESERIALIZE_NULLABLE_METHOD,
                objectDecoder,
                context,
                argumentExpression
            ).cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()));

            deserializeAndAssign = StatementDef.multi(
                deserializerDef,
                initializeDeserializerStatement,
                valueVariable.assign(deserializedValueExpression)
            );
        }
        return StatementDef.doTry(deserializeAndAssign)
            .doCatch(ClassTypeDef.of(Throwable.class), exceptionVariable ->
                ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                    .invokeStatic(
                        WITH_PROPERTY_PATH_THROWABLE_METHOD,
                        exceptionVariable,
                        type,
                        componentNameExpression,
                        argumentExpression
                    )
                    .doThrow()
            );
    }

    private String indexedName(String prefix, int index) {
        return prefix + "_" + index;
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
