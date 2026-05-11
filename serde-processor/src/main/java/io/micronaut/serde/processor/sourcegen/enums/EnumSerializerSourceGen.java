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
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates source serializers for enum types.
 */
public final class EnumSerializerSourceGen {
    private static final String CONTEXT_PARAMETER = "context";
    private static final String VALUE_PARAMETER = "value";
    private static final String GENERATED_VALUE_MEMBER = "value";

    private static final Method ENCODE_STRING_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeString", String.class);
    private static final Method ENUM_NAME_METHOD = ReflectionUtils.getRequiredMethod(Enum.class, "name");
    private static final Method RUNTIME_FALLBACK_FORMATTED_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeFallbackUtil.class,
        "runtimeFormattedEnumSerializer",
        Serializer.EncoderContext.class,
        Argument.class,
        FormatConfiguration.class
    );
    private static final Method RUNTIME_FALLBACK_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeFallbackUtil.class,
        "withRuntimeEnumFallback",
        Serializer.class,
        Serializer.EncoderContext.class,
        Argument.class
    );

    public ClassDef generate(ClassElement element, EnumSerdeShape enumSerdeShape) {
        TypeDef enumTypeDef = TypeDef.of(element);
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Prototype.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember(GENERATED_VALUE_MEMBER, "Micronaut")
            .build())
            .addSuperinterface(TypeDef.parameterized(FormattedSerializer.class, enumTypeDef))
            .addSuperinterface(TypeDef.parameterized(ObjectSerializer.class, enumTypeDef))
            .addMethod(generateCreateSpecificMethod(enumTypeDef))
            .addMethod(generateCreateSpecificWithFormatMethod(enumTypeDef))
            .addMethod(generateSerializeMethod(enumTypeDef, enumSerdeShape))
            .addMethod(generateSerializeIntoMethod(enumTypeDef, enumSerdeShape))
            .build();
    }

    private MethodDef generateCreateSpecificMethod(TypeDef enumTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Serializer.class, enumTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.parameterized(Argument.class, TypeDef.wildcardSubtypeOf(enumTypeDef)))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter context = methodParameters.get(0);
                VariableDef.MethodParameter type = methodParameters.get(1);
                return ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                    .invokeStatic(RUNTIME_FALLBACK_SERIALIZER_METHOD, aThis, context, type)
                    .cast(TypeDef.parameterized(Serializer.class, enumTypeDef))
                    .returning();
            });
    }

    private MethodDef generateCreateSpecificWithFormatMethod(TypeDef enumTypeDef) {
        return MethodDef.builder("createSpecific")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(TypeDef.parameterized(Serializer.class, enumTypeDef))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.parameterized(Argument.class, TypeDef.wildcardSubtypeOf(enumTypeDef)))
            .addParameter("format", TypeDef.of(FormatConfiguration.class))
            .addThrows(TypeDef.of(SerdeException.class))
            .build((aThis, methodParameters) -> ClassTypeDef.of(GeneratedSerdeFallbackUtil.class)
                .invokeStatic(
                    RUNTIME_FALLBACK_FORMATTED_SERIALIZER_METHOD,
                    methodParameters.get(0),
                    methodParameters.get(1),
                    methodParameters.get(2)
                )
                .cast(TypeDef.parameterized(Serializer.class, enumTypeDef))
                .returning());
    }

    private MethodDef generateSerializeMethod(TypeDef enumTypeDef, EnumSerdeShape enumSerdeShape) {
        return MethodDef.builder("serialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter(VALUE_PARAMETER, enumTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> StatementDef.multi(
                serializeStatements(methodParameters.get(0), methodParameters.get(3), enumSerdeShape)
            ));
    }

    private MethodDef generateSerializeIntoMethod(TypeDef enumTypeDef, EnumSerdeShape enumSerdeShape) {
        return MethodDef.builder("serializeInto")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter(CONTEXT_PARAMETER, TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter(VALUE_PARAMETER, enumTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> StatementDef.multi(
                serializeStatements(methodParameters.get(0), methodParameters.get(3), enumSerdeShape)
            ));
    }

    private List<StatementDef> serializeStatements(VariableDef encoder,
                                                   VariableDef.MethodParameter value,
                                                   EnumSerdeShape enumSerdeShape) {
        List<StatementDef> statements = new ArrayList<>();

        StatementDef.DefineAndAssign enumNameDef = value.invoke(ENUM_NAME_METHOD)
            .newLocal("enumName");
        statements.add(enumNameDef);
        VariableDef serializedValueVariable = enumNameDef.variable();

        if (enumSerdeShape.hasPropertyOverrides()) {
            StatementDef.DefineAndAssign serializedValueDef = enumNameDef.variable().newLocal("serializedValue");
            statements.add(serializedValueDef);
            serializedValueVariable = serializedValueDef.variable();
            for (EnumSerdeShape.EnumConstant constant : enumSerdeShape.constants()) {
                if (!constant.serializedValue().equals(constant.name())) {
                    statements.add(serializedValueVariable.equalsStructurally(ExpressionDef.constant(constant.name())).ifTrue(
                        serializedValueVariable.assign(ExpressionDef.constant(constant.serializedValue()))
                    ));
                }
            }
        }

        statements.add(encoder.invoke(ENCODE_STRING_METHOD, serializedValueVariable));
        return statements;
    }
}
