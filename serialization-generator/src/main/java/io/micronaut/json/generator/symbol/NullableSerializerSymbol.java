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
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Internal;

@Internal
public class NullableSerializerSymbol implements SerializerSymbol {
    private final SerializerSymbol delegate;

    public NullableSerializerSymbol(SerializerSymbol delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        throw new UnsupportedOperationException("Not part of the normal linker chain");
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsNullDeserialization() {
        return true;
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        delegate.visitDependencies(visitor, type);
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        String variable = generatorContext.newLocalVariable("tmp");
        return CodeBlock.builder()
                .addStatement("$T $N = $L", PoetUtil.toTypeName(type), variable, readExpression)
                .beginControlFlow("if ($N == null)", variable)
                .addStatement("$N.encodeNull()", encoderVariable)
                .nextControlFlow("else")
                .add(delegate.serialize(generatorContext, encoderVariable, type, CodeBlock.of("$N", variable)))
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        return CodeBlock.builder()
                .beginControlFlow("if ($N.decodeNull())", decoderVariable)
                .add(setter.createSetStatement(CodeBlock.of("null")))
                .nextControlFlow("else")
                .add(delegate.deserialize(generatorContext, decoderVariable, type, setter))
                .endControlFlow()
                .build();
    }

    @Override
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
        ConditionExpression<CodeBlock> expr = this.delegate.shouldIncludeCheck(generatorContext, type, inclusionPolicy);
        switch (inclusionPolicy) {
            case NON_NULL:
            case NON_ABSENT:
            case NON_EMPTY:
                expr = ConditionExpression.<CodeBlock>of(v -> CodeBlock.of("$L != null", v)).and(expr);
        }
        return expr;
    }
}
