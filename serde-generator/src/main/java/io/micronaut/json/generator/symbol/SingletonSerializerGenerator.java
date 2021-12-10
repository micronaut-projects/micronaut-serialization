/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.generator.symbol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.squareup.javapoet.*;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Inject;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Internal
public final class SingletonSerializerGenerator {
    private final GeneratorType valueType;
    @Nullable
    private ProblemReporter problemReporter = null;
    private boolean checkProblemReporter = false;
    @Nullable
    private SerializerSymbol symbol = null;
    @Nullable
    private TypeName valueReferenceName = null;
    @Nullable
    private String packageName = null;
    private boolean generateSerializer = true;
    private boolean generateDeserializer = true;

    private Element originatingElement;

    private SingletonSerializerGenerator(GeneratorType valueType) {
        this.valueType = valueType;
    }

    public static SingletonSerializerGenerator create(ClassElement valueType) {
        return create(GeneratorType.ofClass(valueType));
    }

    public static SingletonSerializerGenerator create(GeneratorType valueType) {
        return new SingletonSerializerGenerator(valueType);
    }

    public SingletonSerializerGenerator problemReporter(ProblemReporter problemReporter) {
        this.problemReporter = problemReporter;
        return this;
    }

    /**
     * symbol to use for serialization
     */
    public SingletonSerializerGenerator symbol(SerializerSymbol symbol) {
        this.symbol = symbol;
        return this;
    }

    public SingletonSerializerGenerator linker(SerializerLinker linker) {
        return symbol(linker.findSymbol(valueType));
    }

    /**
     * type name to use for the value being serialized, must be a reference type
     */
    public SingletonSerializerGenerator valueReferenceName(TypeName valueReferenceName) {
        this.valueReferenceName = valueReferenceName;
        return this;
    }

    /**
     * Package of the generated classes
     */
    public SingletonSerializerGenerator packageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public SingletonSerializerGenerator generateSerializer(boolean generateSerializer) {
        this.generateSerializer = generateSerializer;
        return this;
    }

    public SingletonSerializerGenerator generateDeserializer(boolean generateDeserializer) {
        this.generateDeserializer = generateDeserializer;
        return this;
    }

    public SingletonSerializerGenerator originatingElement(Element originatingElement) {
        this.originatingElement = originatingElement;
        return this;
    }

    private void fillInMissingFields() {
        if (problemReporter == null) {
            problemReporter = new ProblemReporter();
            checkProblemReporter = true;
        }
        if (valueReferenceName == null) {
            if (!valueType.isArray() && valueType.isPrimitive()) {
                throw new IllegalStateException("For primitives, must pass a separate valueReferenceName");
            }
            valueReferenceName = PoetUtil.toTypeName(valueType);
            assert valueReferenceName != null;
        }
        if (symbol == null) {
            throw new IllegalStateException("Must pass a symbol or a linker");
        }
        if (packageName == null) {
            packageName = valueType.getRawClass().getPackageName();
        }
    }

    private String prefix() {
        return '$' + valueType.getRelativeTypeName(packageName).replaceAll("[. ?<>,\\[\\]]", "_");
    }

    /**
     * Generate this serializer as a single class implementing both the serializer and deserializer.
     */
    public GenerationResult generateSingle() {
        fillInMissingFields();
        assert valueReferenceName != null;
        assert symbol != null;
        assert packageName != null;
        assert problemReporter != null;

        return generateClass(generateSerializer, generateDeserializer, ClassName.get(packageName, prefix() + "$Serializer"));
    }

    /**
     * Generate this serializer as multiple classes.
     */
    public List<GenerationResult> generateMulti() {
        fillInMissingFields();
        assert valueReferenceName != null;
        assert symbol != null;
        assert packageName != null;
        assert problemReporter != null;

        List<GenerationResult> results = new ArrayList<>(2);
        if (generateSerializer) {
            results.add(generateClass(true, false, ClassName.get(packageName, prefix() + "$Serializer")));
        }
        if (generateDeserializer) {
            results.add(generateClass(false, true, ClassName.get(packageName, prefix() + "$Deserializer")));
        }
        return results;
    }

    private GenerationResult generateClass(boolean serializer, boolean deserializer, ClassName generatedName) {
        assert valueReferenceName != null;
        assert symbol != null;

        GeneratorContext classContext = GeneratorContext.create(problemReporter, valueReferenceName.toString());

        TypeSpec.Builder builder = TypeSpec.classBuilder(generatedName.simpleName())
                .addModifiers(Modifier.FINAL);

        if (originatingElement != null) {
            Object nativeType = originatingElement.getNativeType();
            if (nativeType instanceof javax.lang.model.element.Element) {
                builder.addOriginatingElement((javax.lang.model.element.Element) nativeType);
            }
        }

        if (serializer) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), valueReferenceName))
                    .addMethod(generateSerialize(classContext));

            ConditionExpression<CodeBlock> isEmptyCheck = symbol.shouldIncludeCheck(classContext.newMethodContext("value"), valueType, JsonInclude.Include.NON_EMPTY);
            if (!isEmptyCheck.isAlwaysTrue()) {
                builder.addMethod(MethodSpec.methodBuilder("isEmpty")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(valueReferenceName, "value")
                        .returns(boolean.class)
                        .addCode("return !($L);\n", isEmptyCheck.build(CodeBlock.of("value")))
                        .build());
            }
        }
        if (deserializer) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Deserializer.class), valueReferenceName))
                    .addMethod(generateDeserialize(classContext));

            if (symbol.supportsNullDeserialization()) {
                builder.addMethod(MethodSpec.methodBuilder("supportsNullDeserialization")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean.class)
                        .addCode("return true;\n")
                        .build());
            }
        }

        // add type parameters if necessary
        List<GenericPlaceholderElement> freeVariables = new ArrayList<>(valueType.getFreeVariables());
        for (GenericPlaceholderElement typeVariable : freeVariables) {
            builder.addTypeVariable(TypeVariableName.get(
                    typeVariable.getVariableName(),
                    typeVariable.getBounds().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new)
            ));
        }
        List<String> freeVariableNames = freeVariables.stream().map(GenericPlaceholderElement::getVariableName).collect(Collectors.toList());

        MethodSpec.Builder serializerConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        CodeBlock.Builder serializerConstructorCode = CodeBlock.builder();

        CodeBlock.Builder fullConstructorCode = CodeBlock.builder();
        MethodSpec.Builder fullConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE);
        MethodSpec.Builder partialConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Inject.class);

        CodeBlock.Builder initialConstructorCall = CodeBlock.builder().add("this(");
        CodeBlock.Builder specializeConstructorCall = CodeBlock.builder().add("new $T(", generatedName);

        boolean firstInjected = true;
        // normal serializers we need, from SerializerLocator
        for (Map.Entry<GeneratorContext.InjectableSerializerType, GeneratorContext.Injected> entry : classContext.getInjectedNormalSerializers().entrySet()) {
            GeneratorContext.InjectableSerializerType injectable = entry.getKey();
            GeneratorContext.Injected injected = entry.getValue();

            builder.addField(injectable.fieldType, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL);

            serializerConstructor.addParameter(injectable.fieldType, injected.fieldName);
            serializerConstructorCode.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);

            if (!firstInjected) {
                initialConstructorCall.add(", ");
                specializeConstructorCall.add(", ");
            }
            firstInjected = false;
            initialConstructorCall.add("null");
            specializeConstructorCall.add("null"); // TODO
        }
        // other injected beans (e.g. user-specified custom serializers)
        for (Map.Entry<TypeName, GeneratorContext.Injected> entry : classContext.getInjectedBeans().entrySet()) {
            TypeName injectable = entry.getKey();
            GeneratorContext.Injected injected = entry.getValue();

            builder.addField(injectable, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL);

            serializerConstructor.addParameter(injectable, injected.fieldName);
            serializerConstructorCode.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);

            if (!firstInjected) {
                initialConstructorCall.add(", ");
                specializeConstructorCall.add(", ");
            }
            firstInjected = false;
            initialConstructorCall.add("$N", injected.fieldName);
            specializeConstructorCall.add("this.$N", injected.fieldName);

            fullConstructor.addParameter(injectable, injected.fieldName);
            partialConstructor.addParameter(injectable, injected.fieldName);
            fullConstructorCode.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);
        }
        initialConstructorCall.add(")");

        serializerConstructor.addCode(serializerConstructorCode.build());
        builder.addMethod(serializerConstructor.build());



        JavaFile generatedFile = JavaFile.builder(generatedName.packageName(), builder.build()).build();

        if (checkProblemReporter) {
            assert problemReporter != null;
            problemReporter.throwOnFailures();
        }

        return new GenerationResult(generatedName, generatedFile);
    }

    private MethodSpec generateSerialize(GeneratorContext classContext) {
        assert symbol != null;
        assert valueReferenceName != null;
        return MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Encoder.class, "encoder")
                .addParameter(Serializer.EncoderContext.class, "context")
                .addParameter(valueReferenceName, "value")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Argument.class), WildcardTypeName.subtypeOf(valueReferenceName)), "type")
                .addException(IOException.class)
                .addCode(symbol.serialize(classContext.newMethodContext("value", "encoder", "context", "type"), "encoder", "context", valueType, CodeBlock.of("value")))
                .build();
    }

    private MethodSpec generateDeserialize(GeneratorContext classContext) {
        assert symbol != null;
        return MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Decoder.class, "decoder")
                .addParameter(Deserializer.DecoderContext.class, "context")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Argument.class), WildcardTypeName.supertypeOf(valueReferenceName)), "type")
                .returns(valueReferenceName)
                .addException(IOException.class)
                .addCode(symbol.deserialize(classContext.newMethodContext("decoder", "context", "type"), "decoder", "context", valueType, new SerializerSymbol.Setter() {
                    @Override
                    public CodeBlock createSetStatement(CodeBlock expr) {
                        return CodeBlock.of("return $L;\n", expr);
                    }

                    @Override
                    public boolean terminatesBlock() {
                        return true;
                    }
                }))
                .build();
    }

    public static final class GenerationResult {
        private final ClassName serializerClassName;
        private final JavaFile generatedFile;

        private GenerationResult(ClassName serializerClassName, JavaFile generatedFile) {
            this.serializerClassName = serializerClassName;
            this.generatedFile = generatedFile;
        }

        public ClassName getSerializerClassName() {
            return serializerClassName;
        }

        public JavaFile getGeneratedFile() {
            return generatedFile;
        }
    }
}
