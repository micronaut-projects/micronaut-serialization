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

import io.micronaut.serde.patch.JsonPatchOptions;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Binary token tape with an execution-wide memory and storage budget. */
final class ReplayStore extends OutputStream implements TokenWriter, AutoCloseable {
    private static final int CHUNK_SIZE = 8192;
    private static final PatchToken[] TOKENS = PatchToken.values();
    private final Scope scope;
    private final List<byte[]> chunks = new ArrayList<>();
    private final Set<Reader> readers = new LinkedHashSet<>();
    private final DataOutputStream data = new DataOutputStream(this);
    private @Nullable OutputStream fileOutput;
    private @Nullable Path file;
    private long size;
    private boolean sealed;
    private boolean closed;

    ReplayStore(Scope scope) {
        this.scope = scope;
        scope.stores.add(this);
    }

    @Override
    @SuppressWarnings("EnumOrdinal") // Ephemeral tapes are read only by this same running engine.
    public void write(PatchToken token, String text) throws IOException {
        data.writeByte(token.ordinal());
        if (token.hasText()) {
            // UTF-16 preserves Java strings, including unpaired surrogate code units, without a
            // second scalar-sized allocation. No modified-UTF 64 KiB string limit applies.
            data.writeInt(text.length());
            data.writeChars(text);
        }
    }

    @Override
    public void write(int value) throws IOException {
        if (sealed || closed) {
            throw new IOException("Replay store is not writable");
        }
        if (scope.bytes == scope.options.storageLimit()) {
            throw new IOException("JSON Patch replay storage limit exceeded");
        }
        if (fileOutput == null && size % CHUNK_SIZE == 0) {
            if (scope.memory + CHUNK_SIZE <= scope.options.memoryLimit()) {
                chunks.add(new byte[CHUNK_SIZE]);
                scope.memory += CHUNK_SIZE;
            } else {
                spill();
            }
        }
        OutputStream output = fileOutput;
        if (output == null) {
            chunks.get((int) (size / CHUNK_SIZE))[(int) (size % CHUNK_SIZE)] = (byte) value;
        } else {
            output.write(value);
        }
        size++;
        scope.bytes++;
    }

    private void spill() throws IOException {
        Path directory = scope.options.spillDirectory();
        if (directory == null) {
            throw new IOException("JSON Patch replay memory limit exceeded; configure a spill directory");
        }
        file = Files.createTempFile(directory, "micronaut-json-patch-", ".tokens");
        fileOutput = new BufferedOutputStream(Files.newOutputStream(file));
        long remaining = size;
        for (byte[] chunk : chunks) {
            int count = (int) Math.min(remaining, CHUNK_SIZE);
            fileOutput.write(chunk, 0, count);
            remaining -= count;
        }
        releaseMemory();
    }

    TokenReader reader() throws IOException {
        if (closed) {
            throw new IOException("Replay store is closed");
        }
        sealed = true;
        InputStream input;
        if (fileOutput != null) {
            fileOutput.flush();
            input = Files.newInputStream(Objects.requireNonNull(file));
        } else {
            List<InputStream> inputs = new ArrayList<>(chunks.size());
            long remaining = size;
            for (byte[] chunk : chunks) {
                int count = (int) Math.min(remaining, CHUNK_SIZE);
                inputs.add(new ByteArrayInputStream(chunk, 0, count));
                remaining -= count;
            }
            input = new SequenceInputStream(Collections.enumeration(inputs));
        }
        Reader reader = new Reader(input, this);
        readers.add(reader);
        return reader;
    }

    private void releaseMemory() {
        scope.memory -= (long) chunks.size() * CHUNK_SIZE;
        chunks.clear();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            scope.stores.remove(this);
            scope.bytes -= size;
            releaseMemory();
            IOException failure = null;
            for (Reader reader : List.copyOf(readers)) {
                try {
                    reader.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            try {
                if (fileOutput != null) {
                    fileOutput.close();
                }
            } finally {
                if (file != null) {
                    Files.deleteIfExists(file);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** Owns every tape until the operation or the complete invocation releases it. */
    static final class Scope implements AutoCloseable {
        final JsonPatchOptions options;
        final Set<ReplayStore> stores = new LinkedHashSet<>();
        long memory;
        long bytes;

        Scope(JsonPatchOptions options) {
            this.options = options;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (ReplayStore store : List.copyOf(stores)) {
                try {
                    store.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class Reader implements TokenReader {
        private final DataInputStream input;
        private final ReplayStore owner;
        private @Nullable PatchToken current;
        private String text = "";

        Reader(InputStream input, ReplayStore owner) throws IOException {
            this.owner = owner;
            this.input = new DataInputStream(new BufferedInputStream(input));
            try {
                next();
            } catch (IOException | RuntimeException e) {
                input.close();
                throw e;
            }
        }

        @Override
        public @Nullable PatchToken current() {
            return current;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public void next() throws IOException {
            int code = input.read();
            current = code == -1 ? null : TOKENS[code];
            text = "";
            if (current != null && current.hasText()) {
                int length = input.readInt();
                StringBuilder builder = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    builder.append(input.readChar());
                }
                text = builder.toString();
            }
        }

        @Override
        public void close() throws IOException {
            owner.readers.remove(this);
            input.close();
        }
    }
}
