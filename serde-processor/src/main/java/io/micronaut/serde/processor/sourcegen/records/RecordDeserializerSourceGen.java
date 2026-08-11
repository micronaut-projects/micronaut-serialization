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
import io.micronaut.serde.KeyDescriptor;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
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
import java.util.stream.Stream;

/**
 * Generates optimized source deserializers for records.
 */
public final class RecordDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef BOOLEAN_TYPE = TypeDef.primitive(boolean.class);
    private static final TypeDef INT_TYPE = TypeDef.primitive(int.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final ClassTypeDef KEYS_TYPE = ClassTypeDef.of(Keys.class);
    private static final ClassTypeDef KEY_DESCRIPTOR_TYPE = ClassTypeDef.of(KeyDescriptor.class);
    private static final ClassTypeDef KEYS_AWARE_DECODER_TYPE = ClassTypeDef.of(KeysAwareDecoder.class);
    private static final ClassTypeDef DISPATCH_RESULT_TYPE = ClassTypeDef.of(GeneratedSerdeExceptionUtil.PropertyDispatchResult.class);
    private static final String CONTEXT_PARAMETER = "context";
    private static final String KEYS_FIELD = "KEYS";
    private static final String FAIL_ON_NULL_FOR_PRIMITIVES_FIELD = "failOnNullForPrimitives";
    private static final String IGNORE_UNKNOWN_FIELD = "ignoreUnknown";
    private static final String STRICT_NULLABLE_FIELD = "strictNullable";
    private static final String GENERATED_VALUE_MEMBER = "value";
    private static final String HANDLED_DISPATCH_RESULT = "HANDLED";
    private static final String UNKNOWN_DISPATCH_RESULT = "UNKNOWN";
    private static final String DUPLICATE_DISPATCH_RESULT = "DUPLICATE";
    private static final String NULL_DISPATCH_RESULT = "NULL";
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
    private static final Method KEYS_CREATE_METHOD = ReflectionUtils.getRequiredMethod(Keys.class, "create", String[].class);
    private static final Method KEYS_CREATE_WITH_METADATA_METHOD = ReflectionUtils.getRequiredMethod(Keys.class, "createWithMetadata", KeyDescriptor[].class);
    private static final Method KEY_DESCRIPTOR_CREATE_METHOD = ReflectionUtils.getRequiredMethod(KeyDescriptor.class, "create", String.class, String[].class);
    private static final Method DECODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeObject", Argument.class);
    private static final Method DECODE_KEY_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeKey");
    private static final Method KEYS_AWARE_DECODER_OF_METHOD = ReflectionUtils.getRequiredMethod(KeysAwareDecoder.class, "of", Decoder.class);
    private static final Method DECODE_KEY_KEYS_METHOD = ReflectionUtils.getRequiredMethod(KeysAwareDecoder.class, "decodeKey", Keys.class);
    private static final Method SKIP_VALUE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "skipValue");
    private static final Method DECODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeNull");
    private static final Method DECODE_STRING_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeString");
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
        IGNORE_UNKNOWN_FIELD,
        Deserializer.DecoderContext.class
    );
    private static final Method STRICT_NULLABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        STRICT_NULLABLE_FIELD,
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
                .initializer(ExpressionDef.constant(component.serializedName()))
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
        if (!keyFieldNames.isEmpty()) {
            fields.add(FieldDef.builder(KEYS_FIELD, KEYS_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(keysCreateExpression(deserializerClassTypeDef, recordSerdeShape.components(), new ArrayList<>(keyFieldNames.values())))
                .build());
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
        classDefBuilder.addMethod(generateConstructor(
            element,
            deserializerClassTypeDef,
            recordSerdeShape,
            argumentFieldNames,
            deserializerFieldNames,
            failOnNullForPrimitives,
            strictNullable
        ));

        return classDefBuilder.build();
    }

    private MethodDef generateConstructor(ClassElement element,
                                          ClassTypeDef deserializerClassTypeDef,
                                          RecordSerdeShape recordSerdeShape,
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
                ExpressionDef deserializerExpression = isSelfReferentialComponent(element, recordSerdeShape, componentName)
                    ? ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                        .invokeStatic(WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD, aThis, context, argumentExpression)
                    : context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression);
                statements.add(aThis.field(deserializerFieldName, DESERIALIZER_TYPE).put(
                    deserializerExpression
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

                StatementDef.DefineAndAssign ignoreUnknownDef = aThis.field(IGNORE_UNKNOWN_FIELD, BOOLEAN_TYPE).newLocal(IGNORE_UNKNOWN_FIELD);
                statements.add(ignoreUnknownDef);
                VariableDef ignoreUnknownVariable = ignoreUnknownDef.variable();

                List<RecordSerdeShape.RecordComponent> components = recordSerdeShape.components();
                VariableDef.Local seenPropertiesMask = null;
                List<VariableDef.Local> seenPropertyVariables = new ArrayList<>(components.size());
                if (hasSmallSeenPropertiesMask(components)) {
                    StatementDef.DefineAndAssign seenPropertiesDef = ExpressionDef.constant(0L).newLocal("seenProperties");
                    statements.add(seenPropertiesDef);
                    seenPropertiesMask = seenPropertiesDef.variable();
                } else {
                    for (int i = 0; i < components.size(); i++) {
                        StatementDef.DefineAndAssign seenPropertyDef = ExpressionDef.falseValue()
                            .newLocal(RecordSerdeSourceGenUtils.localName("seenProperty", i));
                        statements.add(seenPropertyDef);
                        seenPropertyVariables.add(seenPropertyDef.variable());
                    }
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
                        argumentFieldNames,
                        deserializerFieldNames,
                        null
                    ));
                    index++;
                }

                RecordDispatchInfo dispatchInfo = new RecordDispatchInfo(
                    components,
                    keyFieldNames,
                    argumentFieldNames,
                    deserializerFieldNames,
                    seenPropertiesMask,
                    seenPropertyVariables,
                    valueVariables,
                    componentDeserializers
                );
                List<StatementDef> finishStatements = new ArrayList<>();
                finishStatements.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                StatementDef strictNullableStatement = strictNullableStatement(aThis, deserializerClassTypeDef, type, dispatchInfo);
                if (strictNullableStatement != null) {
                    finishStatements.add(strictNullableStatement);
                }
                finishStatements.add(ClassTypeDef.of(element).instantiate(recordSerdeShape.canonicalConstructor(), constructorValues).returning());
                StatementDef finishStatement = StatementDef.multi(finishStatements);
                StatementDef stringDispatchLoop = buildStringComponentDispatchLoop(
                    aThis,
                    deserializerClassTypeDef,
                    objectDecoder,
                    context,
                    type,
                    ignoreUnknownVariable,
                    dispatchInfo,
                    finishStatement
                );
                if (components.isEmpty()) {
                    statements.add(stringDispatchLoop);
                } else {
                    statements.add(KEYS_AWARE_DECODER_TYPE.invokeStatic(KEYS_AWARE_DECODER_OF_METHOD, objectDecoder)
                        .newLocal("keysAwareDecoder", keysAwareDecoder -> buildKeysAwareComponentDispatchLoop(
                            aThis,
                            deserializerClassTypeDef,
                            objectDecoder,
                            keysAwareDecoder,
                            context,
                            type,
                            ignoreUnknownVariable,
                            dispatchInfo,
                            finishStatement
                        ))
                    );
                }
                return StatementDef.multi(statements);
            });
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildStringComponentDispatchLoop(VariableDef.This aThis,
                                                          ClassTypeDef deserializerClassTypeDef,
                                                          VariableDef objectDecoder,
                                                          VariableDef.MethodParameter context,
                                                          VariableDef.MethodParameter type,
                                                          ExpressionDef ignoreUnknownExpression,
                                                          RecordDispatchInfo dispatchInfo,
                                                          StatementDef finishStatement) {
        return ExpressionDef.trueValue().whileLoop(buildStringComponentDispatchStep(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            context,
            type,
            ignoreUnknownExpression,
            dispatchInfo,
            finishStatement
        ));
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildStringComponentDispatchStep(VariableDef.This aThis,
                                                          ClassTypeDef deserializerClassTypeDef,
                                                          VariableDef objectDecoder,
                                                          VariableDef.MethodParameter context,
                                                          VariableDef.MethodParameter type,
                                                          ExpressionDef ignoreUnknownExpression,
                                                          RecordDispatchInfo dispatchInfo,
                                                          StatementDef finishStatement) {
        StatementDef.DefineAndAssign keyDef = objectDecoder.invoke(DECODE_KEY_METHOD).newLocal("key");
        VariableDef keyVariable = keyDef.variable();
        StatementDef switchStatement = buildComponentDispatchStatement(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            context,
            type,
            keyVariable,
            ignoreUnknownExpression,
            dispatchInfo
        );
        return StatementDef.multi(
            keyDef,
            keyVariable.isNull().ifTrue(finishStatement),
            switchStatement
        );
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildKeysAwareComponentDispatchLoop(VariableDef.This aThis,
                                                             ClassTypeDef deserializerClassTypeDef,
                                                             VariableDef objectDecoder,
                                                             VariableDef keysAwareDecoder,
                                                             VariableDef.MethodParameter context,
                                                             VariableDef.MethodParameter type,
                                                             ExpressionDef ignoreUnknownExpression,
                                                             RecordDispatchInfo dispatchInfo,
                                                             StatementDef finishStatement) {
        ExpressionDef keyIndexExpression = keysAwareDecoder.invoke(
            DECODE_KEY_KEYS_METHOD,
            deserializerClassTypeDef.getStaticField(KEYS_FIELD, KEYS_TYPE)
        );
        List<StatementDef> loopStatements = new ArrayList<>();
        loopStatements.add(buildKeyIndexComponentDispatchStatement(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            keysAwareDecoder,
            context,
            type,
            keyIndexExpression,
            ignoreUnknownExpression,
            dispatchInfo,
            finishStatement
        ));
        return ExpressionDef.trueValue().whileLoop(StatementDef.multi(loopStatements));
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildKeyIndexComponentDispatchStatement(VariableDef.This aThis,
                                                                 ClassTypeDef deserializerClassTypeDef,
                                                                 VariableDef objectDecoder,
                                                                 VariableDef keysAwareDecoder,
                                                                 VariableDef.MethodParameter context,
                                                                 VariableDef.MethodParameter type,
                                                                 ExpressionDef keyIndexExpression,
                                                                 ExpressionDef ignoreUnknownExpression,
                                                                 RecordDispatchInfo dispatchInfo,
                                                                 StatementDef finishStatement) {
        Map<ExpressionDef.Constant, StatementDef> cases = buildKeyIndexLifecycleCases(
            keysAwareDecoder,
            ignoreUnknownExpression,
            type,
            finishStatement
        );
        for (int i = 0; i < dispatchInfo.components().size(); i++) {
            RecordSerdeShape.RecordComponent component = dispatchInfo.components().get(i);
            ExpressionDef componentArgumentExpression = deserializerClassTypeDef.getStaticField(required(dispatchInfo.argumentFieldNames(), component.name()), ARGUMENT_TYPE);
            int componentIndex = i;
            StatementDef deserializeComponent = dispatchInfo.seenPropertiesMask() == null
                ? deserializeComponent(
                    aThis,
                    deserializerClassTypeDef,
                    objectDecoder,
                    context,
                    type,
                    dispatchInfo.valueVariables().get(componentIndex),
                    component,
                    componentIndex,
                    dispatchInfo.argumentFieldNames(),
                    dispatchInfo.deserializerFieldNames(),
                    null
                )
                : deserializeComponentDirect(
                    aThis,
                    deserializerClassTypeDef,
                    objectDecoder,
                    context,
                    type,
                    dispatchInfo.valueVariables().get(componentIndex),
                    component,
                    componentIndex,
                    dispatchInfo.argumentFieldNames(),
                    dispatchInfo.deserializerFieldNames()
                );
            if (dispatchInfo.seenPropertiesMask() != null) {
                deserializeComponent = StatementDef.multi(
                    isComponentSeen(dispatchInfo, i).ifTrue(duplicatePropertyStatement(componentArgumentExpression, type)),
                    markComponentSeen(dispatchInfo, i),
                    wrapWithPropertyPath(deserializeComponent, type, componentArgumentExpression)
                );
            } else {
                deserializeComponent = isComponentSeen(dispatchInfo, i).doIfElse(
                    duplicatePropertyStatement(componentArgumentExpression, type),
                    StatementDef.multi(
                        markComponentSeen(dispatchInfo, i),
                        deserializeComponent
                    )
                );
            }
            cases.put(ExpressionDef.constant(i), deserializeComponent);
        }
        return keyIndexExpression.asStatementSwitch(INT_TYPE, cases);
    }

    private Map<ExpressionDef.Constant, StatementDef> buildKeyIndexLifecycleCases(VariableDef keysAwareDecoder,
                                                                                  ExpressionDef ignoreUnknownExpression,
                                                                                  VariableDef.MethodParameter type,
                                                                                  StatementDef finishStatement) {
        Map<ExpressionDef.Constant, StatementDef> cases = new LinkedHashMap<>();
        cases.put(ExpressionDef.constant(KeysAwareDecoder.MATCH_END_OBJECT), finishStatement);
        cases.put(ExpressionDef.constant(KeysAwareDecoder.MATCH_UNKNOWN_NAME), buildUnknownKeyIndexComponentDispatchStep(
            keysAwareDecoder,
            ignoreUnknownExpression,
            type,
            finishStatement
        ));
        return cases;
    }

    private StatementDef buildUnknownKeyIndexComponentDispatchStep(VariableDef keysAwareDecoder,
                                                                   ExpressionDef ignoreUnknownExpression,
                                                                   VariableDef.MethodParameter type,
                                                                   StatementDef finishStatement) {
        StatementDef.DefineAndAssign keyDef = keysAwareDecoder.invoke(DECODE_KEY_METHOD).newLocal("key");
        VariableDef keyVariable = keyDef.variable();
        return StatementDef.multi(
            keyDef,
            keyVariable.isNull().ifTrue(finishStatement),
            unknownPropertyStatement(ignoreUnknownExpression, keysAwareDecoder, dynamicPropertyArgument(keyVariable), type)
        );
    }

    private StatementDef buildComponentDispatchStatement(VariableDef.This aThis,
                                                         ClassTypeDef deserializerClassTypeDef,
                                                         VariableDef objectDecoder,
                                                         VariableDef.MethodParameter context,
                                                         VariableDef.MethodParameter type,
                                                         VariableDef keyVariable,
                                                         ExpressionDef ignoreUnknownExpression,
                                                         RecordDispatchInfo dispatchInfo) {
        StatementDef unknownPropertyStatement = unknownPropertyStatement(ignoreUnknownExpression, objectDecoder, dynamicPropertyArgument(keyVariable), type);
        if (dispatchInfo.components().isEmpty()) {
            return unknownPropertyStatement;
        }
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
                    dispatchInfo.seenPropertiesMask(),
                    dispatchInfo.seenPropertyVariables(),
                    dispatchInfo.valueVariables()
                ),
                dispatchResult(UNKNOWN_DISPATCH_RESULT)
            )
            .newLocal("propertyDispatchResult", dispatchResultVariable -> dispatchResultStatement(
                objectDecoder,
                keyVariable,
                ignoreUnknownExpression,
                type,
                dispatchResultVariable
            )
        );
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
                                                                        VariableDef.@Nullable Local seenPropertiesMaskVariable,
                                                                        List<VariableDef.Local> seenPropertyVariables,
                                                                        List<VariableDef> valueVariables) {
        Map<ExpressionDef.Constant, ExpressionDef> switchCases = new LinkedHashMap<>();
        for (int i = 0; i < components.size(); i++) {
            RecordSerdeShape.RecordComponent component = components.get(i);
            StatementDef.DefineAndAssign dispatchResultDef = dispatchResult(HANDLED_DISPATCH_RESULT).newLocal("dispatchResult");
            VariableDef.Local dispatchResultVariable = dispatchResultDef.variable();
            switchCases.put(ExpressionDef.constant(component.serializedName()),
                new ExpressionDef.SwitchYieldCase(
                    DISPATCH_RESULT_TYPE,
                    StatementDef.multi(
                        dispatchResultDef,
                        isComponentSeen(seenPropertiesMaskVariable, seenPropertyVariables, i).doIfElse(
                            dispatchResultVariable.assign(dispatchResult(DUPLICATE_DISPATCH_RESULT)),
                            StatementDef.multi(
                                markComponentSeen(seenPropertiesMaskVariable, seenPropertyVariables, i),
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

    private StatementDef dispatchResultStatement(VariableDef objectDecoder,
                                                 VariableDef keyVariable,
                                                 ExpressionDef ignoreUnknownExpression,
                                                 VariableDef.MethodParameter type,
                                                 VariableDef dispatchResultVariable) {
        StatementDef unknownStatement = unknownPropertyStatement(ignoreUnknownExpression, objectDecoder, dynamicPropertyArgument(keyVariable), type);
        StatementDef duplicateStatement = duplicatePropertyStatement(dynamicPropertyArgument(keyVariable), type);
        StatementDef nullStatement = nullPropertyStatement(type, dynamicPropertyArgument(keyVariable));
        Map<ExpressionDef.Constant, StatementDef> cases = new LinkedHashMap<>();
        cases.put(dispatchResultConstant(UNKNOWN_DISPATCH_RESULT), unknownStatement);
        cases.put(dispatchResultConstant(DUPLICATE_DISPATCH_RESULT), duplicateStatement);
        cases.put(dispatchResultConstant(NULL_DISPATCH_RESULT), nullStatement);
        cases.put(dispatchResultConstant(HANDLED_DISPATCH_RESULT), StatementDef.multi());
        return dispatchResultVariable.asStatementSwitch(DISPATCH_RESULT_TYPE, cases);
    }

    private ExpressionDef.ConditionExpressionDef isComponentSeen(RecordDispatchInfo dispatchInfo, int componentIndex) {
        return isComponentSeen(dispatchInfo.seenPropertiesMask(), dispatchInfo.seenPropertyVariables(), componentIndex);
    }

    private ExpressionDef.ConditionExpressionDef isComponentSeen(VariableDef.@Nullable Local seenPropertiesMask,
                                                                 List<VariableDef.Local> seenPropertyVariables,
                                                                 int componentIndex) {
        if (seenPropertiesMask != null) {
            return seenPropertiesMask.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_AND, seenComponentMask(componentIndex))
                .compare(ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO, ExpressionDef.constant(0L));
        }
        return seenPropertyVariables.get(componentIndex).isTrue();
    }

    private StatementDef markComponentSeen(RecordDispatchInfo dispatchInfo, int componentIndex) {
        return markComponentSeen(dispatchInfo.seenPropertiesMask(), dispatchInfo.seenPropertyVariables(), componentIndex);
    }

    private StatementDef markComponentSeen(VariableDef.@Nullable Local seenPropertiesMask,
                                           List<VariableDef.Local> seenPropertyVariables,
                                           int componentIndex) {
        if (seenPropertiesMask != null) {
            return seenPropertiesMask.assign(
                seenPropertiesMask.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_OR, seenComponentMask(componentIndex))
            );
        }
        return seenPropertyVariables.get(componentIndex).assign(ExpressionDef.trueValue());
    }

    private ExpressionDef.Constant seenComponentMask(int componentIndex) {
        return ExpressionDef.constant(1L << componentIndex);
    }

    private StatementDef unknownPropertyStatement(ExpressionDef ignoreUnknownExpression,
                                                  VariableDef objectDecoder,
                                                  ExpressionDef propertyArgumentExpression,
                                                  VariableDef.MethodParameter type) {
        return ignoreUnknownExpression.ifTrue(
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
        return StatementDef.doTry(deserializeComponentDirect(
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
            dispatchResultVariable
        ))
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

    @SuppressWarnings("java:S107")
    private StatementDef deserializeComponentDirect(VariableDef.This aThis,
                                                   ClassTypeDef deserializerClassTypeDef,
                                                   VariableDef objectDecoder,
                                                   VariableDef.MethodParameter context,
                                                   VariableDef.MethodParameter type,
                                                   VariableDef valueVariable,
                                                   RecordSerdeShape.RecordComponent component,
                                                   int index,
                                                   Map<String, String> argumentFieldNames,
                                                   Map<String, String> deserializerFieldNames) {
        return deserializeComponentDirect(
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
        );
    }

    @SuppressWarnings("java:S107")
    private StatementDef deserializeComponentDirect(VariableDef.This aThis,
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
        boolean usesNonNullScalarDecode = false;
        if (scalarDecodeMethod != null) {
            if (component.type().isPrimitive() && !component.type().isArray()) {
                scalarDecodeMethod = Objects.requireNonNull(scalarDecoderMethod(component.type(), false));
                StatementDef keepDefaultOnNullStatement;
                if (useNullableScalarDecodeForDefaultPrimitive(component.type())) {
                    Method nullableScalarDecodeMethod = Objects.requireNonNull(nullableScalarDecoderMethod(component.type()));
                    StatementDef.DefineAndAssign nullableValueDef = objectDecoder.invoke(nullableScalarDecodeMethod)
                        .cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()))
                        .newLocal(RecordSerdeSourceGenUtils.localName("decodedValue", index));
                    keepDefaultOnNullStatement = StatementDef.multi(
                        nullableValueDef,
                        nullableValueDef.variable().isNonNull()
                            .doIf(valueVariable.assign(nullableValueDef.variable()))
                    );
                } else {
                    keepDefaultOnNullStatement = objectDecoder.invoke(DECODE_NULL_METHOD)
                        .ifFalse(valueVariable.assign(objectDecoder.invoke(scalarDecodeMethod)));
                }
                deserializeAndAssign = aThis.field(FAIL_ON_NULL_FOR_PRIMITIVES_FIELD, BOOLEAN_TYPE).ifTrue(
                    deserializePrimitiveComponentFailOnNull(
                        objectDecoder,
                        valueVariable,
                        scalarDecodeMethod
                    ),
                    keepDefaultOnNullStatement
                );
            } else {
                Method nonNullScalarDecodeMethod = nonNullScalarDecoderMethod(component.type());
                if (component.propertyElement().isNonNull()
                    && !component.propertyElement().isNullable()
                    && nonNullScalarDecodeMethod != null) {
                    scalarDecodeMethod = nonNullScalarDecodeMethod;
                    usesNonNullScalarDecode = true;
                }
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
            && !component.propertyElement().isNullable()
            && !usesNonNullScalarDecode) {
            deserializeAndAssign = StatementDef.multi(
                deserializeAndAssign,
                valueVariable.isNull().ifTrue(
                    nullValueOrDispatchStatement(type, argumentExpression, dispatchResultVariable)
                )
            );
        }
        return deserializeAndAssign;
    }

    private boolean useNullableScalarDecodeForDefaultPrimitive(ClassElement type) {
        return "long".equals(type.getName());
    }

    private StatementDef deserializePrimitiveComponentFailOnNull(VariableDef objectDecoder,
                                                                 VariableDef valueVariable,
                                                                 Method scalarDecodeMethod) {
        return valueVariable.assign(objectDecoder.invoke(scalarDecodeMethod));
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

    private StatementDef wrapWithPropertyPath(StatementDef statement,
                                              VariableDef.MethodParameter type,
                                              ExpressionDef argumentExpression) {
        return StatementDef.doTry(statement)
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

    private String indexedName(String prefix, int index) {
        return prefix + "_" + index;
    }

    private ExpressionDef keysCreateExpression(ClassTypeDef deserializerClassTypeDef,
                                               List<RecordSerdeShape.RecordComponent> components,
                                               List<String> keyFieldNames) {
        List<ExpressionDef> keyExpressions = keyFieldNames.stream()
            .map(keyFieldName -> (ExpressionDef) deserializerClassTypeDef.getStaticField(keyFieldName, STRING_TYPE))
            .toList();
        if (components.stream().noneMatch(component -> !component.keyMetadata().isEmpty())) {
            return KEYS_TYPE.invokeStatic(
                KEYS_CREATE_METHOD,
                STRING_TYPE.array().instantiate(keyExpressions)
            );
        }
        List<ExpressionDef> descriptorExpressions = new ArrayList<>(components.size());
        for (int i = 0; i < components.size(); i++) {
            List<ExpressionDef> metadataExpressions = components.get(i).keyMetadata().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> Stream.<ExpressionDef>of(
                    RecordSerdeSourceGenUtils.keyMetadataPropertyExpression(entry.getKey()),
                    ExpressionDef.constant(entry.getValue())
                ))
                .toList();
            descriptorExpressions.add(metadataExpressions.isEmpty()
                ? KEY_DESCRIPTOR_TYPE.instantiate(keyExpressions.get(i))
                : KEY_DESCRIPTOR_TYPE.invokeStatic(
                    KEY_DESCRIPTOR_CREATE_METHOD,
                    keyExpressions.get(i),
                    STRING_TYPE.array().instantiate(metadataExpressions)
                ));
        }
        return KEYS_TYPE.invokeStatic(
            KEYS_CREATE_WITH_METADATA_METHOD,
            KEY_DESCRIPTOR_TYPE.array().instantiate(descriptorExpressions)
        );
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

    private @Nullable StatementDef strictNullableStatement(VariableDef.This aThis,
                                                           ClassTypeDef deserializerClassTypeDef,
                                                           VariableDef.MethodParameter type,
                                                           RecordDispatchInfo dispatchInfo) {
        long strictNullableMask = strictNullableMask(dispatchInfo.components());
        if (strictNullableMask == 0L) {
            return null;
        }
        List<StatementDef> strictNullableStatements = new ArrayList<>();
        if (dispatchInfo.seenPropertiesMask() != null) {
            strictNullableStatements.add(
                dispatchInfo.seenPropertiesMask()
                    .math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_AND, ExpressionDef.constant(strictNullableMask))
                    .compare(ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO, ExpressionDef.constant(strictNullableMask))
                    .ifTrue(missingStrictNullablePropertyStatement(
                        deserializerClassTypeDef,
                        type,
                        dispatchInfo
                    ))
            );
        } else {
            strictNullableStatements.add(missingStrictNullablePropertyStatement(
                deserializerClassTypeDef,
                type,
                dispatchInfo
            ));
        }
        return StatementDef.multi(aThis.field(STRICT_NULLABLE_FIELD, BOOLEAN_TYPE).ifTrue(
            StatementDef.multi(strictNullableStatements)
        ));
    }

    private StatementDef missingStrictNullablePropertyStatement(ClassTypeDef deserializerClassTypeDef,
                                                               VariableDef.MethodParameter type,
                                                               RecordDispatchInfo dispatchInfo) {
        List<StatementDef> statements = new ArrayList<>();
        for (int i = 0; i < dispatchInfo.components().size(); i++) {
            RecordSerdeShape.RecordComponent component = dispatchInfo.components().get(i);
            if (requiresStrictNullableCheck(component)) {
                ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(
                    required(dispatchInfo.argumentFieldNames(), component.name()),
                    ARGUMENT_TYPE
                );
                StatementDef missingPropertyStatement = ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                    .invokeStatic(
                        STRICT_NULLABLE_CONSTRUCTOR_PARAMETER_METHOD,
                        type,
                        argumentExpression
                    )
                    .doThrow();
                if (dispatchInfo.seenPropertiesMask() != null) {
                    statements.add(dispatchInfo.seenPropertiesMask()
                        .math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_AND, seenComponentMask(i))
                        .compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, ExpressionDef.constant(0L))
                        .ifTrue(missingPropertyStatement));
                } else {
                    statements.add(isComponentSeen(dispatchInfo, i).doIfElse(
                        StatementDef.multi(),
                        missingPropertyStatement
                    ));
                }
            }
        }
        return StatementDef.multi(statements);
    }

    private long strictNullableMask(List<RecordSerdeShape.RecordComponent> components) {
        long mask = 0L;
        for (int i = 0; i < components.size(); i++) {
            if (requiresStrictNullableCheck(components.get(i))) {
                mask |= 1L << i;
            }
        }
        return mask;
    }

    private boolean hasSmallSeenPropertiesMask(List<RecordSerdeShape.RecordComponent> components) {
        return !components.isEmpty() && components.size() <= Long.SIZE;
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

    private boolean isSelfReferentialComponent(ClassElement element,
                                               RecordSerdeShape recordSerdeShape,
                                               String componentName) {
        for (RecordSerdeShape.RecordComponent component : recordSerdeShape.components()) {
            if (component.name().equals(componentName) && component.type().getName().equals(element.getName())) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Method scalarDecoderMethod(ClassElement type) {
        return scalarDecoderMethod(type, true);
    }

    private @Nullable Method nullableScalarDecoderMethod(ClassElement type) {
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

    private @Nullable Method nonNullScalarDecoderMethod(ClassElement type) {
        if (type.isArray()) {
            return null;
        }
        return switch (type.getName()) {
            case "java.lang.String" -> DECODE_STRING_METHOD;
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
                                      VariableDef.@Nullable Local seenPropertiesMask,
                                      List<VariableDef.Local> seenPropertyVariables,
                                      List<VariableDef> valueVariables,
                                      List<StatementDef> componentDeserializers) {
    }
}
