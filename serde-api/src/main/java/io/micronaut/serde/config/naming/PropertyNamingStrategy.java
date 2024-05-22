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
package io.micronaut.serde.config.naming;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;

import java.util.Optional;

/**
 * Allows defining a custom naming strategy for properties.
 *
 * <p>Note that implementations are used only at build time and not at runtime,
 *  therefore adding a new implementation entails ensuring the implementation exists on the annotation processor classpath</p>
 *
 * @since 1.0.0
 */
public interface PropertyNamingStrategy {
    /**
     * Property name as is without changes.
     */
    PropertyNamingStrategy IDENTITY = new IdentityStrategy();

    /**
     * upper first letter, but camel case with a space between. Example: {@code Foo Bar}.
     */
    PropertyNamingStrategy UPPER_CAMEL_CASE_WITH_SPACES = new UpperCamelCaseStrategyWithSpaces();

    /**
     * lower first letter, but camel case. Example: {@code fooBar}.
     */
    PropertyNamingStrategy LOWER_CAMEL_CASE = new LowerCamelCaseStrategy();

    /**
     * upper first letter, but camel case. Example: {@code FooBar}.
     */
    PropertyNamingStrategy UPPER_CAMEL_CASE = new UpperCamelCaseStrategy();

    /**
     * Lowercase, separated by underscores. Example: {@code foo_bar}.
     */
    PropertyNamingStrategy SNAKE_CASE = new SnakeCaseStrategy();

    /**
     * Lowercase, separated by dots. Example: {@code foo.bar}.
     */
    PropertyNamingStrategy LOWER_DOT_CASE = new LowerDotCaseStrategy();

    /**
     * Lowercase, separated by hyphens. Example: {@code foo-bar}.
     */
    PropertyNamingStrategy KEBAB_CASE = new KebabCaseStrategy();

    /**
     * All lower case. Example: {@code foobar}.
     */
    PropertyNamingStrategy LOWER_CASE = new LowerCaseStrategy();

    /**
     * Translate the given name into the desired format.
     * @param element The annotated element to translate
     * @return The translated name
     */
    @NonNull String translate(@NonNull AnnotatedElement element);

    /**
     * Return an existing naming strategy for each name.
     * @param namingStrategy The naming strategy name.
     * @return The naming strategy
     */
    static Optional<PropertyNamingStrategy> forName(String namingStrategy) {
        if (StringUtils.isNotEmpty(namingStrategy)) {
            return switch (namingStrategy) {
                case "KEBAB_CASE", "io.micronaut.serde.config.naming.KebabCaseStrategy" ->
                    Optional.of(KEBAB_CASE);
                case "IDENTITY", "io.micronaut.serde.config.naming.IdentityStrategy" ->
                    Optional.of(IDENTITY);
                case "LOWER_CASE", "io.micronaut.serde.config.naming.LowerCaseStrategy" ->
                    Optional.of(LOWER_CASE);
                case "LOWER_DOT_CASE", "io.micronaut.serde.config.naming.LowerDotCaseStrategy" ->
                    Optional.of(LOWER_DOT_CASE);
                case "SNAKE_CASE", "io.micronaut.serde.config.naming.SnakeCaseStrategy" ->
                    Optional.of(SNAKE_CASE);
                case "UPPER_CAMEL_CASE",
                     "io.micronaut.serde.config.naming.UpperCamelCaseStrategy" ->
                    Optional.of(UPPER_CAMEL_CASE);
                case "LOWER_CAMEL_CASE",
                     "io.micronaut.serde.config.naming.LowerCamelCaseStrategy" ->
                    Optional.of(LOWER_CAMEL_CASE);
                case "UPPER_CAMEL_CASE_WITH_SPACES",
                     "io.micronaut.serde.config.naming.UpperCamelCaseStrategyWithSpaces" ->
                    Optional.of(UPPER_CAMEL_CASE_WITH_SPACES);
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }
}
