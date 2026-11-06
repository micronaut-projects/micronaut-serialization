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
package io.micronaut.serde.processor.sourcegen.csv;

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShape;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV-aware serializer source generator. Mirrors {@code BeanSerializerSourceGen} but sources
 * the CSV column names from {@link CsvSerdeShape} and drives {@code io.micronaut.serde.csv.CsvEncoder}.
 *
 * <p>Handles scalar properties with cached column names. Inline nested beans are rendered
 * via {@code String.valueOf(value)} for scalar cells.</p>
 */
public final class CsvSerializerSourceGen {

    private static final String CONTEXT_PARAMETER = "context";
    private static final String VALUE_PARAMETER = "value";
    private static final String KEYS_FIELD = "KEYS";
    private static final String GENERATED_VALUE_MEMBER = "value";

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(io.micronaut.core.type.Argument.class);
    private static final TypeDef SERIALIZER_TYPE = TypeDef.of(Serializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final ClassTypeDef KEYS_TYPE = ClassTypeDef.of(Keys.class);
    private static final ClassTypeDef KEYS_AWARE_ENCODER_TYPE = ClassTypeDef.of(KeysAwareEncoder.class);

    private static final Method KEYS_CREATE_METHOD = ReflectionUtils.getRequiredMethod(Keys.class, "create", String[].class);
    private static final Method ENCODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeObject", io.micronaut.core.type.Argument.class);
    private static final Method ENCODE_KEY_KEYS_METHOD = ReflectionUtils.getRequiredMethod(KeysAwareEncoder.class, "encodeKey", Keys.class, int.class);
    private static final Method ENCODE_STRING_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeString", String.class);
    private static final Method ENCODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeNull");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "finishStructure");

    public ClassDef generate(ClassElement element, CsvSerdeShape shape) {
        BeanSerdeShape beanShape = shape.beanShape();
        TypeDef beanTypeDef = TypeDef.of(element);
        ClassTypeDef serializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedSerializerClassName(element));
        List<FieldDef> fields = new ArrayList<>();
        Map<String, String> keyFieldNames = new LinkedHashMap<>();

        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanShape.serializationProperties()) {
            String keyFieldName = "KEY_" + index;
            keyFieldNames.put(property.name(), keyFieldName);
            String colName = shape.propertyToColumn().getOrDefault(property.name(), property.name());
            fields.add(FieldDef.builder(keyFieldName, STRING_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(ExpressionDef.constant(colName))
                .build());
            index++;
        }
        if (!keyFieldNames.isEmpty()) {
            List<ExpressionDef> keyExpressions = new ArrayList<>();
            for (int i = 0; i < keyFieldNames.size(); i++) {
                keyExpressions.add(serializerClassTypeDef.getStaticField("KEY_" + i, STRING_TYPE));
            }
            fields.add(FieldDef.builder(KEYS_FIELD, KEYS_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(KEYS_TYPE.invokeStatic(KEYS_CREATE_METHOD, STRING_TYPE.array().instantiate(keyExpressions)))
                .build());
        }

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember(GENERATED_VALUE_MEMBER, "Micronaut").build())
            .addSuperinterface(TypeDef.parameterized(Serializer.class, beanTypeDef))
            .addFields(fields)
            .addMethod(generateSerializeMethod(beanTypeDef))
            .addMethod(generateSerializeIntoMethod(beanTypeDef, serializerClassTypeDef, beanShape, shape, keyFieldNames));

        return classDefBuilder.build();
    }

    private MethodDef generateSerializeMethod(TypeDef beanTypeDef) {
        return MethodDef.builder("serialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(io.micronaut.core.type.Argument.class))
            .addParameter(VALUE_PARAMETER, beanTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(2);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter value = methodParameters.get(3);
                StatementDef.DefineAndAssign objectEncoderDef = encoder.invoke(ENCODE_OBJECT_METHOD, type).newLocal("objectEncoder");
                List<StatementDef> stmts = new ArrayList<>();
                stmts.add(objectEncoderDef);
                stmts.add(aThis.invoke("serializeInto", TypeDef.VOID, objectEncoderDef.variable(), context, type, value));
                stmts.add(objectEncoderDef.variable().invoke(FINISH_STRUCTURE_METHOD));
                return StatementDef.multi(stmts);
            });
    }

    @SuppressWarnings("java:S107")
    private MethodDef generateSerializeIntoMethod(TypeDef beanTypeDef,
                                                  ClassTypeDef serializerClassTypeDef,
                                                  BeanSerdeShape beanShape,
                                                  CsvSerdeShape shape,
                                                  Map<String, String> keyFieldNames) {
        return MethodDef.builder("serializeInto")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(io.micronaut.core.type.Argument.class))
            .addParameter(VALUE_PARAMETER, beanTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter value = methodParameters.get(3);
                List<StatementDef> stmts = new ArrayList<>();
                stmts.add(KEYS_AWARE_ENCODER_TYPE.invokeStatic(
                    ReflectionUtils.getRequiredMethod(KeysAwareEncoder.class, "of", Encoder.class),
                    encoder).newLocal("keysAwareEncoder", keysAwareEncoder -> {
                    List<StatementDef> inner = new ArrayList<>();
                    int idx = 0;
                    for (BeanSerdeShape.BeanProperty property : beanShape.serializationProperties()) {
                        inner.add(keysAwareEncoder.invoke(ENCODE_KEY_KEYS_METHOD,
                            serializerClassTypeDef.getStaticField(KEYS_FIELD, KEYS_TYPE),
                            ExpressionDef.constant(idx)));
                        // Scalar: encodeString(String.valueOf(value))
                        inner.add(encoder.invoke(ENCODE_STRING_METHOD,
                            ClassTypeDef.of(String.class).invokeStatic(
                                ReflectionUtils.getRequiredMethod(String.class, "valueOf", Object.class),
                                readPropertyValue(value, property))));
                        idx++;
                    }
                    return StatementDef.multi(inner);
                }));
                return StatementDef.multi(stmts);
            });
    }

    private ExpressionDef readPropertyValue(VariableDef.MethodParameter value, BeanSerdeShape.BeanProperty property) {
        if (property.readField() != null) {
            return new VariableDef.Local(VALUE_PARAMETER + "." + property.readField().getName(),
                TypeDef.of(property.serializationType()));
        }
        return value.invoke(java.util.Objects.requireNonNull(property.readMethod()));
    }
}
