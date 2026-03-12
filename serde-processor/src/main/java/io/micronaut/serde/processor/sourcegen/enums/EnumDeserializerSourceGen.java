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
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
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

public final class EnumDeserializerSourceGen {

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
    private static final Method ENUM_VALUE_OF_METHOD = ReflectionUtils.getRequiredMethod(Enum.class, "valueOf", Class.class, String.class);

    public ClassDef generate(ClassElement element, EnumSerdeShape enumSerdeShape) {
        TypeDef enumTypeDef = TypeDef.of(element);
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, enumTypeDef))
            .addMethod(generateDeserializeMethod(element, enumTypeDef, enumSerdeShape))
            .build();
    }

    private MethodDef generateDeserializeMethod(ClassElement element,
                                                TypeDef enumTypeDef,
                                                EnumSerdeShape enumSerdeShape) {
        return MethodDef.builder("deserialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(enumTypeDef)
            .addParameter("decoder", TypeDef.of(Decoder.class))
            .addParameter("context", TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter decoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);

                ExpressionDef stringArgument = EnumSerdeSourceGenUtils.stringArgumentExpression();
                List<StatementDef> statements = new ArrayList<>();

                StatementDef.DefineAndAssign deserializerLookupDef = context.invoke(FIND_DESERIALIZER_METHOD, stringArgument)
                    .newLocal("stringDeserializerLookup");
                StatementDef.DefineAndAssign deserializerDef = deserializerLookupDef.variable().invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, stringArgument)
                    .newLocal("stringDeserializer");
                statements.add(deserializerLookupDef);
                statements.add(deserializerDef);
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

                statements.add(ClassTypeDef.of(Enum.class)
                    .invokeStatic(
                        ENUM_VALUE_OF_METHOD,
                        ExpressionDef.constant(TypeDef.erasure(element)),
                        enumNameVariable
                    )
                    .cast(enumTypeDef)
                    .returning());
                return StatementDef.multi(statements);
            });
    }
}
