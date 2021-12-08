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

final class StringSerializerSymbol implements SerializerSymbol {
    static final StringSerializerSymbol INSTANCE = new StringSerializerSymbol();

    private StringSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return type.isRawTypeEquals(String.class) || type.isRawTypeEquals(CharSequence.class);
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        // scalar, no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        if (!type.isRawTypeEquals(String.class)) {
            readExpression = CodeBlock.of("$L.toString()", readExpression);
        }
        return CodeBlock.of("$N.encodeString($L);\n", encoderVariable, readExpression);
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        return setter.createSetStatement(CodeBlock.of("$N.decodeString()", decoderVariable));
    }

    @Override
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
        if (inclusionPolicy == JsonInclude.Include.NON_EMPTY) {
            // note: can't use isEmpty, we support CharSequence
            return ConditionExpression.of(expr -> CodeBlock.of("$L.length() != 0", expr));
        }
        return SerializerSymbol.super.shouldIncludeCheck(generatorContext, type, inclusionPolicy);
    }
}
