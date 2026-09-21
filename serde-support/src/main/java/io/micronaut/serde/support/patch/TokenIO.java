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
package io.micronaut.serde.support.patch;

import io.micronaut.json.tree.JsonNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Token traversal, strict JSON equality, and patch-value materialization. */
final class TokenIO {
    static final TokenWriter DISCARD = (token, text) -> { };

    private TokenIO() {
    }

    static void require(TokenReader reader, PatchToken token) throws IOException {
        if (reader.current() != token) {
            throw new IOException("Expected " + token + ", got " + reader.current());
        }
    }

    static void eof(TokenReader reader) throws IOException {
        if (reader.current() != null) {
            throw new IOException("Unexpected trailing JSON content");
        }
    }

    static void copyValue(TokenReader reader, TokenWriter writer) throws IOException {
        int depth = 0;
        do {
            PatchToken token = reader.current();
            if (token == null || (depth == 0 && (token == PatchToken.KEY || token == PatchToken.END_ARRAY || token == PatchToken.END_OBJECT))) {
                throw new IOException("Expected a JSON value");
            }
            if (token == PatchToken.START_ARRAY || token == PatchToken.START_OBJECT) {
                depth++;
            } else if (token == PatchToken.END_ARRAY || token == PatchToken.END_OBJECT) {
                depth--;
            }
            writer.write(token, reader.text());
            reader.next();
        } while (depth != 0);
    }

    static void emitNode(JsonNode node, TokenWriter writer) throws IOException {
        if (node.isObject()) {
            writer.write(PatchToken.START_OBJECT, "");
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                writer.write(PatchToken.KEY, entry.getKey());
                emitNode(entry.getValue(), writer);
            }
            writer.write(PatchToken.END_OBJECT, "");
        } else if (node.isArray()) {
            writer.write(PatchToken.START_ARRAY, "");
            for (JsonNode child : node.values()) {
                emitNode(child, writer);
            }
            writer.write(PatchToken.END_ARRAY, "");
        } else if (node.isString()) {
            writer.write(PatchToken.STRING, node.getStringValue());
        } else if (node.isNumber()) {
            String text = node.getNumberValue().toString();
            try {
                new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new IOException("Non-JSON numeric patch value", e);
            }
            writer.write(PatchToken.NUMBER, text);
        } else if (node.isBoolean()) {
            writer.write(node.getBooleanValue() ? PatchToken.TRUE : PatchToken.FALSE, "");
        } else {
            writer.write(PatchToken.NULL, "");
        }
    }

    /** Compares while forwarding tokens. Auxiliary memory is bounded by the expected patch value. */
    static void test(TokenReader reader, JsonNode expected, TokenWriter writer) throws IOException {
        PatchToken token = reader.current();
        if (token == PatchToken.START_OBJECT && expected.isObject()) {
            Set<String> seen = new HashSet<>();
            writer.write(token, "");
            reader.next();
            while (reader.current() != PatchToken.END_OBJECT) {
                require(reader, PatchToken.KEY);
                String key = reader.text();
                JsonNode child = expected.get(key);
                if (child == null || !seen.add(key)) {
                    throw new IOException("Test failed");
                }
                writer.write(PatchToken.KEY, key);
                reader.next();
                test(reader, child, writer);
            }
            if (seen.size() != expected.size()) {
                throw new IOException("Test failed");
            }
        } else if (token == PatchToken.START_ARRAY && expected.isArray()) {
            writer.write(token, "");
            reader.next();
            for (JsonNode child : expected.values()) {
                test(reader, child, writer);
            }
            require(reader, PatchToken.END_ARRAY);
        } else {
            boolean equal = switch (token == null ? PatchToken.KEY : token) {
                case NULL -> expected.isNull();
                case TRUE, FALSE -> expected.isBoolean() && expected.getBooleanValue() == (token == PatchToken.TRUE);
                case STRING -> expected.isString() && expected.getStringValue().equals(reader.text());
                case NUMBER -> expected.isNumber() && new BigDecimal(reader.text()).compareTo(new BigDecimal(expected.getNumberValue().toString())) == 0;
                default -> false;
            };
            if (!equal) {
                throw new IOException("Test failed");
            }
        }
        PatchToken end = reader.current();
        if (end == null) {
            throw new IOException("Unexpected end of JSON");
        }
        writer.write(end, reader.text());
        reader.next();
    }

    /** Counts tokens even when callers skip subtrees, retaining no input values. */
    static final class LimitedReader implements TokenReader {
        private final TokenReader delegate;
        private final int maxDepth;
        private final long maxCharacters;
        private int depth;
        private long characters;

        LimitedReader(TokenReader delegate, int maxDepth, long maxCharacters) throws IOException {
            this.delegate = delegate;
            this.maxDepth = maxDepth;
            this.maxCharacters = maxCharacters;
            check();
        }

        private void check() throws IOException {
            PatchToken token = current();
            if (token != null) {
                long count = 1L + text().length();
                if (count > maxCharacters - characters) {
                    throw new IOException("JSON Patch character limit exceeded");
                }
                characters += count;
                if (token == PatchToken.START_ARRAY || token == PatchToken.START_OBJECT) {
                    if (++depth > maxDepth) {
                        throw new IOException("Maximum JSON nesting depth exceeded");
                    }
                } else if (token == PatchToken.END_ARRAY || token == PatchToken.END_OBJECT) {
                    depth--;
                }
            }
        }

        @Override
        public @Nullable PatchToken current() {
            return delegate.current();
        }

        @Override
        public String text() {
            return delegate.text();
        }

        @Override
        public void next() throws IOException {
            delegate.next();
            check();
        }
    }

    static final class LimitedWriter implements TokenWriter {
        private final TokenWriter delegate;
        private final int maxDepth;
        private final long maxBytes;
        private long bytes;
        private int depth;
        private int roots;

        LimitedWriter(TokenWriter delegate, int maxDepth, long maxBytes) {
            this.delegate = delegate;
            this.maxDepth = maxDepth;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(PatchToken token, String text) throws IOException {
            long count = token.hasText() ? 5L + 2L * text.length() : 1;
            if (count > maxBytes - bytes) {
                throw new IOException("JSON Patch output size limit exceeded");
            }
            bytes += count;
            if (depth == 0) {
                roots++;
            }
            if (token == PatchToken.START_ARRAY || token == PatchToken.START_OBJECT) {
                if (++depth > maxDepth) {
                    throw new IOException("Maximum JSON nesting depth exceeded");
                }
            } else if (token == PatchToken.END_ARRAY || token == PatchToken.END_OBJECT) {
                depth--;
            }
            delegate.write(token, text);
        }

        void requireDocument() throws IOException {
            if (roots != 1 || depth != 0) {
                throw new IOException("Patch result has no JSON root");
            }
        }
    }
}
