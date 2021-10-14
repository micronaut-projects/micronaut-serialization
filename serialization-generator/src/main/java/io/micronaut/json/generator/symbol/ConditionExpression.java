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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A builder for a {@link CodeBlock} that is a boolean expression. Accepts a parameter {@code I} to build the
 * condition.
 */
public class ConditionExpression<I> {
    private static final ConditionExpression<Object> ALWAYS_TRUE = new ConditionExpression<>(Collections.emptyList());

    private final List<? extends Function<? super I, CodeBlock>> andTerms;

    private ConditionExpression(List<? extends Function<? super I, CodeBlock>> andTerms) {
        this.andTerms = andTerms;
    }

    @SuppressWarnings("unchecked")
    public static <I> ConditionExpression<I> alwaysTrue() {
        return (ConditionExpression<I>) ALWAYS_TRUE;
    }

    public static <I> ConditionExpression<I> of(Function<? super I, CodeBlock> condition) {
        return new ConditionExpression<>(Collections.singletonList(condition));
    }

    public ConditionExpression<I> and(ConditionExpression<? super I> other) {
        List<Function<? super I, CodeBlock>> newAndTerms = new ArrayList<>(andTerms.size() + other.andTerms.size());
        newAndTerms.addAll(andTerms);
        newAndTerms.addAll(other.andTerms);
        return new ConditionExpression<>(newAndTerms);
    }

    public <J> ConditionExpression<J> compose(Function<J, I> function) {
        return new ConditionExpression<>(andTerms.stream()
                .map(f -> f.compose(function))
                .collect(Collectors.toList()));
    }

    public <J> ConditionExpression<J> bind(I value) {
        return new ConditionExpression<>(andTerms.stream()
                .map(fun -> (Function<J, CodeBlock>) ignored -> fun.apply(value))
                .collect(Collectors.toList()));
    }

    public CodeBlock build(I input) {
        if (isAlwaysTrue()) {
            return CodeBlock.of("true");
        }

        CodeBlock.Builder builder = CodeBlock.builder();
        boolean first = true;
        for (Function<? super I, CodeBlock> andTerm : andTerms) {
            if (!first) {
                builder.add(" && ");
            }
            first = false;
            builder.add(andTerm.apply(input));
        }
        return builder.build();
    }

    public boolean isAlwaysTrue() {
        return andTerms.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConditionExpression && ((ConditionExpression<?>) o).andTerms.equals(andTerms);
    }

    @Override
    public int hashCode() {
        return andTerms.hashCode();
    }
}
