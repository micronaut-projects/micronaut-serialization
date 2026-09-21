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
package io.micronaut.serde.patch;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, reusable RFC 6902 patch. Only patch values are materialized; the target document is not.
 *
 * @param operations Operations in execution order
 * @since 3.2.0
 */
@Experimental
public record JsonPatch(List<Operation> operations) {
    /**
     * Creates a patch with an immutable operation list.
     * @param operations Operations in execution order
     * @since 3.2.0
     */
    public JsonPatch {
        operations = List.copyOf(operations);
    }

    /**
     * A validated operation with precompiled JSON pointers.
     * @since 3.2.0
     */
    @Experimental
    public static final class Operation {
        private static final Set<String> NAMES = Set.of("add", "remove", "replace", "move", "copy", "test");
        private final String op;
        private final String path;
        private final @Nullable String from;
        private final @Nullable JsonNode value;
        private final List<String> pathTokens;
        private final List<String> fromTokens;

        /**
         * Creates an operation. Java {@code null} means an absent value member; use
         * {@link JsonNode#nullNode()} for a JSON null value.
         * @param op Operation name
         * @param path Target pointer
         * @param from Source pointer for move and copy
         * @param value Value for add, replace and test
         * @throws IllegalArgumentException If the operation is malformed
         * @since 3.2.0
         */
        public Operation(String op, String path, @Nullable String from, @Nullable JsonNode value) {
            this.op = Objects.requireNonNull(op);
            this.path = Objects.requireNonNull(path);
            if (!NAMES.contains(op)) {
                throw new IllegalArgumentException("Unknown JSON Patch operation: " + op);
            }
            this.pathTokens = pointer(path);
            boolean sourceRequired = op.equals("move") || op.equals("copy");
            if (sourceRequired && from == null) {
                throw new IllegalArgumentException("Missing 'from'");
            }
            if ((op.equals("add") || op.equals("replace") || op.equals("test")) && value == null) {
                throw new IllegalArgumentException("Missing 'value'");
            }
            this.from = sourceRequired ? from : null;
            this.fromTokens = sourceRequired ? pointer(Objects.requireNonNull(from)) : List.of();
            this.value = op.equals("add") || op.equals("replace") || op.equals("test") ? value : null;
            if (op.equals("move") && pathTokens.size() > fromTokens.size()
                && pathTokens.subList(0, fromTokens.size()).equals(fromTokens)) {
                throw new IllegalArgumentException("Cannot move a value into its descendant");
            }
        }

        /**
         * Returns operation name.
         * @return Operation name
         * @since 3.2.0
         */
        public String op() {
            return op;
        }

        /**
         * Returns target pointer.
         * @return Target pointer
         * @since 3.2.0
         */
        public String path() {
            return path;
        }

        /**
         * Returns source pointer, if required.
         * @return Source pointer, if required
         * @since 3.2.0
         */
        public @Nullable String from() {
            return from;
        }

        /**
         * Returns operation value, or null when absent.
         * @return Operation value, or null when absent
         * @since 3.2.0
         */
        public @Nullable JsonNode value() {
            return value;
        }

        /**
         * Returns compiled target pointer.
         * @return Compiled target pointer
         */
        @Internal
        public List<String> pathTokens() {
            return pathTokens;
        }

        /**
         * Returns compiled source pointer.
         * @return Compiled source pointer
         */
        @Internal
        public List<String> fromTokens() {
            return fromTokens;
        }

        private static List<String> pointer(String pointer) {
            if (pointer.isEmpty()) {
                return List.of();
            }
            if (pointer.charAt(0) != '/') {
                throw new IllegalArgumentException("JSON pointer must be empty or start with '/'");
            }
            List<String> tokens = new ArrayList<>();
            for (String token : pointer.substring(1).split("/", -1)) {
                StringBuilder decoded = new StringBuilder();
                for (int i = 0; i < token.length(); i++) {
                    char c = token.charAt(i);
                    if (c == '~') {
                        if (++i == token.length() || (token.charAt(i) != '0' && token.charAt(i) != '1')) {
                            throw new IllegalArgumentException("Invalid JSON pointer escape");
                        }
                        c = token.charAt(i) == '0' ? '~' : '/';
                    }
                    decoded.append(c);
                }
                tokens.add(decoded.toString());
            }
            return List.copyOf(tokens);
        }
    }
}
