/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.toon;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;

import java.util.Objects;

/**
 * TOON-specific configuration.
 *
 * @since 3.2.0
 */
@BootstrapContextCompatible
@Internal
@ConfigurationProperties(SerdeToonConfiguration.PREFIX)
public final class SerdeToonConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".format.toon";

    private Delimiter delimiter = Delimiter.COMMA;
    private int indent = 2;

    /**
     * Returns the configured delimiter used to separate inline array values,
     * tabular row cells, and keyed entry-row cells.
     *
     * @return The configured delimiter
     */
    public Delimiter getDelimiter() {
        return delimiter;
    }

    /**
     * Sets the delimiter used to separate inline array values, tabular row
     * cells, and keyed entry-row cells.
     *
     * @param delimiter The delimiter
     */
    public void setDelimiter(Delimiter delimiter) {
        this.delimiter = Objects.requireNonNull(delimiter, "delimiter");
    }

    /**
     * Returns the configured number of spaces used per indentation level.
     *
     * @return The indent size
     */
    public int getIndent() {
        return indent;
    }

    /**
     * Sets the number of spaces used per indentation level.
     *
     * @param indent The indent size
     */
    public void setIndent(int indent) {
        if (indent < 1) {
            throw new IllegalArgumentException("indent must be at least 1");
        }
        this.indent = indent;
    }

    /**
     * The delimiter used to separate values within a TOON document, as defined
     * by the specification.
     *
     * @since 3.2.0
     */
    public enum Delimiter {
        /**
         * Comma ({@code ,}); the default delimiter.
         */
        COMMA(','),

        /**
         * Horizontal tab (U+0009).
         */
        TAB('\t'),

        /**
         * Pipe ({@code |}).
         */
        PIPE('|');

        private final char character;

        Delimiter(char character) {
            this.character = character;
        }

        /**
         * Returns the literal character for this delimiter.
         *
         * @return The delimiter character
         */
        public char getCharacter() {
            return character;
        }
    }
}
