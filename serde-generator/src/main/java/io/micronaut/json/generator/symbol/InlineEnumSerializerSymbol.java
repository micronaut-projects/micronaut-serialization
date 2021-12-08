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

import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.EnumElement;

import java.util.List;
import java.util.stream.Collectors;

@Internal
public class InlineEnumSerializerSymbol implements SerializerSymbol {
    public static final InlineEnumSerializerSymbol INSTANCE = new InlineEnumSerializerSymbol();

    private InlineEnumSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return type.isEnum();
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        EnumDefinition enumDefinition = new EnumDefinition((EnumElement) type.getClassElement());

        CodeBlock.Builder builder = CodeBlock.builder();
        builder.beginControlFlow("switch ($L)", readExpression);
        for (int i = 0; i < enumDefinition.constants.size(); i++) {
            builder.beginControlFlow("case $N:", enumDefinition.constants.get(i));
            builder.add(enumDefinition.valueSerializer.serialize(
                    generatorContext, encoderVariable,
                    enumDefinition.valueType,
                    enumDefinition.serializedLiterals.get(i)));
            builder.addStatement("break");
            builder.endControlFlow();
        }

        builder.beginControlFlow("default:");
        // new enum constant added. Throw ICCE to be consistent with java switch expressions
        builder.addStatement("throw new $T()", IncompatibleClassChangeError.class);
        builder.endControlFlow();

        builder.endControlFlow();
        return builder.build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        EnumDefinition enumDefinition = new EnumDefinition((EnumElement) type.getClassElement());

        return enumDefinition.valueSerializer.deserialize(generatorContext, decoderVariable, type, new Setter() {
            @Override
            public CodeBlock createSetStatement(CodeBlock expr) {
                return InlineEnumSerializerSymbol.this.deserialize0(generatorContext, decoderVariable, type, setter, enumDefinition, expr);
            }

            @Override
            public boolean terminatesBlock() {
                return setter.terminatesBlock();
            }
        });
    }

    @NonNull
    private CodeBlock deserialize0(
            GeneratorContext generatorContext,
            String decoderVariable,
            GeneratorType type,
            Setter setter,
            EnumDefinition enumDefinition,
            CodeBlock valueExpression
    ) {
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.beginControlFlow("switch ($L)", valueExpression);

        for (int i = 0; i < enumDefinition.constants.size(); i++) {
            builder.beginControlFlow("case $L:", enumDefinition.serializedLiterals.get(i));
            builder.add(setter.createSetStatement(CodeBlock.of("$T.$N", PoetUtil.toTypeName(type), enumDefinition.constants.get(i))));
            if (!setter.terminatesBlock()) {
                builder.addStatement("break");
            }
            builder.endControlFlow();
        }

        builder.beginControlFlow("default:");
        builder.addStatement(
                "throw $N.createDeserializationException($S)",
                decoderVariable,
                "Bad enum value for field " + generatorContext.getReadablePath()
        );
        builder.endControlFlow();

        builder.endControlFlow();
        return builder.build();
    }

    private static class EnumDefinition {
        private final List<String> constants;
        private final List<CodeBlock> serializedLiterals;

        private final SerializerSymbol valueSerializer;
        private final GeneratorType valueType;

        EnumDefinition(EnumElement element) {
            // todo: support @JsonProperty
            // todo: optimize the case where name()/valueOf() can be used
            this.constants = element.values();
            this.serializedLiterals = element.values().stream()
                    .map(v -> CodeBlock.of("$S", v))
                    .collect(Collectors.toList());
            this.valueSerializer = StringSerializerSymbol.INSTANCE;
            this.valueType = GeneratorType.STRING;
        }
    }
}
