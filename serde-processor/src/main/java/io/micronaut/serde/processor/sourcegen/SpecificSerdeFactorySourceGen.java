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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SpecificSerdeFactory;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;
import jakarta.inject.Singleton;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Generates the singleton bean that creates the specific serdes of a generated serde.
 *
 * <p>A generated serde takes the context and the type as constructor arguments and resolves the serdes of
 * its properties with that context. The factory constructs it directly for every specific serde, with the
 * context of the call, instead of going through {@code BeanContext#createBean}.</p>
 *
 * @since 3.2.5
 */
@Internal
final class SpecificSerdeFactorySourceGen {

    private static final String FACTORY_SUFFIX = "Factory";
    private static final String CONTEXT_PARAMETER = "context";
    private static final String TYPE_PARAMETER = "type";
    private static final String FORMAT_PARAMETER = "format";

    private static final Method SERIALIZER_CREATE_SPECIFIC = ReflectionUtils.getRequiredMethod(
        Serializer.class, "createSpecific", Serializer.EncoderContext.class, Argument.class);
    private static final Method SERIALIZER_CREATE_SPECIFIC_FORMATTED = ReflectionUtils.getRequiredMethod(
        FormattedSerializer.class, "createSpecific", Serializer.EncoderContext.class, Argument.class, FormatConfiguration.class);
    private static final Method SERIALIZE = ReflectionUtils.getRequiredMethod(
        Serializer.class, "serialize", Encoder.class, Serializer.EncoderContext.class, Argument.class, Object.class);
    private static final Method DESERIALIZER_CREATE_SPECIFIC = ReflectionUtils.getRequiredMethod(
        Deserializer.class, "createSpecific", Deserializer.DecoderContext.class, Argument.class);
    private static final Method DESERIALIZER_CREATE_SPECIFIC_FORMATTED = ReflectionUtils.getRequiredMethod(
        FormattedDeserializer.class, "createSpecific", Deserializer.DecoderContext.class, Argument.class, FormatConfiguration.class);
    private static final Method DESERIALIZE = ReflectionUtils.getRequiredMethod(
        Deserializer.class, "deserialize", Decoder.class, Deserializer.DecoderContext.class, Argument.class);

    private SpecificSerdeFactorySourceGen() {
    }

    /**
     * Whether the generated serde takes the context and the type as constructor arguments, and is therefore
     * created by a factory. Other generated serdes are beans themselves.
     *
     * @param serde The generated serde
     * @return Whether the serde needs a factory
     */
    static boolean isCreatedWithContext(ClassDef serde) {
        if (serde.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        for (MethodDef method : serde.getMethods()) {
            if (method.isConstructor() && method.getParameters().size() == 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates the factory of a generated serializer.
     *
     * @param serializer The generated serializer
     * @param secondary  Whether the factory is a secondary bean
     * @return The factory definition
     */
    static ClassDef serializerFactory(ClassDef serializer, boolean secondary) {
        TypeDef valueType = valueType(serializer, Serializer.class, FormattedSerializer.class);
        boolean formatted = implementsInterface(serializer, FormattedSerializer.class);
        ClassTypeDef serializerType = serializer.asTypeDef();
        TypeDef returnType = TypeDef.parameterized(Serializer.class, valueType);
        TypeDef contextType = TypeDef.of(Serializer.EncoderContext.class);
        TypeDef argumentType = TypeDef.parameterized(Argument.class, TypeDef.wildcardSubtypeOf(valueType));
        List<TypeDef> constructorTypes = List.of(contextType, TypeDef.of(Argument.class));

        ClassDef.ClassDefBuilder builder = factoryBuilder(serializer, secondary)
            .addSuperinterface(TypeDef.parameterized(formatted ? FormattedSerializer.class : Serializer.class, valueType))
            .addMethod(MethodDef.builder("createSpecific")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(returnType)
                .addParameter(CONTEXT_PARAMETER, contextType)
                .addParameter(TYPE_PARAMETER, argumentType)
                .addThrows(TypeDef.of(SerdeException.class))
                .build((aThis, parameters) -> serializerType.instantiate(constructorTypes, parameters.get(0), parameters.get(1))
                    .invoke(SERIALIZER_CREATE_SPECIFIC, parameters.get(0), parameters.get(1))
                    .cast(returnType)
                    .returning()))
            .addMethod(MethodDef.builder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .addParameter("encoder", TypeDef.of(Encoder.class))
                .addParameter(CONTEXT_PARAMETER, contextType)
                .addParameter(TYPE_PARAMETER, argumentType)
                .addParameter("value", valueType)
                .addThrows(TypeDef.of(IOException.class))
                .build((aThis, parameters) -> aThis.invoke(SERIALIZER_CREATE_SPECIFIC, parameters.get(1), parameters.get(2))
                    .invoke(SERIALIZE, parameters.get(0), parameters.get(1), parameters.get(2), parameters.get(3))));
        if (formatted) {
            builder.addMethod(MethodDef.builder("createSpecific")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(returnType)
                .addParameter(CONTEXT_PARAMETER, contextType)
                .addParameter(TYPE_PARAMETER, argumentType)
                .addParameter(FORMAT_PARAMETER, TypeDef.of(FormatConfiguration.class))
                .addThrows(TypeDef.of(SerdeException.class))
                .build((aThis, parameters) -> serializerType.instantiate(constructorTypes, parameters.get(0), parameters.get(1))
                    .invoke(SERIALIZER_CREATE_SPECIFIC_FORMATTED, parameters.get(0), parameters.get(1), parameters.get(2))
                    .cast(returnType)
                    .returning()));
        }
        return builder.build();
    }

    /**
     * Generates the factory of a generated deserializer.
     *
     * @param deserializer The generated deserializer
     * @param secondary    Whether the factory is a secondary bean
     * @return The factory definition
     */
    static ClassDef deserializerFactory(ClassDef deserializer, boolean secondary) {
        TypeDef valueType = valueType(deserializer, Deserializer.class, FormattedDeserializer.class);
        boolean formatted = implementsInterface(deserializer, FormattedDeserializer.class);
        ClassTypeDef deserializerType = deserializer.asTypeDef();
        TypeDef returnType = TypeDef.parameterized(Deserializer.class, valueType);
        TypeDef contextType = TypeDef.of(Deserializer.DecoderContext.class);
        TypeDef argumentType = TypeDef.parameterized(Argument.class, TypeDef.wildcardSupertypeOf(valueType));
        List<TypeDef> constructorTypes = List.of(contextType, TypeDef.of(Argument.class));

        ClassDef.ClassDefBuilder builder = factoryBuilder(deserializer, secondary)
            .addSuperinterface(TypeDef.parameterized(formatted ? FormattedDeserializer.class : Deserializer.class, valueType))
            .addMethod(MethodDef.builder("createSpecific")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(returnType)
                .addParameter(CONTEXT_PARAMETER, contextType)
                .addParameter(TYPE_PARAMETER, argumentType)
                .addThrows(TypeDef.of(SerdeException.class))
                .build((aThis, parameters) -> deserializerType.instantiate(constructorTypes, parameters.get(0), parameters.get(1))
                    .invoke(DESERIALIZER_CREATE_SPECIFIC, parameters.get(0), parameters.get(1))
                    .cast(returnType)
                    .returning()))
            .addMethod(MethodDef.builder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(valueType)
                .addParameter("decoder", TypeDef.of(Decoder.class))
                .addParameter(CONTEXT_PARAMETER, contextType)
                .addParameter(TYPE_PARAMETER, argumentType)
                .addThrows(TypeDef.of(IOException.class))
                .build((aThis, parameters) -> aThis.invoke(DESERIALIZER_CREATE_SPECIFIC, parameters.get(1), parameters.get(2))
                    .invoke(DESERIALIZE, parameters.get(0), parameters.get(1), parameters.get(2))
                    .cast(valueType)
                    .returning()));
        if (formatted) {
            builder.addMethod(MethodDef.builder("createSpecific")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(returnType)
                .addParameter(CONTEXT_PARAMETER, contextType)
                .addParameter(TYPE_PARAMETER, argumentType)
                .addParameter(FORMAT_PARAMETER, TypeDef.of(FormatConfiguration.class))
                .addThrows(TypeDef.of(SerdeException.class))
                .build((aThis, parameters) -> deserializerType.instantiate(constructorTypes, parameters.get(0), parameters.get(1))
                    .invoke(DESERIALIZER_CREATE_SPECIFIC_FORMATTED, parameters.get(0), parameters.get(1), parameters.get(2))
                    .cast(returnType)
                    .returning()));
        }
        return builder.build();
    }

    private static ClassDef.ClassDefBuilder factoryBuilder(ClassDef serde, boolean secondary) {
        ClassDef.ClassDefBuilder builder = ClassDef.builder(serde.getName() + FACTORY_SUFFIX)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Singleton.class)
            .addAnnotation(SpecificSerdeFactory.class)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build());
        if (secondary) {
            builder.addAnnotation(Secondary.class);
        }
        return builder;
    }

    /**
     * The value type of a generated serde, as declared by its serde superinterface.
     */
    private static TypeDef valueType(ClassDef serde, Class<?> serdeType, Class<?> formattedSerdeType) {
        for (TypeDef superinterface : serde.getSuperinterfaces()) {
            if (superinterface instanceof ClassTypeDef.Parameterized parameterized
                && (parameterized.getName().equals(serdeType.getName()) || parameterized.getName().equals(formattedSerdeType.getName()))) {
                return parameterized.typeArguments().get(0);
            }
        }
        throw new IllegalStateException("Generated serde " + serde.getName() + " does not implement " + serdeType.getName());
    }

    private static boolean implementsInterface(ClassDef classDef, Class<?> type) {
        for (TypeDef superinterface : classDef.getSuperinterfaces()) {
            // A parameterized type reports the name of its raw type
            if (superinterface instanceof ClassTypeDef classTypeDef && classTypeDef.getName().equals(type.getName())) {
                return true;
            }
        }
        return false;
    }
}
