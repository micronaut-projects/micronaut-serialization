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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeErrorHandler;
import io.micronaut.sourcegen.model.ClassTypeDef;
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

public final class BeanSerializerSourceGen {

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
    private static final Method ENCODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeObject", Argument.class);
    private static final Method ENCODE_KEY_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeKey", String.class);
    private static final Method ENCODE_NULL_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "encodeNull");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Encoder.class, "finishStructure");
    private static final Method WITH_PROPERTY_PATH_THROWABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "withPropertyPath",
        Throwable.class,
        Argument.class,
        String.class,
        Argument.class
    );

    public ClassDef generate(ClassElement element, BeanSerdeShape beanSerdeShape) {
        TypeDef beanTypeDef = TypeDef.of(element);
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedSerializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(TypeDef.parameterized(Serializer.class, beanTypeDef))
            .addSuperinterface(TypeDef.parameterized(ObjectSerializer.class, beanTypeDef))
            .addMethod(generateSerializeMethod(beanTypeDef, beanSerdeShape))
            .addMethod(generateSerializeIntoMethod(beanTypeDef, beanSerdeShape))
            .build();
    }

    private MethodDef generateSerializeMethod(TypeDef beanTypeDef, BeanSerdeShape beanSerdeShape) {
        return MethodDef.builder("serialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter("value", beanTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter encoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);
                VariableDef.MethodParameter value = methodParameters.get(3);

                List<StatementDef> objectStatements = new ArrayList<>();
                StatementDef.DefineAndAssign objectEncoderDef = encoder.invoke(ENCODE_OBJECT_METHOD, type).newLocal("objectEncoder");
                objectStatements.add(objectEncoderDef);
                objectStatements.addAll(serializeIntoStatements(objectEncoderDef.variable(), context, type, value, beanSerdeShape));
                objectStatements.add(objectEncoderDef.variable().invoke(FINISH_STRUCTURE_METHOD));

                return value.isNull().ifTrue(
                    encoder.invoke(ENCODE_NULL_METHOD),
                    StatementDef.multi(objectStatements)
                );
            });
    }

    private MethodDef generateSerializeIntoMethod(TypeDef beanTypeDef, BeanSerdeShape beanSerdeShape) {
        return MethodDef.builder("serializeInto")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .addParameter("encoder", TypeDef.of(Encoder.class))
            .addParameter("context", TypeDef.of(Serializer.EncoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addParameter("value", beanTypeDef)
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> StatementDef.multi(
                serializeIntoStatements(methodParameters.get(0), methodParameters.get(1), methodParameters.get(2), methodParameters.get(3), beanSerdeShape)
            ));
    }

    private List<StatementDef> serializeIntoStatements(VariableDef encoder,
                                                       VariableDef.MethodParameter context,
                                                       VariableDef.MethodParameter type,
                                                       VariableDef.MethodParameter value,
                                                       BeanSerdeShape beanSerdeShape) {
        List<StatementDef> statements = new ArrayList<>();
        int index = 0;
        for (BeanSerdeShape.BeanProperty property : beanSerdeShape.properties()) {
            statements.add(encoder.invoke(ENCODE_KEY_METHOD, ExpressionDef.constant(property.name())));
            statements.add(serializeProperty(encoder, context, type, value, property, index++));
        }
        return statements;
    }

    private StatementDef serializeProperty(VariableDef objectEncoder,
                                           VariableDef.MethodParameter context,
                                           VariableDef.MethodParameter type,
                                           VariableDef.MethodParameter value,
                                           BeanSerdeShape.BeanProperty property,
                                           int index) {
        ExpressionDef argumentExpression = BeanSerdeSourceGenUtils.argumentExpression(property.serializationType());
        StatementDef.DefineAndAssign serializerLookupDef = context.invoke(FIND_SERIALIZER_METHOD, argumentExpression)
            .newLocal(BeanSerdeSourceGenUtils.localName("serializerLookup", property.name(), index));
        StatementDef.DefineAndAssign serializerDef = serializerLookupDef.variable().invoke(CREATE_SPECIFIC_SERIALIZER_METHOD, context, argumentExpression)
            .newLocal(BeanSerdeSourceGenUtils.localName("serializer", property.name(), index));
        VariableDef serializer = serializerDef.variable();
        ExpressionDef propertyValue = value.invoke(property.readMethod());

        StatementDef serializeStatement = StatementDef.multi(
            serializerLookupDef,
            serializerDef,
            serializer.invoke(
                SERIALIZE_METHOD,
                objectEncoder,
                context,
                argumentExpression,
                propertyValue.cast(TypeDef.OBJECT)
            )
        );
        if (property.serializationType().isPrimitive() && !property.serializationType().isArray()) {
            return wrapWithPropertyPath(serializeStatement, type, property.name(), argumentExpression);
        }
        StatementDef.DefineAndAssign propertyValueDef = propertyValue.newLocal(BeanSerdeSourceGenUtils.localName("value", property.name(), index));
        return wrapWithPropertyPath(StatementDef.multi(
            propertyValueDef,
            propertyValueDef.variable().isNull().ifTrue(
                objectEncoder.invoke(ENCODE_NULL_METHOD),
                StatementDef.multi(
                    serializerLookupDef,
                    serializerDef,
                    serializer.invoke(
                        SERIALIZE_METHOD,
                        objectEncoder,
                        context,
                        argumentExpression,
                        propertyValueDef.variable().cast(TypeDef.OBJECT)
                    )
                )
            )
        ), type, property.name(), argumentExpression);
    }

    private StatementDef wrapWithPropertyPath(StatementDef statement,
                                              VariableDef.MethodParameter type,
                                              String propertyName,
                                              ExpressionDef argumentExpression) {
        return StatementDef.doTry(statement)
            .doCatch(ClassTypeDef.of(Throwable.class), exceptionVariable ->
                ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                    .invokeStatic(
                        WITH_PROPERTY_PATH_THROWABLE_METHOD,
                        exceptionVariable,
                        type,
                        ExpressionDef.constant(propertyName),
                        argumentExpression
                    )
                    .doThrow()
            );
    }
}
