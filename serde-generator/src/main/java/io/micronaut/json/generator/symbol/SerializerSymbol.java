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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.Element;

import java.util.function.Function;

@Internal
public interface SerializerSymbol {
    boolean canSerialize(GeneratorType type);

    /**
     * @return a symbol equivalent to this one, but with the capability of dealing with recursive / circular serialization issues.
     */
    default SerializerSymbol withRecursiveSerialization() {
        return this;
    }

    default boolean supportsNullDeserialization() {
        return false;
    }

    void visitDependencies(DependencyVisitor visitor, GeneratorType type);

    /**
     * Generate code that writes the value returned by {@code readExpression} into {@code encoderVariable}.
     *
     * @param generatorContext The context of the generator, e.g. declared local variables.
     * @param encoderVariable The variable name of the encoder to use for serialization
     * @param type The type of the value being serialized.
     * @param readExpression The expression that reads the value. Must only be evaluated once.
     * @return The code block containing statements that perform the serialization.
     */
    CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression);

    /**
     * Generate code that reads a value from {@code decoderVariable}.
     * <p>
     * Decoder should be positioned at the first token of the value (as specified by
     * {@link io.micronaut.json.Deserializer#deserialize})
     *
     * @param generatorContext The context of the generator, e.g. declared local variables.
     * @param decoderVariable The variable name of the decoder to use for deserialization
     * @param type The type of the value being deserialized.
     * @param setter The setter to use to build the final return value.
     * @return The code that performs the deserialization.
     */
    CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter);

    /**
     * Get an expression giving a default value for this type. Used when deserializing a bean and a property is missing.
     */
    default CodeBlock getDefaultExpression(GeneratorType type) {
        return CodeBlock.of("null");
    }

    /**
     * Get an expression returning a boolean that checks whether this value should be included in serialized output
     * according to the given {@code inclusionPolicy}.
     *
     * @return an expression taking the value of this field, and returning whether the value
     */
    default ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
        switch (inclusionPolicy) {
            // overridden in symbols that actually support nulls.
            case ALWAYS:
            case NON_NULL:
            case NON_ABSENT:
            case NON_EMPTY:
                return ConditionExpression.alwaysTrue();
            default:
                throw new UnsupportedOperationException("Unsupported inclusion policy " + inclusionPolicy);
        }
    }

    @FunctionalInterface
    interface Setter {
        /**
         * Create a statement that assigns the given expression using this setter. The given expression must only be evaluated once.
         */
        CodeBlock createSetStatement(CodeBlock expression);

        /**
         * Return whether this setter terminates the current block (e.g. a return statement or a break).
         */
        default boolean terminatesBlock() {
            return false;
        }

        /**
         * Create a setter that first transforms the expression to set using {@code transform}, and then sets it using
         * {@code downstream}. Equivalent to {@code expr -> downstream.createSetStatement(transform.apply(expr))}, but
         * with proper handling of {@link #terminatesBlock()}.
         */
        static Setter delegate(Setter downstream, Function<CodeBlock, CodeBlock> transform) {
            return new Setter() {
                @Override
                public CodeBlock createSetStatement(CodeBlock expression) {
                    return downstream.createSetStatement(transform.apply(expression));
                }

                @Override
                public boolean terminatesBlock() {
                    return downstream.terminatesBlock();
                }
            };
        }
    }

    interface DependencyVisitor {
        /**
         * @return Whether to visit the elements of this structure
         */
        boolean visitStructure();

        void visitStructureElement(SerializerSymbol dependencySymbol, GeneratorType dependencyType, @Nullable Element element);

        void visitInjected(GeneratorType dependencyType, boolean provider);
    }
}
