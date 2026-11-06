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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
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
 * CSV-aware deserializer source generator. Mirrors {@code BeanDeserializerSourceGen} but
 * sources the CSV column names from {@link CsvSerdeShape} and drives {@code io.micronaut.serde.csv.CsvDecoder}.
 *
 * <p>Handles scalar properties: decodes each cell as a String, converts to the target type
 * via {@code ConversionService}, and sets it on the bean.</p>
 */
public final class CsvDeserializerSourceGen {

    private static final String CONTEXT_PARAMETER = "context";
    private static final String BEAN_LOCAL = "bean";
    private static final String KEYS_FIELD = "KEYS";
    private static final String GENERATED_VALUE_MEMBER = "value";

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(io.micronaut.core.type.Argument.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final TypeDef STRING_TYPE = TypeDef.of(String.class);
    private static final ClassTypeDef KEYS_TYPE = ClassTypeDef.of(Keys.class);
    private static final ClassTypeDef KEYS_AWARE_DECODER_TYPE = ClassTypeDef.of(KeysAwareDecoder.class);

    private static final Method KEYS_CREATE_METHOD = ReflectionUtils.getRequiredMethod(Keys.class, "create", String[].class);
    private static final Method DECODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeObject", io.micronaut.core.type.Argument.class);
    private static final Method DECODE_KEY_KEYS_METHOD = ReflectionUtils.getRequiredMethod(KeysAwareDecoder.class, "decodeKey", Keys.class);
    private static final Method DECODE_STRING_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeString");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "finishStructure");
    private static final Method SKIP_VALUE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "skipValue");

    public ClassDef generate(ClassElement element, CsvSerdeShape shape) {
        BeanSerdeShape beanShape = shape.beanShape();
        TypeDef beanTypeDef = TypeDef.of(element);
        ClassTypeDef deserializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
        List<FieldDef> fields = new ArrayList<>();
        Map<String, String> keyFieldNames = new LinkedHashMap<>();

        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanShape.deserializationProperties()) {
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
                keyExpressions.add(deserializerClassTypeDef.getStaticField("KEY_" + i, STRING_TYPE));
            }
            fields.add(FieldDef.builder(KEYS_FIELD, KEYS_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(KEYS_TYPE.invokeStatic(KEYS_CREATE_METHOD, STRING_TYPE.array().instantiate(keyExpressions)))
                .build());
        }

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember(GENERATED_VALUE_MEMBER, "Micronaut").build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, beanTypeDef))
            .addFields(fields)
            .addMethod(generateCreateSpecificMethod(beanTypeDef))
            .addMethod(generateDeserializeMethod(element, beanTypeDef, deserializerClassTypeDef, beanShape, keyFieldNames));

        return classDefBuilder.build();
    }

    private MethodDef generateCreateSpecificMethod(TypeDef beanTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Deserializer.class, beanTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(io.micronaut.core.type.Argument.class))
            .addThrows(TypeDef.of(io.micronaut.serde.exceptions.SerdeException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter context = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(1);
                return ClassTypeDef.of(io.micronaut.serde.util.GeneratedSerdeFallbackUtil.class)
                    .invokeStatic(ReflectionUtils.getRequiredMethod(
                        io.micronaut.serde.util.GeneratedSerdeFallbackUtil.class, "withRuntimeObjectFallback",
                        Deserializer.class, Deserializer.DecoderContext.class, io.micronaut.core.type.Argument.class),
                        aThis, context, type)
                    .cast(TypeDef.parameterized(Deserializer.class, beanTypeDef))
                    .returning();
            });
    }

    private MethodDef generateDeserializeMethod(ClassElement element,
                                                TypeDef beanTypeDef,
                                                ClassTypeDef deserializerClassTypeDef,
                                                BeanSerdeShape beanShape,
                                                Map<String, String> keyFieldNames) {
        return MethodDef.builder("deserialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("decoder", TypeDef.of(Decoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(io.micronaut.core.type.Argument.class))
            .addThrows(TypeDef.of(IOException.class))
            .returns(beanTypeDef)
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter decoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);
                StatementDef.DefineAndAssign objectDecoderDef = decoder.invoke(DECODE_OBJECT_METHOD, type).newLocal("objectDecoder");
                VariableDef objectDecoder = objectDecoderDef.variable();
                StatementDef.DefineAndAssign beanDef = ClassTypeDef.of(element).instantiate().newLocal(BEAN_LOCAL);
                VariableDef beanVariable = beanDef.variable();

                List<StatementDef> stmts = new ArrayList<>();
                stmts.add(objectDecoderDef);
                stmts.add(beanDef);

                // Decode loop: decodeKey returns key index, dispatch to property
                StatementDef.DefineAndAssign keysAwareDecoderDef = KEYS_AWARE_DECODER_TYPE.invokeStatic(
                    ReflectionUtils.getRequiredMethod(KeysAwareDecoder.class, "of", Decoder.class),
                    objectDecoder).newLocal("keysAwareDecoder");
                VariableDef keysAwareDecoder = keysAwareDecoderDef.variable();
                stmts.add(keysAwareDecoderDef);
                for (int i = 0; i < beanShape.deserializationProperties().size(); i++) {
                    BeanSerdeShape.BeanProperty property = beanShape.deserializationProperties().get(i);
                    ExpressionDef keyIndex = keysAwareDecoder.invoke(DECODE_KEY_KEYS_METHOD,
                        deserializerClassTypeDef.getStaticField(KEYS_FIELD, KEYS_TYPE));
                    stmts.add(keyIndex.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, ExpressionDef.constant(i))
                        .ifTrue(StatementDef.multi(
                            beanVariable.invoke(
                                java.util.Objects.requireNonNull(property.writeMethod()),
                                ClassTypeDef.of(io.micronaut.core.convert.ConversionService.class)
                                    .getStaticField("SHARED", ClassTypeDef.of(io.micronaut.core.convert.ConversionService.class))
                                    .invoke(
                                        ReflectionUtils.getRequiredMethod(io.micronaut.core.convert.ConversionService.class, "convertRequired", Object.class, Class.class),
                                        keysAwareDecoder.invoke(DECODE_STRING_METHOD),
                                        ExpressionDef.constant(TypeDef.of(property.deserializationType()))
                                    )
                            )
                        )));
                }
                stmts.add(keysAwareDecoder.invoke(SKIP_VALUE_METHOD));

                stmts.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                stmts.add(beanVariable.returning());
                return StatementDef.multi(stmts);
            });
    }
}
