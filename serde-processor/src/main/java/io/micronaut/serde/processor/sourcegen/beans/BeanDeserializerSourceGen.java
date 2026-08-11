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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
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
 * Generates optimized source deserializers for bean types.
 */
public final class BeanDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef BOOLEAN_TYPE = TypeDef.primitive(boolean.class);
    private static final TypeDef.Primitive INT_TYPE = TypeDef.Primitive.INT;
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final ClassTypeDef KEYS_TYPE = ClassTypeDef.of(Keys.class);
    private static final ClassTypeDef KEY_DESCRIPTOR_TYPE = ClassTypeDef.of(KeyDescriptor.class);
    private static final ClassTypeDef KEYS_AWARE_DECODER_TYPE = ClassTypeDef.of(KeysAwareDecoder.class);
    private static final ClassTypeDef DISPATCH_RESULT_TYPE = ClassTypeDef.of(GeneratedSerdeExceptionUtil.PropertyDispatchResult.class);
    private static final String CONTEXT_PARAMETER = "context";
    private static final String VALUE_LOCAL_PREFIX = "value";
    private static final String BEAN_LOCAL = "bean";
    private static final String KEYS_FIELD = "KEYS";
    private static final String FAIL_ON_NULL_FOR_PRIMITIVES_FIELD = "failOnNullForPrimitives";
    private static final String IGNORE_UNKNOWN_FIELD = "ignoreUnknown";
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

    private static String required(Map<String, String> names, String key) {
        return Objects.requireNonNull(names.get(key));
    }

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
        boolean fieldAccessProperties = false;
        boolean failOnNullForPrimitives = requiresFailOnNullForPrimitives(beanSerdeShape);

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
                .initializer(BeanSerdeSourceGenUtils.argumentExpression(
                    lookupType,
                    deserializerClassTypeDef.getStaticField(keyFieldName, STRING_TYPE)
                ))
                .build());
            if (scalarDecoderMethod(property.deserializationType()) == null) {
                String deserializerFieldName = BeanSerdeSourceGenUtils.localName("deserializer", index);
                deserializerFieldNames.put(property.name(), deserializerFieldName);
                fields.add(FieldDef.builder(deserializerFieldName, DESERIALIZER_TYPE)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            }
            if (property.writeField() != null) {
                fieldAccessProperties = true;
            }
            index++;
        }
        if (!keyFieldNames.isEmpty()) {
            fields.add(FieldDef.builder(KEYS_FIELD, KEYS_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(keysCreateExpression(deserializerClassTypeDef, beanSerdeShape.properties(), new ArrayList<>(keyFieldNames.values())))
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

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Prototype.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember(GENERATED_VALUE_MEMBER, "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, beanTypeDef))
            .addFields(fields)
            .addMethod(generateCreateSpecificMethod(beanTypeDef))
            .addMethod(generateDeserializeMethod(
                "deserialize",
                Modifier.PUBLIC,
                element,
                beanTypeDef,
                deserializerClassTypeDef,
                beanSerdeShape,
                keyFieldNames,
                argumentFieldNames,
                deserializerFieldNames,
                PrimitiveNullMode.DYNAMIC
            ));
        classDefBuilder.addMethod(generateConstructor(
            element,
            deserializerClassTypeDef,
            beanSerdeShape,
            argumentFieldNames,
            deserializerFieldNames,
            failOnNullForPrimitives
        ));

        List<Object> suppressWarnings = new ArrayList<>();
        if (fieldAccessProperties) {
            suppressWarnings.add("UnnecessaryParentheses");
        }
        if (!suppressWarnings.isEmpty()) {
            classDefBuilder.addAnnotation(AnnotationDef.builder(SuppressWarnings.class)
                .addMember(GENERATED_VALUE_MEMBER, suppressWarnings)
                .build());
        }
        if (fieldAccessProperties) {
            classDefBuilder.addAnnotation(Secondary.class);
        }
        return classDefBuilder.build();
    }

    private MethodDef generateConstructor(ClassElement element,
                                          ClassTypeDef deserializerClassTypeDef,
                                          BeanSerdeShape beanSerdeShape,
                                          Map<String, String> argumentFieldNames,
                                          Map<String, String> deserializerFieldNames,
                                          boolean failOnNullForPrimitives) {
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
            for (Map.Entry<String, String> deserializerFieldEntry : deserializerFieldNames.entrySet()) {
                String propertyName = deserializerFieldEntry.getKey();
                String deserializerFieldName = deserializerFieldEntry.getValue();
                ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, propertyName), ARGUMENT_TYPE);
                ExpressionDef deserializerExpression = isSelfReferentialProperty(element, beanSerdeShape, propertyName)
                    ? ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                        .invokeStatic(WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD, aThis, context, argumentExpression)
                    : context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression);
                statements.add(aThis.field(deserializerFieldName, DESERIALIZER_TYPE).put(deserializerExpression));
            }
            return StatementDef.multi(statements);
        });
    }

    private MethodDef generateCreateSpecificMethod(TypeDef beanTypeDef) {
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
                return ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                    .invokeStatic(
                        WITH_RUNTIME_FALLBACK_DESERIALIZER_METHOD,
                        aThis,
                        context,
                        type
                    )
                    .cast(TypeDef.parameterized(Deserializer.class, beanTypeDef))
                    .returning();
            });
    }

    private MethodDef generateDeserializeMethod(String methodName,
                                                Modifier modifier,
                                                ClassElement element,
                                                TypeDef beanTypeDef,
                                                ClassTypeDef deserializerClassTypeDef,
                                                BeanSerdeShape beanSerdeShape,
                                                Map<String, String> keyFieldNames,
                                                Map<String, String> argumentFieldNames,
                                                Map<String, String> deserializerFieldNames,
                                                PrimitiveNullMode primitiveNullMode) {
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(methodName)
            .addModifiers(modifier)
            .returns(beanTypeDef)
            .addParameter("decoder", TypeDef.of(Decoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(IOException.class));
        if (modifier == Modifier.PUBLIC) {
            methodBuilder.overrides();
        }
        return methodBuilder.build((aThis, methodParameters) -> {
                return buildDeserializeBody(
                    aThis,
                    methodParameters.get(0),
                    methodParameters.get(1),
                    methodParameters.get(2),
                    element,
                    deserializerClassTypeDef,
                    beanSerdeShape,
                    keyFieldNames,
                    argumentFieldNames,
                    deserializerFieldNames,
                    primitiveNullMode
                );
            });
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildDeserializeBody(VariableDef.This aThis,
                                              VariableDef.MethodParameter decoder,
                                              VariableDef.MethodParameter context,
                                              VariableDef.MethodParameter type,
                                              ClassElement element,
                                              ClassTypeDef deserializerClassTypeDef,
                                              BeanSerdeShape beanSerdeShape,
                                              Map<String, String> keyFieldNames,
                                              Map<String, String> argumentFieldNames,
                                              Map<String, String> deserializerFieldNames,
                                              PrimitiveNullMode primitiveNullMode) {
        List<StatementDef> statements = new ArrayList<>();
        StatementDef.DefineAndAssign objectDecoderDef = decoder.invoke(DECODE_OBJECT_METHOD, type).newLocal("objectDecoder");
        statements.add(objectDecoderDef);
        VariableDef objectDecoder = objectDecoderDef.variable();

        StatementDef.DefineAndAssign beanDef = ClassTypeDef.of(element).instantiate().newLocal(BEAN_LOCAL);
        statements.add(beanDef);
        VariableDef beanVariable = beanDef.variable();

        ExpressionDef ignoreUnknownExpression = aThis.field(IGNORE_UNKNOWN_FIELD, BOOLEAN_TYPE);

        List<BeanSerdeShape.BeanProperty> properties = beanSerdeShape.properties();
        List<VariableDef.Local> seenPropertyVariables = new ArrayList<>(properties.size());
        VariableDef.@Nullable Local seenPropertiesMaskVariable = null;
        boolean seenPropertiesMaskLong = false;
        if (!properties.isEmpty() && properties.size() <= Long.SIZE) {
            seenPropertiesMaskLong = true;
            StatementDef.DefineAndAssign seenPropertiesMaskDef = ExpressionDef.constant(0L)
                .newLocal("seenProperties");
            statements.add(seenPropertiesMaskDef);
            seenPropertiesMaskVariable = seenPropertiesMaskDef.variable();
        } else {
            for (int i = 0; i < properties.size(); i++) {
                StatementDef.DefineAndAssign seenPropertyDef = ExpressionDef.falseValue()
                    .newLocal(BeanSerdeSourceGenUtils.localName("seenProperty", i));
                statements.add(seenPropertyDef);
                seenPropertyVariables.add(seenPropertyDef.variable());
            }
        }
        VariableDef.@Nullable Local seenPropertiesMask = seenPropertiesMaskVariable;
        boolean useLongSeenPropertiesMask = seenPropertiesMaskLong;
        for (BeanSerdeShape.BeanProperty property : properties) {
            if (!property.deserializationType().isOptional()) {
                continue;
            }
            statements.add(assignProperty(beanVariable, property, BeanSerdeSourceGenUtils.optionalDefaultValueExpression(property.deserializationType())));
        }

        StatementDef finishStatement = StatementDef.multi(
            objectDecoder.invoke(FINISH_STRUCTURE_METHOD),
            beanVariable.returning()
        );
        BeanDeserializeContext deserializeContext = new BeanDeserializeContext(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            context,
            type,
            beanVariable,
            primitiveNullMode
        );
        if (properties.isEmpty()) {
            BeanDispatchInfo dispatchInfo = buildBeanDispatchInfo(
                deserializeContext,
                beanSerdeShape,
                keyFieldNames,
                argumentFieldNames,
                deserializerFieldNames,
                seenPropertiesMask,
                useLongSeenPropertiesMask,
                seenPropertyVariables
            );
            statements.add(buildStringPropertyDispatchLoop(
                aThis,
                deserializerClassTypeDef,
                objectDecoder,
                context,
                type,
                beanVariable,
                ignoreUnknownExpression,
                dispatchInfo,
                finishStatement,
                primitiveNullMode
            ));
        } else {
            statements.add(KEYS_AWARE_DECODER_TYPE.invokeStatic(KEYS_AWARE_DECODER_OF_METHOD, objectDecoder)
                .newLocal("keysAwareDecoder", keysAwareDecoder -> {
                    BeanDispatchInfo dispatchInfo = buildBeanDispatchInfo(
                        deserializeContext,
                        beanSerdeShape,
                        keyFieldNames,
                        argumentFieldNames,
                        deserializerFieldNames,
                        seenPropertiesMask,
                        useLongSeenPropertiesMask,
                        seenPropertyVariables
                    );
                    return buildKeysAwarePropertyDispatchLoop(
                        aThis,
                        deserializerClassTypeDef,
                        objectDecoder,
                        keysAwareDecoder,
                        context,
                        type,
                        beanVariable,
                        ignoreUnknownExpression,
                        dispatchInfo,
                        finishStatement,
                        primitiveNullMode
                    );
                })
            );
        }

        return StatementDef.multi(statements);
    }

    private BeanDispatchInfo buildBeanDispatchInfo(BeanDeserializeContext deserializeContext,
                                                   BeanSerdeShape beanSerdeShape,
                                                   Map<String, String> keyFieldNames,
                                                   Map<String, String> argumentFieldNames,
                                                   Map<String, String> deserializerFieldNames,
                                                   VariableDef.@Nullable Local seenPropertiesMaskVariable,
                                                   boolean seenPropertiesMaskLong,
                                                   List<VariableDef.Local> seenPropertyVariables) {
        List<BeanSerdeShape.BeanProperty> properties = beanSerdeShape.properties();
        List<StatementDef> propertyDeserializers = new ArrayList<>(properties.size());
        for (int i = 0; i < properties.size(); i++) {
            BeanSerdeShape.BeanProperty property = properties.get(i);
            propertyDeserializers.add(deserializeAndAssignProperty(
                deserializeContext.aThis(),
                deserializeContext.deserializerClassTypeDef(),
                deserializeContext.objectDecoder(),
                deserializeContext.decoderContext(),
                deserializeContext.type(),
                deserializeContext.beanVariable(),
                property,
                i,
                argumentFieldNames,
                deserializerFieldNames,
                null,
                deserializeContext.primitiveNullMode()
            ));
        }
        return new BeanDispatchInfo(
            properties,
            keyFieldNames,
            argumentFieldNames,
            deserializerFieldNames,
            seenPropertiesMaskVariable,
            seenPropertiesMaskLong,
            seenPropertyVariables,
            propertyDeserializers
        );
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildStringPropertyDispatchLoop(VariableDef.This aThis,
                                                         ClassTypeDef deserializerClassTypeDef,
                                                         VariableDef objectDecoder,
                                                         VariableDef.MethodParameter context,
                                                         VariableDef.MethodParameter type,
                                                         VariableDef beanVariable,
                                                         ExpressionDef ignoreUnknownExpression,
                                                         BeanDispatchInfo dispatchInfo,
                                                         StatementDef finishStatement,
                                                         PrimitiveNullMode primitiveNullMode) {
        return ExpressionDef.trueValue().whileLoop(buildStringPropertyDispatchStep(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            context,
            type,
            beanVariable,
            ignoreUnknownExpression,
            dispatchInfo,
            finishStatement,
            primitiveNullMode
        ));
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildStringPropertyDispatchStep(VariableDef.This aThis,
                                                         ClassTypeDef deserializerClassTypeDef,
                                                         VariableDef objectDecoder,
                                                         VariableDef.MethodParameter context,
                                                         VariableDef.MethodParameter type,
                                                         VariableDef beanVariable,
                                                         ExpressionDef ignoreUnknownExpression,
                                                         BeanDispatchInfo dispatchInfo,
                                                         StatementDef finishStatement,
                                                         PrimitiveNullMode primitiveNullMode) {
        StatementDef.DefineAndAssign keyDef = objectDecoder.invoke(DECODE_KEY_METHOD).newLocal("key");
        VariableDef keyVariable = keyDef.variable();
        StatementDef switchStatement = buildPropertyDispatchStatement(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            context,
            type,
            beanVariable,
            keyVariable,
            ignoreUnknownExpression,
            dispatchInfo,
            primitiveNullMode
        );
        return StatementDef.multi(
            keyDef,
            keyVariable.isNull().ifTrue(finishStatement),
            switchStatement
        );
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildKeysAwarePropertyDispatchLoop(VariableDef.This aThis,
                                                            ClassTypeDef deserializerClassTypeDef,
                                                            VariableDef objectDecoder,
                                                            VariableDef keysAwareDecoder,
                                                            VariableDef.MethodParameter context,
                                                            VariableDef.MethodParameter type,
                                                            VariableDef beanVariable,
                                                            ExpressionDef ignoreUnknownExpression,
                                                            BeanDispatchInfo dispatchInfo,
                                                            StatementDef finishStatement,
                                                            PrimitiveNullMode primitiveNullMode) {
        ExpressionDef keyIndexExpression = keysAwareDecoder.invoke(
            DECODE_KEY_KEYS_METHOD,
            deserializerClassTypeDef.getStaticField(KEYS_FIELD, KEYS_TYPE)
        );
        List<StatementDef> loopStatements = new ArrayList<>();
        loopStatements.add(buildKeyIndexPropertyDispatchStatement(
            aThis,
            deserializerClassTypeDef,
            objectDecoder,
            keysAwareDecoder,
            context,
            type,
            beanVariable,
            keyIndexExpression,
            ignoreUnknownExpression,
            dispatchInfo,
            finishStatement,
            primitiveNullMode
        ));
        return ExpressionDef.trueValue().whileLoop(StatementDef.multi(loopStatements));
    }

    @SuppressWarnings("java:S107")
    private StatementDef buildKeyIndexPropertyDispatchStatement(VariableDef.This aThis,
                                                                ClassTypeDef deserializerClassTypeDef,
                                                                VariableDef objectDecoder,
                                                                VariableDef keysAwareDecoder,
                                                                VariableDef.MethodParameter context,
                                                                VariableDef.MethodParameter type,
                                                                VariableDef beanVariable,
                                                                ExpressionDef keyIndexExpression,
                                                                ExpressionDef ignoreUnknownExpression,
                                                                BeanDispatchInfo dispatchInfo,
                                                                StatementDef finishStatement,
                                                                PrimitiveNullMode primitiveNullMode) {
        Map<ExpressionDef.Constant, StatementDef> cases = buildKeyIndexLifecycleCases(
            keysAwareDecoder,
            ignoreUnknownExpression,
            type,
            finishStatement
        );
        for (int i = 0; i < dispatchInfo.properties().size(); i++) {
            BeanSerdeShape.BeanProperty property = dispatchInfo.properties().get(i);
            ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(dispatchInfo.argumentFieldNames(), property.name()), ARGUMENT_TYPE);
            StatementDef deserializeAndAssignProperty = wrapWithPropertyPath(deserializeAndAssignPropertyDirect(
                aThis,
                deserializerClassTypeDef,
                objectDecoder,
                context,
                type,
                beanVariable,
                property,
                i,
                dispatchInfo.argumentFieldNames(),
                dispatchInfo.deserializerFieldNames(),
                primitiveNullMode
            ), type, argumentExpression);
            if (dispatchInfo.seenPropertiesMask() != null) {
                deserializeAndAssignProperty = StatementDef.multi(
                    isPropertySeen(dispatchInfo, i).ifTrue(duplicatePropertyStatement(argumentExpression, type)),
                    markPropertySeen(dispatchInfo, i),
                    deserializeAndAssignProperty
                );
            } else {
                deserializeAndAssignProperty = isPropertySeen(dispatchInfo, i).doIfElse(
                    duplicatePropertyStatement(argumentExpression, type),
                    StatementDef.multi(
                        markPropertySeen(dispatchInfo, i),
                        deserializeAndAssignProperty
                    )
                );
            }
            cases.put(ExpressionDef.constant(i), deserializeAndAssignProperty);
        }
        return keyIndexExpression.asStatementSwitch(INT_TYPE, cases);
    }

    private Map<ExpressionDef.Constant, StatementDef> buildKeyIndexLifecycleCases(VariableDef keysAwareDecoder,
                                                                                  ExpressionDef ignoreUnknownExpression,
                                                                                  VariableDef.MethodParameter type,
                                                                                  StatementDef finishStatement) {
        Map<ExpressionDef.Constant, StatementDef> cases = new LinkedHashMap<>();
        cases.put(ExpressionDef.constant(KeysAwareDecoder.MATCH_END_OBJECT), finishStatement);
        cases.put(ExpressionDef.constant(KeysAwareDecoder.MATCH_UNKNOWN_NAME), buildUnknownKeyIndexPropertyDispatchStep(
            keysAwareDecoder,
            ignoreUnknownExpression,
            type,
            finishStatement
        ));
        return cases;
    }

    private StatementDef buildUnknownKeyIndexPropertyDispatchStep(VariableDef keysAwareDecoder,
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

    private StatementDef buildPropertyDispatchStatement(VariableDef.This aThis,
                                                        ClassTypeDef deserializerClassTypeDef,
                                                        VariableDef objectDecoder,
                                                        VariableDef.MethodParameter context,
                                                        VariableDef.MethodParameter type,
                                                        VariableDef beanVariable,
                                                        VariableDef keyVariable,
                                                        ExpressionDef ignoreUnknownExpression,
                                                        BeanDispatchInfo dispatchInfo,
                                                        PrimitiveNullMode primitiveNullMode) {
        StatementDef unknownPropertyStatement = unknownPropertyStatement(ignoreUnknownExpression, objectDecoder, dynamicPropertyArgument(keyVariable), type);
        if (dispatchInfo.properties().isEmpty()) {
            return unknownPropertyStatement;
        }
        return keyVariable.asExpressionSwitch(
            DISPATCH_RESULT_TYPE,
            buildSwitchCases(
                    new BeanDeserializeContext(
                        aThis,
                        deserializerClassTypeDef,
                        objectDecoder,
                        context,
                        type,
                        beanVariable,
                        primitiveNullMode
                    ),
                    dispatchInfo
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

    private Map<ExpressionDef.Constant, ExpressionDef> buildSwitchCases(BeanDeserializeContext deserializeContext,
                                                                        BeanDispatchInfo dispatchInfo) {
        Map<ExpressionDef.Constant, ExpressionDef> switchCases = new LinkedHashMap<>();
        List<BeanSerdeShape.BeanProperty> properties = dispatchInfo.properties();
        for (int i = 0; i < properties.size(); i++) {
            BeanSerdeShape.BeanProperty property = properties.get(i);
            StatementDef.DefineAndAssign dispatchResultDef = dispatchResult(HANDLED_DISPATCH_RESULT).newLocal("dispatchResult");
            VariableDef.Local dispatchResultVariable = dispatchResultDef.variable();
            switchCases.put(ExpressionDef.constant(property.name()),
                new ExpressionDef.SwitchYieldCase(
                    DISPATCH_RESULT_TYPE,
                    StatementDef.multi(
                        dispatchResultDef,
                        isPropertySeen(dispatchInfo.seenPropertiesMask(), dispatchInfo.seenPropertiesMaskLong(), dispatchInfo.seenPropertyVariables(), i).doIfElse(
                            dispatchResultVariable.assign(dispatchResult(DUPLICATE_DISPATCH_RESULT)),
                            StatementDef.multi(
                                markPropertySeen(dispatchInfo.seenPropertiesMask(), dispatchInfo.seenPropertiesMaskLong(), dispatchInfo.seenPropertyVariables(), i),
                                deserializeAndAssignProperty(
                                    deserializeContext.aThis(),
                                    deserializeContext.deserializerClassTypeDef(),
                                    deserializeContext.objectDecoder(),
                                    deserializeContext.decoderContext(),
                                    deserializeContext.type(),
                                    deserializeContext.beanVariable(),
                                    property,
                                    i,
                                    dispatchInfo.argumentFieldNames(),
                                    dispatchInfo.deserializerFieldNames(),
                                    dispatchResultVariable,
                                    deserializeContext.primitiveNullMode()
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

    private ExpressionDef.ConditionExpressionDef isPropertySeen(BeanDispatchInfo dispatchInfo, int propertyIndex) {
        return isPropertySeen(
            dispatchInfo.seenPropertiesMask(),
            dispatchInfo.seenPropertiesMaskLong(),
            dispatchInfo.seenPropertyVariables(),
            propertyIndex
        );
    }

    private ExpressionDef.ConditionExpressionDef isPropertySeen(VariableDef.@Nullable Local seenPropertiesMask,
                                                                boolean seenPropertiesMaskLong,
                                                                List<VariableDef.Local> seenPropertyVariables,
                                                                int propertyIndex) {
        if (seenPropertiesMask != null) {
            return seenPropertiesMask.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_AND, seenPropertyMask(propertyIndex, seenPropertiesMaskLong))
                .compare(ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO, seenPropertyZero(seenPropertiesMaskLong));
        }
        return seenPropertyVariables.get(propertyIndex).isTrue();
    }

    private StatementDef markPropertySeen(BeanDispatchInfo dispatchInfo, int propertyIndex) {
        return markPropertySeen(
            dispatchInfo.seenPropertiesMask(),
            dispatchInfo.seenPropertiesMaskLong(),
            dispatchInfo.seenPropertyVariables(),
            propertyIndex
        );
    }

    private StatementDef markPropertySeen(VariableDef.@Nullable Local seenPropertiesMask,
                                          boolean seenPropertiesMaskLong,
                                          List<VariableDef.Local> seenPropertyVariables,
                                          int propertyIndex) {
        if (seenPropertiesMask != null) {
            return seenPropertiesMask.assign(
                seenPropertiesMask.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_OR, seenPropertyMask(propertyIndex, seenPropertiesMaskLong))
            );
        }
        return seenPropertyVariables.get(propertyIndex).assign(ExpressionDef.trueValue());
    }

    private ExpressionDef.Constant seenPropertyMask(int propertyIndex, boolean seenPropertiesMaskLong) {
        return seenPropertiesMaskLong ? ExpressionDef.constant(1L << propertyIndex) : ExpressionDef.constant(1 << propertyIndex);
    }

    private ExpressionDef.Constant seenPropertyZero(boolean seenPropertiesMaskLong) {
        return seenPropertiesMaskLong ? ExpressionDef.constant(0L) : ExpressionDef.constant(0);
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
    private StatementDef deserializeAndAssignProperty(VariableDef.This aThis,
                                                      ClassTypeDef deserializerClassTypeDef,
                                                      VariableDef objectDecoder,
                                                      VariableDef.MethodParameter context,
                                                      VariableDef.MethodParameter type,
                                                      VariableDef beanVariable,
                                                      BeanSerdeShape.BeanProperty property,
                                                      int index,
                                                      Map<String, String> argumentFieldNames,
                                                      Map<String, String> deserializerFieldNames,
                                                      @Nullable VariableDef dispatchResultVariable,
                                                      PrimitiveNullMode primitiveNullMode) {
        ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, property.name()), ARGUMENT_TYPE);
        Method scalarDecodeMethod = scalarDecoderMethod(property.deserializationType());
        StatementDef deserializeAndAssign;
        if (scalarDecodeMethod != null && property.deserializationType().isPrimitive() && !property.deserializationType().isArray()) {
            deserializeAndAssign = deserializeAndAssignPrimitiveProperty(
                aThis,
                objectDecoder,
                beanVariable,
                property,
                index,
                primitiveNullMode
            );
        } else if (scalarDecodeMethod != null) {
            Method nonNullScalarDecodeMethod = nonNullScalarDecoderMethod(property.deserializationType());
            if (property.nonNull() && !property.nullable() && nonNullScalarDecodeMethod != null) {
                deserializeAndAssign = assignProperty(
                    beanVariable,
                    property,
                    objectDecoder.invoke(nonNullScalarDecodeMethod)
                );
            } else if (property.nonNull() && !property.nullable()) {
                StatementDef.DefineAndAssign decodedValueDef = objectDecoder.invoke(scalarDecodeMethod)
                    .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
                    .newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
                deserializeAndAssign = StatementDef.multi(
                    decodedValueDef,
                    decodedValueDef.variable().isNull().ifTrue(
                        nullValueOrDispatchStatement(type, argumentExpression, dispatchResultVariable),
                        assignProperty(beanVariable, property, decodedValueDef.variable())
                    )
                );
            } else {
                deserializeAndAssign = assignProperty(
                    beanVariable,
                    property,
                    objectDecoder.invoke(scalarDecodeMethod)
                        .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
                );
            }
        } else {
            String deserializerFieldName = required(deserializerFieldNames, property.name());
            StatementDef.DefineAndAssign deserializedValueDef = aThis.field(deserializerFieldName, DESERIALIZER_TYPE).invoke(
                DESERIALIZE_NULLABLE_METHOD,
                objectDecoder,
                context,
                argumentExpression
            ).cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType())).newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
            deserializeAndAssign = deserializedValueDef;
            StatementDef assignStatement = assignProperty(beanVariable, property, deserializedValueDef.variable());
            StatementDef propertyAssignment;
            if (property.nonNull() && !property.nullable()) {
                propertyAssignment = deserializedValueDef.variable().isNull().ifTrue(
                    nullValueOrDispatchStatement(type, argumentExpression, dispatchResultVariable),
                    assignStatement
                );
            } else {
                propertyAssignment = assignStatement;
            }
            deserializeAndAssign = StatementDef.multi(
                deserializeAndAssign,
                propertyAssignment
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

    @SuppressWarnings("java:S107")
    private StatementDef deserializeAndAssignPropertyDirect(VariableDef.This aThis,
                                                            ClassTypeDef deserializerClassTypeDef,
                                                            VariableDef objectDecoder,
                                                            VariableDef.MethodParameter context,
                                                            VariableDef.MethodParameter type,
                                                            VariableDef beanVariable,
                                                            BeanSerdeShape.BeanProperty property,
                                                            int index,
                                                            Map<String, String> argumentFieldNames,
                                                            Map<String, String> deserializerFieldNames,
                                                            PrimitiveNullMode primitiveNullMode) {
        ExpressionDef argumentExpression = deserializerClassTypeDef.getStaticField(required(argumentFieldNames, property.name()), ARGUMENT_TYPE);
        Method scalarDecodeMethod = scalarDecoderMethod(property.deserializationType());
        if (scalarDecodeMethod != null && property.deserializationType().isPrimitive() && !property.deserializationType().isArray()) {
            return deserializeAndAssignPrimitiveProperty(
                aThis,
                objectDecoder,
                beanVariable,
                property,
                index,
                primitiveNullMode
            );
        }
        if (scalarDecodeMethod != null) {
            Method nonNullScalarDecodeMethod = nonNullScalarDecoderMethod(property.deserializationType());
            if (property.nonNull() && !property.nullable() && nonNullScalarDecodeMethod != null) {
                return assignProperty(
                    beanVariable,
                    property,
                    objectDecoder.invoke(nonNullScalarDecodeMethod)
                );
            }
            if (property.nonNull() && !property.nullable()) {
                StatementDef.DefineAndAssign decodedValueDef = objectDecoder.invoke(scalarDecodeMethod)
                    .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
                    .newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
                StatementDef assignStatement = assignProperty(beanVariable, property, decodedValueDef.variable());
                return StatementDef.multi(
                    decodedValueDef,
                    decodedValueDef.variable().isNull().ifTrue(
                        nullValueOrDispatchStatement(type, argumentExpression, null),
                        assignStatement
                    )
                );
            }
            return assignProperty(
                beanVariable,
                property,
                objectDecoder.invoke(scalarDecodeMethod)
                    .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
            );
        }
        String deserializerFieldName = required(deserializerFieldNames, property.name());
        StatementDef.DefineAndAssign deserializedValueDef = aThis.field(deserializerFieldName, DESERIALIZER_TYPE).invoke(
            DESERIALIZE_NULLABLE_METHOD,
            objectDecoder,
            context,
            argumentExpression
        ).cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType())).newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
        StatementDef assignStatement = assignProperty(beanVariable, property, deserializedValueDef.variable());
        if (property.nonNull() && !property.nullable()) {
            return StatementDef.multi(
                deserializedValueDef,
                deserializedValueDef.variable().isNull().ifTrue(
                    nullValueOrDispatchStatement(type, argumentExpression, null),
                    assignStatement
                )
            );
        }
        return StatementDef.multi(
            deserializedValueDef,
            assignStatement
        );
    }

    private StatementDef deserializeAndAssignPrimitiveProperty(VariableDef.This aThis,
                                                              VariableDef objectDecoder,
                                                              VariableDef beanVariable,
                                                              BeanSerdeShape.BeanProperty property,
                                                              int index,
                                                              PrimitiveNullMode primitiveNullMode) {
        Method scalarDecodeMethod = Objects.requireNonNull(scalarDecoderMethod(property.deserializationType(), false));
        StatementDef assignDecodedValueStatement = assignProperty(beanVariable, property, objectDecoder.invoke(scalarDecodeMethod));
        StatementDef keepDefaultOnNullStatement;
        if (useNullableScalarDecodeForDefaultPrimitive(property.deserializationType())) {
            Method nullableScalarDecodeMethod = Objects.requireNonNull(nullableScalarDecoderMethod(property.deserializationType()));
            StatementDef.DefineAndAssign nullableValueDef = objectDecoder.invoke(nullableScalarDecodeMethod)
                .cast(BeanSerdeSourceGenUtils.deserializedCastType(property.deserializationType()))
                .newLocal(BeanSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
            keepDefaultOnNullStatement = StatementDef.multi(
                nullableValueDef,
                nullableValueDef.variable().isNonNull()
                    .doIf(assignProperty(beanVariable, property, nullableValueDef.variable()))
            );
        } else {
            keepDefaultOnNullStatement = objectDecoder.invoke(DECODE_NULL_METHOD)
                .ifFalse(assignDecodedValueStatement);
        }
        return switch (primitiveNullMode) {
            case FAIL_ON_NULL -> deserializeAndAssignPrimitivePropertyFailOnNull(
                objectDecoder,
                beanVariable,
                property,
                scalarDecodeMethod
            );
            case KEEP_DEFAULT -> keepDefaultOnNullStatement;
            case DYNAMIC -> aThis.field(FAIL_ON_NULL_FOR_PRIMITIVES_FIELD, BOOLEAN_TYPE).ifTrue(
                deserializeAndAssignPrimitivePropertyFailOnNull(
                    objectDecoder,
                    beanVariable,
                    property,
                    scalarDecodeMethod
                ),
                keepDefaultOnNullStatement
            );
        };
    }

    private boolean useNullableScalarDecodeForDefaultPrimitive(ClassElement type) {
        return "long".equals(type.getName());
    }

    private StatementDef deserializeAndAssignPrimitivePropertyFailOnNull(VariableDef objectDecoder,
                                                                         VariableDef beanVariable,
                                                                         BeanSerdeShape.BeanProperty property,
                                                                         Method scalarDecodeMethod) {
        return assignProperty(beanVariable, property, objectDecoder.invoke(scalarDecodeMethod));
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

    private StatementDef assignProperty(VariableDef beanVariable, BeanSerdeShape.BeanProperty property, ExpressionDef value) {
        FieldElement writeField = property.writeField();
        if (writeField != null) {
            return directFieldAccess(BEAN_LOCAL, writeField).assign(value);
        }
        MethodElement writeMethod = Objects.requireNonNull(property.writeMethod());
        return beanVariable.invoke(writeMethod, value);
    }

    private VariableDef.Local directFieldAccess(String instanceName, FieldElement field) {
        return new VariableDef.Local(instanceName + "." + field.getName(), TypeDef.of(field.getType()));
    }

    private boolean requiresFailOnNullForPrimitives(BeanSerdeShape beanSerdeShape) {
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            if (property.deserializationType().isPrimitive() && !property.deserializationType().isArray()) {
                return true;
            }
        }
        return false;
    }

    private String indexedName(String prefix, int index) {
        return prefix + "_" + index;
    }

    private ExpressionDef keysCreateExpression(ClassTypeDef deserializerClassTypeDef,
                                               List<BeanSerdeShape.BeanProperty> properties,
                                               List<String> keyFieldNames) {
        List<ExpressionDef> keyExpressions = keyFieldNames.stream()
            .map(keyFieldName -> (ExpressionDef) deserializerClassTypeDef.getStaticField(keyFieldName, STRING_TYPE))
            .toList();
        if (properties.stream().noneMatch(property -> !property.keyMetadata().isEmpty())) {
            return KEYS_TYPE.invokeStatic(
                KEYS_CREATE_METHOD,
                STRING_TYPE.array().instantiate(keyExpressions)
            );
        }
        List<ExpressionDef> descriptorExpressions = new ArrayList<>(properties.size());
        for (int i = 0; i < properties.size(); i++) {
            List<ExpressionDef> metadataExpressions = properties.get(i).keyMetadata().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> Stream.<ExpressionDef>of(
                    BeanSerdeSourceGenUtils.keyMetadataPropertyExpression(entry.getKey()),
                    ExpressionDef.constant(entry.getValue())
                ))
                .toList();
            descriptorExpressions.add(KEY_DESCRIPTOR_TYPE.invokeStatic(
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

    private boolean isSelfReferentialProperty(ClassElement element,
                                             BeanSerdeShape beanSerdeShape,
                                             String propertyName) {
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            if (property.name().equals(propertyName) && property.deserializationType().getName().equals(element.getName())) {
                return true;
            }
        }
        return false;
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

    private enum PrimitiveNullMode {
        DYNAMIC,
        FAIL_ON_NULL,
        KEEP_DEFAULT
    }

    private record BeanDeserializeContext(VariableDef.This aThis,
                                          ClassTypeDef deserializerClassTypeDef,
                                          VariableDef objectDecoder,
                                          VariableDef.MethodParameter decoderContext,
                                          VariableDef.MethodParameter type,
                                          VariableDef beanVariable,
                                          PrimitiveNullMode primitiveNullMode) {
    }

    private record BeanDispatchInfo(List<BeanSerdeShape.BeanProperty> properties,
                                    Map<String, String> keyFieldNames,
                                    Map<String, String> argumentFieldNames,
                                    Map<String, String> deserializerFieldNames,
                                    VariableDef.@Nullable Local seenPropertiesMask,
                                    boolean seenPropertiesMaskLong,
                                    List<VariableDef.Local> seenPropertyVariables,
                                    List<StatementDef> propertyDeserializers) {
    }
}
