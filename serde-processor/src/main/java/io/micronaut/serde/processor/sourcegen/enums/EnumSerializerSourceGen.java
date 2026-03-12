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
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class EnumSerializerSourceGen {

    private static final Method FIND_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(Serializer.EncoderContext.class, "findSerializer", Argument.class);
    private static final Method CREATE_SPECIFIC_SERIALIZER_METHOD = ReflectionUtils.getRequiredMethod(
        Serializer.class,
        "createSpecific",
        Serializer.EncoderContext.class,
        Argument.class
    );
    private static final Method SERIALIZE_METHOD = ReflectionUtils.getRequiredMethod(
        Serializer.class,
        "serialize",
        Encoder.class,
        Serializer.EncoderContext.class,
        Argument.class,
        Object.class
    );
    private static final Method ENCODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeNull");
    private static final Method ENUM_NAME_METHOD = ReflectionUtils.getRequiredMethod(Enum.class, "name");

    public ClassDef generate(ClassElement element, EnumSerdeShape enumSerdeShape) {
        TypeDef enumTypeDef = TypeDef.of(element);
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(TypeDef.parameterized(Serializer.class, enumTypeDef))
            .addSuperinterface(TypeDef.parameterized(ObjectSerializer.class, enumTypeDef))
            .addMethod(generateSerializeMethod(enumTypeDef, enumSerdeShape))
            .addMethod(generateSerializeIntoMethod(enumTypeDef, enumSerdeShape))
            .build();
    }

    private MethodDef generateSerializeMethod(TypeDef enumTypeDef, EnumSerdeShape enumSerdeShape) {
        return MethodDef.builder("serialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter("value", enumTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter value = methodParameters.get(3);

                return value.isNull().ifTrue(
                    encoder.invoke(ENCODE_NULL_METHOD),
                    StatementDef.multi(serializeStatements(encoder, context, value, enumSerdeShape))
                );
            });
    }

    private MethodDef generateSerializeIntoMethod(TypeDef enumTypeDef, EnumSerdeShape enumSerdeShape) {
        return MethodDef.builder("serializeInto")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter("value", enumTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> StatementDef.multi(
                serializeStatements(methodParameters.get(0), methodParameters.get(1), methodParameters.get(3), enumSerdeShape)
            ));
    }

    private List<StatementDef> serializeStatements(VariableDef encoder,
                                                   VariableDef.MethodParameter context,
                                                   VariableDef.MethodParameter value,
                                                   EnumSerdeShape enumSerdeShape) {
        ExpressionDef stringArgument = EnumSerdeSourceGenUtils.stringArgumentExpression();
        List<StatementDef> statements = new ArrayList<>();

        StatementDef.DefineAndAssign serializerLookupDef = context.invoke(FIND_SERIALIZER_METHOD, stringArgument)
            .newLocal("stringSerializerLookup");
        StatementDef.DefineAndAssign serializerDef = serializerLookupDef.variable().invoke(CREATE_SPECIFIC_SERIALIZER_METHOD, context, stringArgument)
            .newLocal("stringSerializer");
        statements.add(serializerLookupDef);
        statements.add(serializerDef);
        VariableDef serializerVariable = serializerDef.variable();

        StatementDef.DefineAndAssign enumNameDef = value.invoke(ENUM_NAME_METHOD).newLocal("enumName");
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

        statements.add(serializerVariable.invoke(
            SERIALIZE_METHOD,
            encoder,
            context,
            stringArgument,
            serializedValueVariable.cast(TypeDef.OBJECT)
        ));
        return statements;
    }
}
