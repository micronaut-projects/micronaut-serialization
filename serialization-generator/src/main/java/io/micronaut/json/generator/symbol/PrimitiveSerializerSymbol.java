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

import java.math.BigDecimal;
import java.math.BigInteger;

final class PrimitiveSerializerSymbol implements SerializerSymbol {
    static final PrimitiveSerializerSymbol INSTANCE = new PrimitiveSerializerSymbol();

    private PrimitiveSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return !type.isArray() && ((type.isPrimitive() && !type.isRawTypeEquals(void.class)) ||
                type.isRawTypeEquals(BigDecimal.class) || type.isRawTypeEquals(BigInteger.class));
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        // scalar, no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        return CodeBlock.of("$N.encode$N($L);\n", encoderVariable, suffix(type), readExpression);
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        if (!canSerialize(type)) {
            throw new UnsupportedOperationException("This symbol can only handle primitives");
        }
        return setter.createSetStatement(CodeBlock.of("$N.decode$N()", decoderVariable, suffix(type)));
    }

    @Override
    public CodeBlock getDefaultExpression(GeneratorType type) {
        if (type.isRawTypeEquals(boolean.class)) {
            return CodeBlock.of("false");
        } else if (type.isPrimitive()) {
            return CodeBlock.of("0");
        } else {
            // bigdecimal, biginteger
            return SerializerSymbol.super.getDefaultExpression(type);
        }
    }

    private String suffix(GeneratorType type) {
        if (type.isRawTypeEquals(boolean.class)) {
            return "Boolean";
        } else if (type.isRawTypeEquals(byte.class)) {
            return "Byte";
        } else if (type.isRawTypeEquals(short.class)) {
            return "Short";
        } else if (type.isRawTypeEquals(char.class)) {
            return "Char";
        } else if (type.isRawTypeEquals(int.class)) {
            return "Int";
        } else if (type.isRawTypeEquals(long.class)) {
            return "Long";
        } else if (type.isRawTypeEquals(float.class)) {
            return "Float";
        } else if (type.isRawTypeEquals(double.class)) {
            return "Double";
        } else if (type.isRawTypeEquals(BigInteger.class)) {
            return "BigInteger";
        } else if (type.isRawTypeEquals(BigDecimal.class)) {
            return "BigDecimal";
        } else {
            throw new AssertionError("unknown primitive type " + type);
        }
    }
}
