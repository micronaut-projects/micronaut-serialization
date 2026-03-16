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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeErrorHandler;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
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
 * Generates source serializers for enum types.
 */
public final class EnumSerializerSourceGen {
    private static final String CONTEXT_PARAMETER = "context";
    private static final String VALUE_PARAMETER = "value";

    private static final Method ENCODE_STRING_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeString", String.class);
    private static final Method ENCODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeNull");
    private static final Method ENUM_SERIALIZED_NAME_METHOD = ReflectionUtils.getRequiredMethod(GeneratedSerdeErrorHandler.class, "enumSerializedName", Enum.class);

    public ClassDef generate(ClassElement element, EnumSerdeShape enumSerdeShape) {
        TypeDef enumTypeDef = TypeDef.of(element);
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Singleton.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Serializer.class, enumTypeDef))
            .addSuperinterface(TypeDef.parameterized(ObjectSerializer.class, enumTypeDef))
            .addMethod(generateCreateSpecificMethod(enumTypeDef))
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
            .build((aThis, methodParameters) -> aThis.returning());
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
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter value = methodParameters.get(3);

                return value.isNull().ifTrue(
                    encoder.invoke(ENCODE_NULL_METHOD),
                    StatementDef.multi(serializeStatements(encoder, value, enumSerdeShape))
                );
            });
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

        StatementDef.DefineAndAssign enumNameDef = ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
            .invokeStatic(ENUM_SERIALIZED_NAME_METHOD, value)
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
