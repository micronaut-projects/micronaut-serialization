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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.KeyDescriptor;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Generates optimized source serializers for records.
 */
public final class RecordSerializerSourceGen {
    private static final String CONTEXT_PARAMETER = "context";
    private static final String VALUE_PARAMETER = "value";
    private static final String VALUE_LOCAL_PREFIX = "value";
    private static final String GENERATED_VALUE_MEMBER = "value";
    private static final String KEYS_FIELD = "KEYS";

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef SERIALIZER_TYPE = TypeDef.of(Serializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final ClassTypeDef KEYS_TYPE = ClassTypeDef.of(Keys.class);
    private static final ClassTypeDef KEY_DESCRIPTOR_TYPE = ClassTypeDef.of(KeyDescriptor.class);
    private static final ClassTypeDef KEYS_AWARE_ENCODER_TYPE = ClassTypeDef.of(KeysAwareEncoder.class);

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
    private static final Method WITH_RUNTIME_FALLBACK_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeFallbackUtil.class,
        "withRuntimeObjectFallback",
        Serializer.class,
        Serializer.EncoderContext.class,
        Argument.class
    );
    private static final Method KEYS_CREATE_METHOD = ReflectionUtils.getRequiredMethod(Keys.class, "create", String[].class);
    private static final Method KEYS_CREATE_WITH_METADATA_METHOD = ReflectionUtils.getRequiredMethod(Keys.class, "createWithMetadata", KeyDescriptor[].class);
    private static final Method KEY_DESCRIPTOR_CREATE_METHOD = ReflectionUtils.getRequiredMethod(KeyDescriptor.class, "create", String.class, String[].class);
    private static final Method ENCODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeObject", Argument.class);
    private static final Method KEYS_AWARE_ENCODER_OF_METHOD = ReflectionUtils.getRequiredMethod(KeysAwareEncoder.class, "of", Encoder.class);
    private static final Method ENCODE_KEY_KEYS_METHOD = ReflectionUtils.getRequiredMethod(KeysAwareEncoder.class, "encodeKey", Keys.class, int.class);
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
        GeneratedSerdeExceptionUtil.class,
        "withPropertyPath",
        Throwable.class,
        Argument.class,
        Argument.class
    );

    public ClassDef generate(ClassElement element, RecordSerdeShape recordSerdeShape) {
        recordSerdeShape = new RecordSerdeShape(
            recordSerdeShape.canonicalConstructor(),
            recordSerdeShape.components().stream()
                .sorted((left, right) -> Boolean.compare(isXmlAttribute(right), isXmlAttribute(left)))
                .toList()
        );
        TypeDef recordTypeDef = TypeDef.of(element);
        ClassTypeDef serializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedSerializerClassName(element));
        Map<String, String> keyFieldNames = new LinkedHashMap<>();
        Map<String, String> argumentFieldNames = new LinkedHashMap<>();
        Map<String, String> serializerFieldNames = new LinkedHashMap<>();
        List<FieldDef> fields = new ArrayList<>();

        int index = 0;
        for (RecordSerdeShape.RecordComponent component : recordSerdeShape.components()) {
            String keyFieldName = indexedName("KEY", index);
            String argumentFieldName = indexedName("ARGUMENT", index);
            keyFieldNames.put(component.name(), keyFieldName);
            argumentFieldNames.put(component.name(), argumentFieldName);

            fields.add(FieldDef.builder(keyFieldName, STRING_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(component.serializedName()))
                .build());
            fields.add(FieldDef.builder(argumentFieldName, ARGUMENT_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(RecordSerdeSourceGenUtils.argumentExpression(
                    component.type(),
                    serializerClassTypeDef.getStaticField(keyFieldName, STRING_TYPE)
                ))
                .build());
            if (scalarEncoderMethod(component.type()) == null) {
                String serializerFieldName = RecordSerdeSourceGenUtils.localName("serializer", index);
                serializerFieldNames.put(component.name(), serializerFieldName);
                fields.add(FieldDef.builder(serializerFieldName, SERIALIZER_TYPE)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build());
            }
            index++;
        }
        if (!keyFieldNames.isEmpty()) {
            fields.add(FieldDef.builder(KEYS_FIELD, KEYS_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(keysCreateExpression(serializerClassTypeDef, recordSerdeShape.components(), new ArrayList<>(keyFieldNames.values())))
                .build());
        }
        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Prototype.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember(GENERATED_VALUE_MEMBER, "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Serializer.class, recordTypeDef))
            .addSuperinterface(TypeDef.parameterized(ObjectSerializer.class, recordTypeDef))
            .addFields(fields)
            .addMethod(generateCreateSpecificMethod(recordTypeDef))
            .addMethod(generateSerializeMethod(recordTypeDef))
            .addMethod(generateSerializeIntoMethod(recordTypeDef, serializerClassTypeDef, recordSerdeShape, argumentFieldNames, serializerFieldNames));
        if (!serializerFieldNames.isEmpty()) {
            classDefBuilder.addMethod(generateConstructor(
                element,
                recordTypeDef,
                serializerClassTypeDef,
                recordSerdeShape,
                argumentFieldNames,
                serializerFieldNames
            ));
        }

        return classDefBuilder.build();
    }

    private MethodDef generateConstructor(ClassElement element,
                                          TypeDef recordTypeDef,
                                          ClassTypeDef serializerClassTypeDef,
                                          RecordSerdeShape recordSerdeShape,
                                          Map<String, String> argumentFieldNames,
                                          Map<String, String> serializerFieldNames) {
        MethodDef.MethodDefBuilder constructorBuilder = MethodDef.constructor()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(parameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class)))
            .addParameter(parameter("type", TypeDef.parameterized(Argument.class, TypeDef.wildcardSubtypeOf(recordTypeDef))))
            .addThrows(TypeDef.of(SerdeException.class));
        if (serializerFieldNames.isEmpty()) {
            return constructorBuilder.build();
        }
        return constructorBuilder.build((aThis, methodParameters) -> {
            List<StatementDef> statements = new ArrayList<>();
            VariableDef.MethodParameter context = methodParameters.get(0);
            for (Map.Entry<String, String> serializerFieldEntry : serializerFieldNames.entrySet()) {
                String componentName = serializerFieldEntry.getKey();
                String serializerFieldName = serializerFieldEntry.getValue();
                ExpressionDef argumentExpression = serializerClassTypeDef.getStaticField(required(argumentFieldNames, componentName), ARGUMENT_TYPE);
                ExpressionDef serializerExpression = isSelfReferentialComponent(element, recordSerdeShape, componentName)
                    ? ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                        .invokeStatic(WITH_RUNTIME_FALLBACK_SERIALIZER_METHOD, aThis, context, argumentExpression)
                    : context.invoke(FIND_SERIALIZER_METHOD, argumentExpression)
                        .invoke(CREATE_SPECIFIC_SERIALIZER_METHOD, context, argumentExpression);
                statements.add(aThis.field(serializerFieldName, SERIALIZER_TYPE).put(
                    serializerExpression
                ));
            }
            return StatementDef.multi(statements);
        });
    }

    private MethodDef generateCreateSpecificMethod(TypeDef recordTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Serializer.class, recordTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.parameterized(Argument.class, TypeDef.wildcardSubtypeOf(recordTypeDef)))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter context = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(1);
                return ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                    .invokeStatic(
                        WITH_RUNTIME_FALLBACK_SERIALIZER_METHOD,
                        aThis,
                        context,
                        type
                    )
                    .cast(TypeDef.parameterized(Serializer.class, recordTypeDef))
                    .returning();
            });
    }

    private MethodDef generateSerializeMethod(TypeDef recordTypeDef) {
        return MethodDef.builder("serialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter(VALUE_PARAMETER, recordTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);
                VariableDef.MethodParameter value = methodParameters.get(3);

                List<StatementDef> objectStatements = new ArrayList<>();
                StatementDef.DefineAndAssign objectEncoderDef = encoder.invoke(ENCODE_OBJECT_METHOD, type).newLocal("objectEncoder");
                objectStatements.add(objectEncoderDef);
                objectStatements.add(aThis.invoke("serializeInto", TypeDef.VOID, objectEncoderDef.variable(), context, type, value));
                objectStatements.add(objectEncoderDef.variable().invoke(FINISH_STRUCTURE_METHOD));

                return StatementDef.multi(objectStatements);
            });
    }

    private MethodDef generateSerializeIntoMethod(TypeDef recordTypeDef,
                                                  ClassTypeDef serializerClassTypeDef,
                                                  RecordSerdeShape recordSerdeShape,
                                                  Map<String, String> argumentFieldNames,
                                                  Map<String, String> serializerFieldNames) {
        return MethodDef.builder("serializeInto")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter(VALUE_PARAMETER, recordTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);
                VariableDef.MethodParameter value = methodParameters.get(3);
                if (recordSerdeShape.components().isEmpty()) {
                    return StatementDef.multi(serializeIntoStatements(
                        aThis,
                        serializerClassTypeDef,
                        encoder,
                        encoder,
                        context,
                        type,
                        value,
                        recordSerdeShape,
                        argumentFieldNames,
                        serializerFieldNames
                    ));
                }
                return KEYS_AWARE_ENCODER_TYPE.invokeStatic(KEYS_AWARE_ENCODER_OF_METHOD, encoder)
                    .newLocal("keysAwareEncoder", keysAwareEncoder -> StatementDef.multi(serializeIntoStatements(
                            aThis,
                            serializerClassTypeDef,
                            encoder,
                            keysAwareEncoder,
                            context,
                            type,
                            value,
                            recordSerdeShape,
                            argumentFieldNames,
                            serializerFieldNames
                        ))
                    );
            });
    }

    @SuppressWarnings("java:S107")
    private List<StatementDef> serializeIntoStatements(VariableDef.This aThis,
                                                       ClassTypeDef serializerClassTypeDef,
                                                       VariableDef valueEncoder,
                                                       VariableDef keyEncoder,
                                                       VariableDef.MethodParameter context,
                                                       VariableDef.MethodParameter type,
                                                       VariableDef.MethodParameter value,
                                                       RecordSerdeShape recordSerdeShape,
                                                       Map<String, String> argumentFieldNames,
                                                       Map<String, String> serializerFieldNames) {
        List<StatementDef> statements = new ArrayList<>();
        int index = 0;
        for (RecordSerdeShape.RecordComponent component : recordSerdeShape.components()) {
            statements.add(encodeKeyStatement(serializerClassTypeDef, keyEncoder, index));
            statements.add(serializeComponent(aThis, serializerClassTypeDef, valueEncoder, context, type, value, component, index++, argumentFieldNames, serializerFieldNames));
        }
        return statements;
    }

    private StatementDef encodeKeyStatement(ClassTypeDef serializerClassTypeDef,
                                            VariableDef encoder,
                                            int index) {
        return encoder.invoke(
            ENCODE_KEY_KEYS_METHOD,
            serializerClassTypeDef.getStaticField(KEYS_FIELD, KEYS_TYPE),
            ExpressionDef.constant(index)
        );
    }

    @SuppressWarnings("java:S107")
    private StatementDef serializeComponent(VariableDef.This aThis,
                                            ClassTypeDef serializerClassTypeDef,
                                            VariableDef objectEncoder,
                                            VariableDef.MethodParameter context,
                                            VariableDef.MethodParameter type,
                                            VariableDef.MethodParameter value,
                                            RecordSerdeShape.RecordComponent component,
                                            int index,
                                            Map<String, String> argumentFieldNames,
                                            Map<String, String> serializerFieldNames) {
        ExpressionDef argumentExpression = serializerClassTypeDef.getStaticField(required(argumentFieldNames, component.name()), ARGUMENT_TYPE);
        Method scalarMethod = scalarEncoderMethod(component.type());
        ExpressionDef propertyValue = value.getPropertyValue(component.propertyElement());
        if (scalarMethod != null) {
            StatementDef scalarStatement;
            if (component.type().isPrimitive() && !component.type().isArray()) {
                scalarStatement = objectEncoder.invoke(scalarMethod, propertyValue);
            } else {
                StatementDef.DefineAndAssign propertyValueDef = propertyValue.newLocal(RecordSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
                scalarStatement = StatementDef.multi(
                    propertyValueDef,
                    propertyValueDef.variable().isNull().ifTrue(
                        objectEncoder.invoke(ENCODE_NULL_METHOD),
                        StatementDef.multi(objectEncoder.invoke(scalarMethod, propertyValueDef.variable()))
                    )
                );
            }
            return wrapWithPropertyPath(scalarStatement, type, argumentExpression);
        }
        String serializerFieldName = required(serializerFieldNames, component.name());
        ExpressionDef serializer = aThis.field(serializerFieldName, SERIALIZER_TYPE);

        StatementDef serializeStatement = serializer.invoke(
            SERIALIZE_METHOD,
            objectEncoder,
            context,
            argumentExpression,
            propertyValue.cast(TypeDef.OBJECT)
        );
        if (component.type().isPrimitive() && !component.type().isArray()) {
            return wrapWithPropertyPath(serializeStatement, type, argumentExpression);
        }
        StatementDef.DefineAndAssign propertyValueDef = propertyValue.newLocal(RecordSerdeSourceGenUtils.localName(VALUE_LOCAL_PREFIX, index));
        return wrapWithPropertyPath(StatementDef.multi(
            propertyValueDef,
            propertyValueDef.variable().isNull().ifTrue(
                objectEncoder.invoke(ENCODE_NULL_METHOD),
                StatementDef.multi(
                    serializer.invoke(
                        SERIALIZE_METHOD,
                        objectEncoder,
                        context,
                        argumentExpression,
                        propertyValueDef.variable().cast(TypeDef.OBJECT)
                    )
                )
            )
        ), type, argumentExpression);
    }

    private String indexedName(String prefix, int index) {
        return prefix + "_" + index;
    }

    private ExpressionDef keysCreateExpression(ClassTypeDef serializerClassTypeDef,
                                               List<RecordSerdeShape.RecordComponent> components,
                                               List<String> keyFieldNames) {
        List<ExpressionDef> keyExpressions = keyFieldNames.stream()
            .map(keyFieldName -> (ExpressionDef) serializerClassTypeDef.getStaticField(keyFieldName, STRING_TYPE))
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

    private static boolean isXmlAttribute(RecordSerdeShape.RecordComponent component) {
        return Boolean.parseBoolean(component.keyMetadata().get(SerdeConfig.XML_ATTRIBUTE_PROPERTY));
    }

    private static String required(Map<String, String> names, String key) {
        return Objects.requireNonNull(names.get(key));
    }

    private @Nullable Method scalarEncoderMethod(ClassElement type) {
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

    private ParameterDef parameter(String name, TypeDef type) {
        return ParameterDef.builder(name, type)
            .addAnnotation(Parameter.class)
            .build();
    }
}
