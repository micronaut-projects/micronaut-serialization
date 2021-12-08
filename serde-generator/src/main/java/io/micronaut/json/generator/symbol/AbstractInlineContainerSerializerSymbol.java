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
import io.micronaut.core.annotation.NonNull;

/**
 * Abstract class for serializers for lists and maps
 */
abstract class AbstractInlineContainerSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;
    private final boolean recursiveSerialization;

    AbstractInlineContainerSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
        this.recursiveSerialization = false;
    }

    AbstractInlineContainerSerializerSymbol(AbstractInlineContainerSerializerSymbol original, boolean recursiveSerialization) {
        this.linker = original.linker;
        this.recursiveSerialization = recursiveSerialization;
    }

    @Override
    public abstract SerializerSymbol withRecursiveSerialization();

    @Override
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
        if (inclusionPolicy == JsonInclude.Include.NON_EMPTY) {
            return ConditionExpression.of(expr -> CodeBlock.of("!$L.isEmpty()", expr));
        }
        return SerializerSymbol.super.shouldIncludeCheck(generatorContext, type, inclusionPolicy);
    }

    @NonNull
    protected final SerializerSymbol getElementSymbol(GeneratorType elementType) {
        SerializerSymbol symbol = linker.findSymbol(elementType);
        if (recursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        if (!symbol.supportsNullDeserialization()) {
            symbol = new NullableSerializerSymbol(symbol);
        }
        return symbol;
    }
}
