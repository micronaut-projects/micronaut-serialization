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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.patch.JsonPatch;
import io.micronaut.serde.patch.JsonPatchException;
import io.micronaut.serde.patch.JsonPatchOptions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sequential RFC 6902 evaluator over replayable token streams. */
@Internal
public final class PatchEngine {
    private static final Argument<List<JsonNode>> PATCH_TYPE = Argument.listOf(JsonNode.class);

    private PatchEngine() {
    }

    /**
     * Deserializes only the patch document through serde, then validates its operations.
     * @param input Patch token cursor
     * @param context Mapper decoder context
     * @param options Resource limits
     * @param maxDepth Maximum JSON depth
     * @param limits Decoder nesting limits
     * @return Validated patch
     * @throws IOException If the patch is invalid
     */
    public static JsonPatch readPatch(TokenReader input, Deserializer.DecoderContext context, JsonPatchOptions options,
                                      int maxDepth, LimitingStream.RemainingLimits limits) throws IOException {
        var reader = new PatchDocumentReader(new TokenIO.LimitedReader(input, maxDepth, options.maxPatchCharacters()), options.maxOperations());
        List<JsonNode> document;
        try (var scope = new ReplayStore.Scope(options)) {
            var decoder = new PatchedDecoder(reader, limits, CoercionPolicy.STRICT, scope, null);
            Deserializer<? extends List<JsonNode>> deserializer = context.findDeserializer(PATCH_TYPE).createSpecific(context, PATCH_TYPE);
            document = deserializer.deserialize(decoder, context, PATCH_TYPE);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid JSON Patch number", e);
        }
        TokenIO.eof(reader);
        List<JsonPatch.Operation> operations = new ArrayList<>(document.size());
        for (int index = 0; index < document.size(); index++) {
            String op = "";
            String path = "";
            try {
                JsonNode fields = document.get(index);
                op = stringField(fields, "op");
                path = stringField(fields, "path");
                boolean source = op.equals("copy") || op.equals("move");
                boolean value = op.equals("add") || op.equals("replace") || op.equals("test");
                if (reader.hasDuplicates(index, source, value)) {
                    throw new IOException("Duplicate operation member");
                }
                operations.add(new JsonPatch.Operation(op, path, source ? stringField(fields, "from") : null,
                    value ? fields.get("value") : null));
            } catch (IOException | IllegalArgumentException e) {
                throw failure(index, op, path, e);
            }
        }
        return new JsonPatch(operations);
    }

    private static String stringField(JsonNode fields, String name) throws IOException {
        JsonNode node = fields.get(name);
        if (node == null || !node.isString()) {
            throw new IOException("Missing or non-string '" + name + "'");
        }
        return node.getStringValue();
    }

    /**
     * Applies and validates a patch before exposing its result to a deserializer.
     * @param input Source token cursor
     * @param patch Patch
     * @param options Resource limits
     * @param maxDepth Maximum nesting depth
     * @return Closeable validated result
     * @throws IOException If patch application fails
     */
    public static Result apply(TokenReader input, JsonPatch patch, JsonPatchOptions options, int maxDepth) throws IOException {
        ReplayStore.Scope scope = new ReplayStore.Scope(options);
        try {
            ReplayStore store = Objects.requireNonNull(evaluate(input, patch, scope, maxDepth, null));
            TokenReader reader = store.reader();
            if (reader.current() == null) {
                reader.close();
                throw new JsonPatchException(patch.operations().size() - 1, "", "", "Patch result has no JSON root");
            }
            return new Result(scope, reader);
        } catch (IOException | RuntimeException | Error e) {
            try {
                scope.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * Writes a patched document, optionally staging the complete result before output.
     * @param input Source tokens
     * @param patch Patch
     * @param writer Destination tokens
     * @param options Resource and output policy
     * @param maxDepth Maximum nesting depth
     * @throws IOException If patching or output fails
     */
    public static void write(TokenReader input, JsonPatch patch, TokenWriter writer, JsonPatchOptions options, int maxDepth) throws IOException {
        if (options.validateBeforeWrite()) {
            try (Result result = apply(input, patch, options, maxDepth)) {
                TokenIO.copyValue(result.reader(), writer);
            }
        } else {
            try (var scope = new ReplayStore.Scope(options)) {
                var limited = new TokenIO.LimitedWriter(writer, maxDepth, options.storageLimit());
                evaluate(input, patch, scope, maxDepth, limited);
                limited.requireDocument();
            }
        }
    }

    /**
     * Independent immediate object-member edits can share a pass. Otherwise each operation
     * finishes before its successor starts, and only the final operation can write to the sink.
     */
    private static @Nullable ReplayStore evaluate(TokenReader input, JsonPatch patch, ReplayStore.Scope scope,
                                                  int maxDepth, @Nullable TokenWriter finalWriter) throws IOException {
        checkPatch(patch, scope.options, maxDepth);
        TokenReader current = new TokenIO.LimitedReader(input, maxDepth, Long.MAX_VALUE);
        if (current.current() == null) {
            throw new IOException("No JSON input");
        }
        Map<String, Integer> independent = current.current() == PatchToken.START_OBJECT ? independentObjectEdits(patch) : null;
        if (independent != null) {
            ReplayStore result = finalWriter == null ? new ReplayStore(scope) : null;
            TokenWriter destination = result == null ? Objects.requireNonNull(finalWriter) : result;
            var output = new TokenIO.LimitedWriter(destination, maxDepth, scope.options.storageLimit());
            rewriteIndependentObject(current, patch, independent, output);
            output.requireDocument();
            return result;
        }
        ReplayStore previous = null;
        try {
            int count = Math.max(1, patch.operations().size());
            for (int index = 0; index < count; index++) {
                boolean last = index == count - 1;
                ReplayStore next = last && finalWriter != null ? null : new ReplayStore(scope);
                TokenWriter destination = next == null ? Objects.requireNonNull(finalWriter) : next;
                var output = new TokenIO.LimitedWriter(destination, maxDepth, scope.options.storageLimit());
                JsonPatch.Operation operation = patch.operations().isEmpty() ? null : patch.operations().get(index);
                try {
                    if (operation == null) {
                        TokenIO.copyValue(current, output);
                        TokenIO.eof(current);
                    } else {
                        perform(current, previous, operation, output, scope, maxDepth);
                    }
                    if (last) {
                        output.requireDocument();
                    }
                } catch (IOException | IllegalArgumentException e) {
                    throw failure(index, operation == null ? "" : operation.op(), operation == null ? "" : operation.path(), e);
                }
                if (previous != null) {
                    current.close();
                    previous.close();
                    previous = null;
                }
                if (last) {
                    return next;
                }
                previous = Objects.requireNonNull(next);
                current = previous.reader();
            }
            throw new IllegalStateException("No result");
        } finally {
            if (previous != null) {
                current.close();
            }
        }
    }

    /**
     * Only distinct immediate object members commute without array-index or ancestor dependencies.
     * Keep this eligibility test deliberately narrow; everything else uses sequential replay.
     */
    private static @Nullable Map<String, Integer> independentObjectEdits(JsonPatch patch) {
        if (patch.operations().size() < 2) {
            return null;
        }
        Map<String, Integer> edits = new HashMap<>();
        for (int i = 0; i < patch.operations().size(); i++) {
            JsonPatch.Operation operation = patch.operations().get(i);
            if (operation.pathTokens().size() != 1
                || !(operation.op().equals("add") || operation.op().equals("replace") || operation.op().equals("remove"))
                || edits.put(operation.pathTokens().getFirst(), i) != null) {
                return null;
            }
        }
        return edits;
    }

    private static void rewriteIndependentObject(TokenReader input, JsonPatch patch, Map<String, Integer> edits,
                                                  TokenWriter output) throws IOException {
        int[] occurrences = new int[patch.operations().size()];
        output.write(PatchToken.START_OBJECT, "");
        input.next();
        while (input.current() != PatchToken.END_OBJECT) {
            TokenIO.require(input, PatchToken.KEY);
            String key = input.text();
            input.next();
            Integer index = edits.get(key);
            if (index == null) {
                output.write(PatchToken.KEY, key);
                TokenIO.copyValue(input, output);
            } else {
                JsonPatch.Operation operation = patch.operations().get(index);
                TokenIO.copyValue(input, TokenIO.DISCARD);
                if (occurrences[index] == 0 && !operation.op().equals("remove")) {
                    output.write(PatchToken.KEY, key);
                    TokenIO.emitNode(Objects.requireNonNull(operation.value()), output);
                }
                occurrences[index] = Math.min(2, occurrences[index] + 1);
            }
        }
        // Source member order can differ from patch order. Report semantic failures in patch
        // order after every referenced member's existence and uniqueness have been established.
        for (int i = 0; i < occurrences.length; i++) {
            JsonPatch.Operation operation = patch.operations().get(i);
            if (occurrences[i] > 1 || (occurrences[i] == 0 && !operation.op().equals("add"))) {
                throw new JsonPatchException(i, operation.op(), operation.path(),
                    occurrences[i] > 1 ? "Referenced object member is not unique" : "Target member does not exist");
            }
        }
        for (int i = 0; i < occurrences.length; i++) {
            if (occurrences[i] == 0) {
                JsonPatch.Operation operation = patch.operations().get(i);
                output.write(PatchToken.KEY, operation.pathTokens().getFirst());
                TokenIO.emitNode(Objects.requireNonNull(operation.value()), output);
            }
        }
        output.write(PatchToken.END_OBJECT, "");
        input.next();
        TokenIO.eof(input);
    }

    private static void checkPatch(JsonPatch patch, JsonPatchOptions options, int maxDepth) throws IOException {
        if (patch.operations().size() > options.maxOperations()) {
            throw new IOException("JSON Patch operation count limit exceeded");
        }
        long characters = 0;
        for (JsonPatch.Operation operation : patch.operations()) {
            characters += operation.op().length() + (long) operation.path().length() + 1;
            String from = operation.from();
            if (from != null) {
                characters += from.length();
            }
            if (characters > options.maxPatchCharacters()) {
                throw new IOException("JSON Patch character limit exceeded");
            }
            JsonNode value = operation.value();
            if (value != null) {
                long[] remaining = {options.maxPatchCharacters() - characters};
                TokenWriter counter = (token, text) -> {
                    remaining[0] -= 1L + text.length();
                    if (remaining[0] < 0) {
                        throw new IOException("JSON Patch character limit exceeded");
                    }
                };
                TokenIO.emitNode(value, new TokenIO.LimitedWriter(counter, maxDepth, Long.MAX_VALUE));
                characters = options.maxPatchCharacters() - remaining[0];
            }
        }
    }

    private static void perform(TokenReader input, @Nullable ReplayStore previous, JsonPatch.Operation operation,
                                TokenWriter output, ReplayStore.Scope scope, int maxDepth) throws IOException {
        if (!operation.op().equals("copy") && !operation.op().equals("move")) {
            JsonNode value = operation.value();
            ValueWriter replacement = writer -> TokenIO.emitNode(Objects.requireNonNull(value), writer);
            rewrite(input, output, operation.pathTokens(), 0, operation.op(), replacement, value);
            TokenIO.eof(input);
            return;
        }
        ReplayStore source = previous;
        boolean ownsSource = source == null;
        if (source == null) {
            source = new ReplayStore(scope);
            TokenIO.copyValue(input, source);
            TokenIO.eof(input);
        }
        try (var captured = new ReplayStore(scope)) {
            try (TokenReader reader = source.reader()) {
                try {
                    find(reader, operation.fromTokens(), 0, captured);
                    TokenIO.eof(reader);
                } catch (IOException e) {
                    throw new IOException("Source '" + operation.from() + "': " + e.getMessage(), e);
                }
            }
            ValueWriter replacement = writer -> {
                try (TokenReader value = captured.reader()) {
                    TokenIO.copyValue(value, writer);
                }
            };
            if (operation.op().equals("move")) {
                try (var removed = new ReplayStore(scope)) {
                    try (TokenReader reader = source.reader()) {
                        rewrite(reader, new TokenIO.LimitedWriter(removed, maxDepth, scope.options.storageLimit()),
                            operation.fromTokens(), 0, "remove", replacement, null);
                    }
                    try (TokenReader reader = removed.reader()) {
                        rewrite(reader, output, operation.pathTokens(), 0, "add", replacement, null);
                    }
                }
            } else {
                try (TokenReader reader = source.reader()) {
                    rewrite(reader, output, operation.pathTokens(), 0, "add", replacement, null);
                }
            }
        } finally {
            if (ownsSource) {
                source.close();
            }
        }
    }

    /** Rewrites one path, forwarding all unrelated values without materializing them. */
    private static void rewrite(TokenReader input, TokenWriter output, List<String> path, int level,
                                String op, ValueWriter replacement, @Nullable JsonNode expected) throws IOException {
        if (level == path.size()) {
            if (input.current() == null && !op.equals("add")) {
                throw new IOException("Target does not exist");
            }
            if (op.equals("test")) {
                TokenIO.test(input, Objects.requireNonNull(expected), output);
            } else {
                if (input.current() != null) {
                    TokenIO.copyValue(input, TokenIO.DISCARD);
                }
                if (!op.equals("remove")) {
                    replacement.write(output);
                }
            }
            return;
        }
        String component = path.get(level);
        boolean leaf = level + 1 == path.size();
        if (input.current() == PatchToken.START_OBJECT) {
            output.write(PatchToken.START_OBJECT, "");
            input.next();
            boolean found = false;
            while (input.current() != PatchToken.END_OBJECT) {
                TokenIO.require(input, PatchToken.KEY);
                String key = input.text();
                input.next();
                if (key.equals(component)) {
                    if (found) {
                        throw new IOException("Referenced object member is not unique");
                    }
                    found = true;
                    if (!(leaf && op.equals("remove"))) {
                        output.write(PatchToken.KEY, key);
                    }
                    rewrite(input, output, path, level + 1, op, replacement, expected);
                } else {
                    output.write(PatchToken.KEY, key);
                    TokenIO.copyValue(input, output);
                }
            }
            if (!found) {
                if (leaf && op.equals("add")) {
                    output.write(PatchToken.KEY, component);
                    replacement.write(output);
                } else {
                    throw new IOException("Target parent or member does not exist");
                }
            }
            output.write(PatchToken.END_OBJECT, "");
            input.next();
        } else if (input.current() == PatchToken.START_ARRAY) {
            boolean insert = leaf && op.equals("add");
            long target = index(component, insert);
            long position = 0;
            boolean found = false;
            output.write(PatchToken.START_ARRAY, "");
            input.next();
            while (input.current() != PatchToken.END_ARRAY) {
                if (position == target) {
                    found = true;
                    if (insert) {
                        replacement.write(output);
                        TokenIO.copyValue(input, output);
                    } else {
                        rewrite(input, output, path, level + 1, op, replacement, expected);
                    }
                } else {
                    TokenIO.copyValue(input, output);
                }
                position++;
            }
            if (!found) {
                if (insert && (target == position || target == -1)) {
                    replacement.write(output);
                } else {
                    throw new IOException("Array index out of bounds");
                }
            }
            output.write(PatchToken.END_ARRAY, "");
            input.next();
        } else {
            throw new IOException("Cannot traverse a scalar or absent value");
        }
    }

    private static void find(TokenReader input, List<String> path, int level, TokenWriter captured) throws IOException {
        if (level == path.size()) {
            TokenIO.copyValue(input, captured);
            return;
        }
        String component = path.get(level);
        boolean found = false;
        if (input.current() == PatchToken.START_OBJECT) {
            input.next();
            while (input.current() != PatchToken.END_OBJECT) {
                TokenIO.require(input, PatchToken.KEY);
                String key = input.text();
                input.next();
                if (key.equals(component)) {
                    if (found) {
                        throw new IOException("Referenced object member is not unique");
                    }
                    found = true;
                    find(input, path, level + 1, captured);
                } else {
                    TokenIO.copyValue(input, TokenIO.DISCARD);
                }
            }
            input.next();
        } else if (input.current() == PatchToken.START_ARRAY) {
            long target = index(component, false);
            long position = 0;
            input.next();
            while (input.current() != PatchToken.END_ARRAY) {
                if (position++ == target) {
                    found = true;
                    find(input, path, level + 1, captured);
                } else {
                    TokenIO.copyValue(input, TokenIO.DISCARD);
                }
            }
            input.next();
        }
        if (!found) {
            throw new IOException("Source does not exist");
        }
    }

    private static long index(String text, boolean allowAppend) throws IOException {
        if (allowAppend && text.equals("-")) {
            return -1;
        }
        if (text.isEmpty() || (text.length() > 1 && text.charAt(0) == '0')) {
            throw new IOException("Invalid array index: " + text);
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                throw new IOException("Invalid array index: " + text);
            }
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IOException("Array index out of bounds: " + text, e);
        }
    }

    private static JsonPatchException failure(int index, String op, String path, Exception cause) {
        var failure = new JsonPatchException(index, op, path, Objects.toString(cause.getMessage(), "Patch failed"));
        failure.initCause(cause);
        return failure;
    }

    @FunctionalInterface
    private interface ValueWriter {
        void write(TokenWriter writer) throws IOException;
    }

    /** Validated token result. Closing it releases all replay storage. */
    @Internal
    public static final class Result implements AutoCloseable {
        private final ReplayStore.Scope scope;
        private final TokenReader reader;

        private Result(ReplayStore.Scope scope, TokenReader reader) {
            this.scope = scope;
            this.reader = reader;
        }

        /**
         * Returns result cursor.
         * @return Result cursor
         */
        public TokenReader reader() {
            return reader;
        }

        /**
         * Creates the serde decoder, sharing the execution's bounded replay storage.
         * @param limits Decoder limits
         * @param policy Mapper coercion policy
         * @return Result decoder
         */
        public Decoder decoder(LimitingStream.RemainingLimits limits, CoercionPolicy policy) {
            return new PatchedDecoder(reader, limits, policy, scope, null);
        }

        @Override
        public void close() throws IOException {
            try {
                reader.close();
            } finally {
                scope.close();
            }
        }
    }
}
