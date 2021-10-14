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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import io.micronaut.json.Decoder;
import io.micronaut.json.Encoder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

final class InlineStringMapSerializerSymbol extends AbstractInlineContainerSerializerSymbol implements SerializerSymbol {
    InlineStringMapSerializerSymbol(SerializerLinker linker) {
        super(linker);
    }

    private InlineStringMapSerializerSymbol(InlineStringMapSerializerSymbol original, boolean recursiveSerialization) {
        super(original, recursiveSerialization);
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return Stream.of(LinkedHashMap.class, HashMap.class, Map.class).anyMatch(type::isRawTypeEquals)
                && getType(type, "K").isRawTypeEquals(String.class);
    }

    private GeneratorType getType(GeneratorType type, String varName) {
        return type.getTypeArgumentsExact().get(varName);
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        if (visitor.visitStructure()) {
            GeneratorType elementType = getType(type, "V");
            visitor.visitStructureElement(getElementSymbol(elementType), elementType, null);
        }
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new InlineStringMapSerializerSymbol(this, true);
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        GeneratorType elementType = getType(type, "V");
        SerializerSymbol elementSerializer = getElementSymbol(elementType);
        String entryName = generatorContext.newLocalVariable("entry");
        String objectEncoder = generatorContext.newLocalVariable("mapEncoder");
        return CodeBlock.builder()
                .addStatement("$T $N = $N.encodeObject()", Encoder.class, objectEncoder, encoderVariable)
                .beginControlFlow("for ($T $N : $L.entrySet())",
                        ParameterizedTypeName.get(
                                ClassName.get(Map.Entry.class),
                                ClassName.get(String.class),
                                PoetUtil.toTypeName(elementType)
                        ),
                        entryName,
                        readExpression
                )
                .addStatement("$N.encodeKey($N.getKey())", objectEncoder, entryName)
                .add(elementSerializer.serialize(generatorContext.withSubPath("[*]"), objectEncoder, elementType, CodeBlock.of("$N.getValue()", entryName)))
                .endControlFlow()
                .addStatement("$N.finishStructure()", objectEncoder)
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        GeneratorType elementType = getType(type, "V");
        SerializerSymbol elementDeserializer = getElementSymbol(elementType);

        String intermediateVariable = generatorContext.newLocalVariable("map");
        String elementDecoderVariable = generatorContext.newLocalVariable("mapDecoder");

        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("$T $N = $N.decodeObject()", Decoder.class, elementDecoderVariable, decoderVariable);
        block.addStatement("$T $N = new $T<>()",
                ParameterizedTypeName.get(
                        ClassName.get(LinkedHashMap.class),
                        ClassName.get(String.class),
                        PoetUtil.toTypeName(elementType)
                ),
                intermediateVariable,
                LinkedHashMap.class
        );
        String keyVariable = generatorContext.newLocalVariable("key");
        block.addStatement("$T $N", String.class, keyVariable);
        block.beginControlFlow("while (($N = $N.decodeKey()) != null)", keyVariable, elementDecoderVariable);
        block.add(elementDeserializer.deserialize(generatorContext, elementDecoderVariable, elementType, expr -> CodeBlock.of("$N.put($N, $L);\n", intermediateVariable, keyVariable, expr)));
        block.endControlFlow();

        block.addStatement("$N.finishStructure()", elementDecoderVariable);

        block.add(setter.createSetStatement(CodeBlock.of("$N", intermediateVariable)));
        return block.build();
    }
}
