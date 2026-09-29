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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.core.annotation.Internal;

/**
 * Names of the user declarations that generated serde source refers to.
 */
@Internal
public final class SerdeSourceGenNames {

    private SerdeSourceGenNames() {
    }

    /**
     * Escapes a declared name for the Java and Groovy source writers, which take the name as a JavaPoet
     * format string rather than as an argument of one. A {@code $} in a field or method name - legal in
     * Java and used by generated models such as the Kubernetes {@code $ref} and {@code $schema}
     * properties - otherwise reads as a placeholder and fails the generation.
     *
     * @param name The declared name
     * @return The name as a format string that writes it back verbatim
     */
    public static String escapeSourceWriterFormat(String name) {
        return name.replace("$", "$$");
    }

    /**
     * Whether a declared name needs {@link #escapeSourceWriterFormat(String)}.
     *
     * @param name The declared name
     * @return Whether the name carries a format placeholder character
     */
    public static boolean requiresSourceWriterEscape(String name) {
        return name.indexOf('$') >= 0;
    }
}
