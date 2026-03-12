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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.util.GeneratedSerdeErrorHandler;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecordDeserializerSourceGen {

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
    private static final Method DECODE_OBJECT_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeObject", Argument.class);
    private static final Method DECODE_KEY_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "decodeKey");
    private static final Method FINISH_STRUCTURE_METHOD = ReflectionUtils.getRequiredMethod(Decoder.class, "finishStructure");
    private static final Method UNKNOWN_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "unknownProperty",
        String.class,
        Argument.class
    );
    private static final Method DUPLICATE_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "duplicateProperty",
        String.class,
        Argument.class
    );
    private static final Method HANDLE_UNKNOWN_PROPERTY_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "handleUnknownProperty",
        Decoder.class,
        Deserializer.DecoderContext.class,
        String.class,
        Argument.class
    );
    private static final Method WITH_PROPERTY_PATH_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "withPropertyPath",
        SerdeException.class,
        Argument.class,
        String.class,
        Argument.class
    );
    private static final Method WITH_PROPERTY_PATH_THROWABLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeErrorHandler.class,
        "withPropertyPath",
        Throwable.class,
        Argument.class,
        String.class,
        Argument.class
    );
    private static final Method SET_ADD_METHOD = ReflectionUtils.getRequiredMethod(Set.class, "add", Object.class);

    public ClassDef generate(ClassElement element, RecordSerdeShape recordSerdeShape) {
        TypeDef recordTypeDef = TypeDef.of(element);
        return ClassDef.builder(SerdeSourceGenClassNaming.generatedDeserializerClassName(element))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, recordTypeDef))
            .addMethod(generateDeserializeMethod(element, recordTypeDef, recordSerdeShape))
            .build();
    }

    private MethodDef generateDeserializeMethod(ClassElement element,
                                                TypeDef recordTypeDef,
                                                RecordSerdeShape recordSerdeShape) {
        return MethodDef.builder("deserialize")
            .addModifiers(Modifier.PUBLIC)
            .overrides()
            .returns(recordTypeDef)
            .addParameter("decoder", TypeDef.of(Decoder.class))
            .addParameter("context", TypeDef.of(Deserializer.DecoderContext.class))
            .addParameter("type", TypeDef.of(Argument.class))
            .addThrows(TypeDef.of(IOException.class))
            .build((aThis, methodParameters) -> {
                VariableDef.MethodParameter decoder = methodParameters.get(0);
                VariableDef.MethodParameter context = methodParameters.get(1);
                VariableDef.MethodParameter type = methodParameters.get(2);

                List<StatementDef> statements = new ArrayList<>();
                StatementDef.DefineAndAssign objectDecoderDef = decoder.invoke(DECODE_OBJECT_METHOD, type).newLocal("objectDecoder");
                statements.add(objectDecoderDef);
                VariableDef objectDecoder = objectDecoderDef.variable();
                StatementDef.DefineAndAssign seenPropertiesDef = ClassTypeDef.of(HashSet.class).instantiate().newLocal("seenProperties");
                statements.add(seenPropertiesDef);
                VariableDef seenProperties = seenPropertiesDef.variable();

                List<ExpressionDef> constructorValues = new ArrayList<>(recordSerdeShape.components().size());
                List<RecordSerdeShape.RecordComponent> components = recordSerdeShape.components();
                List<StatementDef> componentDeserializers = new ArrayList<>(components.size());
                int index = 0;
                for (RecordSerdeShape.RecordComponent component : components) {
                    boolean nonNull = component.propertyElement().hasDeclaredAnnotation("org.jspecify.annotations.NonNull")
                        || component.propertyElement().hasDeclaredAnnotation("jakarta.annotation.Nonnull")
                        || component.propertyElement().hasDeclaredAnnotation("javax.annotation.Nonnull");
                    StatementDef.DefineAndAssign valueDef = RecordSerdeSourceGenUtils.defaultValueExpression(component.type(), nonNull)
                        .newLocal(RecordSerdeSourceGenUtils.localName("component", component.name(), index));
                    statements.add(valueDef);
                    VariableDef valueVariable = valueDef.variable();
                    constructorValues.add(valueVariable);
                    componentDeserializers.add(deserializeComponent(objectDecoder, context, type, valueVariable, component, index));
                    index++;
                }

                StatementDef.DefineAndAssign keyDef = objectDecoder.invoke(DECODE_KEY_METHOD).newLocal("key");
                statements.add(keyDef);
                VariableDef keyVariable = keyDef.variable();

                StatementDef switchStatement = ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                    .invokeStatic(HANDLE_UNKNOWN_PROPERTY_METHOD, objectDecoder, context, keyVariable, type);
                for (int i = components.size() - 1; i >= 0; i--) {
                    RecordSerdeShape.RecordComponent component = components.get(i);
                    StatementDef duplicatePropertyStatement = ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                        .invokeStatic(DUPLICATE_PROPERTY_METHOD, ExpressionDef.constant(component.name()), type)
                        .doThrow();
                    switchStatement = keyVariable.equalsStructurally(ExpressionDef.constant(component.name())).ifTrue(
                        seenProperties.invoke(SET_ADD_METHOD, ExpressionDef.constant(component.name())).ifTrue(
                            componentDeserializers.get(i),
                            duplicatePropertyStatement
                        ),
                        switchStatement
                    );
                }
                if (components.isEmpty()) {
                    statements.add(keyVariable.isNonNull().whileLoop(switchStatement));
                } else {
                    statements.add(keyVariable.isNonNull().whileLoop(
                        StatementDef.multi(
                            switchStatement,
                            keyVariable.assign(objectDecoder.invoke(DECODE_KEY_METHOD))
                        )
                    ));
                }
                statements.add(objectDecoder.invoke(FINISH_STRUCTURE_METHOD));
                statements.add(ClassTypeDef.of(element).instantiate(recordSerdeShape.canonicalConstructor(), constructorValues).returning());
                return StatementDef.multi(statements);
            });
    }

    private StatementDef deserializeComponent(VariableDef objectDecoder,
                                              VariableDef.MethodParameter context,
                                              VariableDef.MethodParameter type,
                                              VariableDef valueVariable,
                                              RecordSerdeShape.RecordComponent component,
                                              int index) {
        ClassElement lookupType = resolveLookupType(component.type());
        ExpressionDef argumentExpression = RecordSerdeSourceGenUtils.argumentExpression(lookupType);
        StatementDef.DefineAndAssign deserializerLookupDef = context.invoke(FIND_DESERIALIZER_METHOD, argumentExpression)
            .newLocal(RecordSerdeSourceGenUtils.localName("deserializerLookup", component.name(), index));
        StatementDef.DefineAndAssign deserializerDef = deserializerLookupDef.variable().invoke(CREATE_SPECIFIC_DESERIALIZER_METHOD, context, argumentExpression)
            .newLocal(RecordSerdeSourceGenUtils.localName("deserializer", component.name(), index));
        VariableDef deserializerVariable = deserializerDef.variable();

        ExpressionDef deserializedValueExpression = deserializerVariable.invoke(
            DESERIALIZE_NULLABLE_METHOD,
            objectDecoder,
            context,
            argumentExpression
        ).cast(RecordSerdeSourceGenUtils.deserializedCastType(component.type()));

        StatementDef deserializeAndAssign = StatementDef.multi(
            deserializerLookupDef,
            deserializerDef,
            valueVariable.assign(deserializedValueExpression)
        );
        return StatementDef.doTry(deserializeAndAssign)
            .doCatch(ClassTypeDef.of(Throwable.class), exceptionVariable ->
                ClassTypeDef.of(GeneratedSerdeErrorHandler.class)
                    .invokeStatic(
                        WITH_PROPERTY_PATH_THROWABLE_METHOD,
                        exceptionVariable,
                        type,
                        ExpressionDef.constant(component.name()),
                        argumentExpression
                    )
                    .doThrow()
            );
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
}
