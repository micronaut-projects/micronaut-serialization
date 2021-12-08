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

import java.util.Optional;

class OptionalSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;
    private final boolean recursiveSerialization;

    public OptionalSerializerSymbol(SerializerLinker linker) {
        this(linker, false);
    }

    private OptionalSerializerSymbol(SerializerLinker linker, boolean recursiveSerialization) {
        this.linker = linker;
        this.recursiveSerialization = recursiveSerialization;
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return type.isRawTypeEquals(Optional.class);
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new OptionalSerializerSymbol(linker, true);
    }

    @Override
    public boolean supportsNullDeserialization() {
        return true;
    }

    private Optional<GeneratorType> findDelegateType(GeneratorType type) {
        return type.getTypeArgumentsExact(Optional.class).map(m -> m.get("T"));
    }

    private SerializerSymbol getDelegateSerializer(GeneratorType delegateType) {
        SerializerSymbol symbol = linker.findSymbol(delegateType);
        if (recursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        return symbol;
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        findDelegateType(type).ifPresent(t -> getDelegateSerializer(t).visitDependencies(visitor, t));
        // else assume no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        Optional<GeneratorType> delegateType = findDelegateType(type);
        if (!delegateType.isPresent()) {
            generatorContext.getProblemReporter().fail("Could not resolve optional type", null);
            return CodeBlock.of("");
        }
        String variable = generatorContext.newLocalVariable("tmp");
        return CodeBlock.builder()
                .addStatement("$T $N = $L", PoetUtil.toTypeName(type), variable, readExpression)
                .beginControlFlow("if ($N.isPresent())", variable)
                .add(getDelegateSerializer(delegateType.get()).serialize(generatorContext, encoderVariable, delegateType.get(), CodeBlock.of("$N.get()", variable)))
                .nextControlFlow("else")
                .addStatement("$N.encodeNull()", encoderVariable)
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        Optional<GeneratorType> delegateType = findDelegateType(type);
        if (!delegateType.isPresent()) {
            generatorContext.getProblemReporter().fail("Could not resolve optional type", null);
            return CodeBlock.of("");
        }
        return CodeBlock.builder()
                .beginControlFlow("if ($N.decodeNull())", decoderVariable)
                .add(setter.createSetStatement(CodeBlock.of("$T.empty()", Optional.class)))
                .nextControlFlow("else")
                .add(getDelegateSerializer(delegateType.get()).deserialize(generatorContext, decoderVariable,
                        delegateType.get(), Setter.delegate(setter, expr -> CodeBlock.of("$T.of($L)", Optional.class, expr))))
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock getDefaultExpression(GeneratorType type) {
        return CodeBlock.of("$T.empty()", Optional.class);
    }

    @Override
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
        Optional<GeneratorType> delegateType = findDelegateType(type);
        if (!delegateType.isPresent()) {
            // bail
            return ConditionExpression.alwaysTrue();
        }
        SerializerSymbol delegateSerializer = getDelegateSerializer(delegateType.get());
        ConditionExpression<CodeBlock> wrapped = delegateSerializer.shouldIncludeCheck(generatorContext, delegateType.get(), inclusionPolicy);
        switch (inclusionPolicy) {
            case NON_NULL:
            case NON_ABSENT:
            case NON_EMPTY:
                return ConditionExpression.<CodeBlock>of(expr -> CodeBlock.of("$L.isPresent()", expr))
                        .and(wrapped.compose(optional -> CodeBlock.of("$L.get()", optional)));
            default:
                return wrapped;
        }
    }
}
