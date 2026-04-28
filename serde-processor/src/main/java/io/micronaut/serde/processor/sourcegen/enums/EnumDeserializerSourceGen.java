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
package io.micronaut.serde.processor.sourcegen.enums;

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeEnumUtil;
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

import javax.lang.model.element.Modifier;
import javax.annotation.processing.Generated;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import jakarta.inject.Singleton;

/**
 * Generates source deserializers for enum types.
 */
public final class EnumDeserializerSourceGen {

    private static final TypeDef ARGUMENT_TYPE = TypeDef.of(Argument.class);
    private static final TypeDef DESERIALIZER_TYPE = TypeDef.of(Deserializer.class);
    private static final String CONTEXT_PARAMETER = "context";
    private static final String GENERATED_VALUE_MEMBER = "value";
    private static final String ARGUMENT_STRING_FIELD = "ARGUMENT_STRING";
    private static final String STRING_DESERIALIZER_FIELD = "STRING_DESERIALIZER";
    private static final String STRING_DESERIALIZER_LOCAL = "stringDeserializer";

    private static final Method FIND_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(Deserializer.DecoderContext.class, "findDeserializer", Argument.class);
    private static final Method CREATE_SPECIFIC_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        Deserializer.class,
        "createSpecific",
        Deserializer.DecoderContext.class,
        Argument.class
    );
    private static final Method DESERIALIZE_METHOD = ReflectionUtils.getRequiredMethod(
        Deserializer.class,
        "deserialize",
        Decoder.class,
        Deserializer.DecoderContext.class,
        Argument.class
    );
    private static final Method ENUM_VALUE_OF_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeEnumUtil.class,
        "enumValueOf",
        Class.class,
        String.class,
        Deserializer.DecoderContext.class
    );
    private static final Method HANDLE_UNKNOWN_ENUM_VALUE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeExceptionUtil.class,
        "handleUnknownEnumValue",
        Deserializer.DecoderContext.class,
        Argument.class,
        String.class
    );
    private static final Method RUNTIME_FALLBACK_FORMATTED_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeFallbackUtil.class,
        "runtimeFallback",
        Deserializer.DecoderContext.class,
        Argument.class,
        FormatConfiguration.class
    );
    private static final Method RUNTIME_FALLBACK_DESERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeFallbackUtil.class,
        "runtimeFallback",
        Deserializer.class,
        Deserializer.DecoderContext.class,
        Argument.class
    );

    public ClassDef generate(ClassElement element, EnumSerdeShape enumSerdeShape) {
        TypeDef enumTypeDef = TypeDef.of(element);
        ClassTypeDef deserializerClassTypeDef = ClassTypeDef.of(SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Singleton.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember(GENERATED_VALUE_MEMBER, "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(FormattedDeserializer.class, enumTypeDef))
            .addField(FieldDef.builder(ARGUMENT_STRING_FIELD, ARGUMENT_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(EnumSerdeSourceGenUtils.stringArgumentExpression())
                .build())
            .addField(FieldDef.builder(STRING_DESERIALIZER_FIELD, DESERIALIZER_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .addMethod(generateNoArgsConstructor())
            .addMethod(generateSpecializedConstructor())
            .addMethod(generateCreateSpecificMethod(enumTypeDef, deserializerClassTypeDef))
            .addMethod(generateCreateSpecificWithFormatMethod(enumTypeDef))
            .addMethod(generateDeserializeMethod(element, enumTypeDef, enumSerdeShape, deserializerClassTypeDef))
            .build();
    }

    private MethodDef generateNoArgsConstructor() {
        return MethodDef.constructor()
            .addModifiers(Modifier.PUBLIC)
            .build((aThis, methodParameters) ->
                aThis.field(STRING_DESERIALIZER_FIELD, DESERIALIZER_TYPE)
                    .put(ExpressionDef.nullValue().cast(DESERIALIZER_TYPE))
            );
    }

    private MethodDef generateSpecializedConstructor() {
        return MethodDef.constructor()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(STRING_DESERIALIZER_LOCAL, DESERIALIZER_TYPE)
            .build((aThis, methodParameters) ->
                aThis.field(STRING_DESERIALIZER_FIELD, DESERIALIZER_TYPE).put(methodParameters.get(0))
            );
    }

    private MethodDef generateCreateSpecificMethod(TypeDef enumTypeDef,
                                                   ClassTypeDef deserializerClassTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Deserializer.class, enumTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter context = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(1);
                ExpressionDef stringArgument = deserializerClassTypeDef.getStaticField(ARGUMENT_STRING_FIELD, ARGUMENT_TYPE);
                StatementDef.DefineAndAssign deserializerDef = context.invoke(FIND_DESERIALIZER_METHOD, stringArgument)
                    .invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, stringArgument)
                    .newLocal(STRING_DESERIALIZER_LOCAL);
                return StatementDef.multi(
                    deserializerDef,
                    ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                        .invokeStatic(
                            RUNTIME_FALLBACK_DESERIALIZER_METHOD,
                            deserializerClassTypeDef.instantiate(List.of(DESERIALIZER_TYPE), List.of(deserializerDef.variable())),
                            context,
                            type
                        )
                        .cast(TypeDef.parameterized(Deserializer.class, enumTypeDef))
                        .returning()
                );
            });
    }

    private MethodDef generateCreateSpecificWithFormatMethod(TypeDef enumTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Deserializer.class, enumTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.parameterized(Argument.class, TypeDef.wildcardSupertypeOf(enumTypeDef)))
            .addParameter("format", TypeDef.of(FormatConfiguration.class))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                .invokeStatic(
                    RUNTIME_FALLBACK_FORMATTED_DESERIALIZER_METHOD,
                    methodParameters.get(0),
                    methodParameters.get(1),
                    methodParameters.get(2)
                )
                .cast(TypeDef.parameterized(Deserializer.class, enumTypeDef))
                .returning());
    }

    private MethodDef generateDeserializeMethod(ClassElement element,
                                                TypeDef enumTypeDef,
                                                EnumSerdeShape enumSerdeShape,
                                                ClassTypeDef deserializerClassTypeDef) {
        return MethodDef.builder("deserialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(enumTypeDef)
            .addParameter("decoder", TypeDef.of(Decoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter decoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);

                ExpressionDef stringArgument = deserializerClassTypeDef.getStaticField(ARGUMENT_STRING_FIELD, ARGUMENT_TYPE);
                List<StatementDef> statements = new ArrayList<>();

                StatementDef.DefineAndAssign deserializerDef = aThis.field(STRING_DESERIALIZER_FIELD, DESERIALIZER_TYPE)
                    .newLocal(STRING_DESERIALIZER_LOCAL);
                StatementDef initializeDeserializerStatement = deserializerDef.variable().isNull().ifTrue(
                    deserializerDef.variable().assign(context.invoke(FIND_DESERIALIZER_METHOD, stringArgument).invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, stringArgument))
                );
                statements.add(deserializerDef);
                statements.add(initializeDeserializerStatement);
                VariableDef deserializerVariable = deserializerDef.variable();

                StatementDef.DefineAndAssign decodedValueDef = deserializerVariable.invoke(
                    DESERIALIZE_METHOD,
                    decoder,
                    context,
                    stringArgument
                ).cast(TypeDef.of(String.class)).newLocal("decodedValue");
                statements.add(decodedValueDef);
                VariableDef enumNameVariable = decodedValueDef.variable();

                if (enumSerdeShape.hasPropertyOverrides()) {
                    StatementDef.DefineAndAssign enumNameDef = decodedValueDef.variable().newLocal("enumName");
                    statements.add(enumNameDef);
                    enumNameVariable = enumNameDef.variable();
                    for (EnumSerdeShape.EnumConstant constant : enumSerdeShape.constants()) {
                        if (!constant.serializedValue().equals(constant.name())) {
                            statements.add(enumNameVariable.equalsStructurally(ExpressionDef.constant(constant.serializedValue())).ifTrue(
                                enumNameVariable.assign(ExpressionDef.constant(constant.name()))
                            ));
                        }
                    }
                }

                StatementDef deserializeStatement = ClassTypeDef.of(GeneratedSerdeEnumUtil.class)
                    .invokeStatic(
                        ENUM_VALUE_OF_METHOD,
                        ExpressionDef.constant(TypeDef.erasure(element)),
                        enumNameVariable,
                        context
                    )
                    .cast(enumTypeDef)
                    .returning();
                statements.add(StatementDef.doTry(deserializeStatement)
                    .doCatch(ClassTypeDef.of(IllegalArgumentException.class), exceptionVariable ->
                        ClassTypeDef.of(GeneratedSerdeExceptionUtil.class)
                            .invokeStatic(
                                HANDLE_UNKNOWN_ENUM_VALUE_METHOD,
                                context,
                                methodParameters.get(2),
                                decodedValueDef.variable()
                            )
                            .cast(enumTypeDef)
                            .returning()
                    ));
                return StatementDef.multi(statements);
            });
    }
}
