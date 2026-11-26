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
package io.micronaut.serde.yaml;

import io.micronaut.core.annotation.Internal;
import org.snakeyaml.engine.v2.api.StreamDataWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The output of the YAML emitter. The emitter writes a document in many small pieces, which an
 * {@link java.io.OutputStreamWriter} encodes one by one, each through a new {@link java.nio.CharBuffer}.
 * This writer collects the pieces and encodes them in blocks, when it holds
 * {@value #BLOCK_SIZE} characters and when it is flushed; a larger piece is encoded in blocks of
 * that size. Like the writer of the emitter, it reports
 * a failure of the stream as an {@link UncheckedIOException}.
 *
 * @since 3.3
 */
@Internal
final class YamlOutputBuffer implements StreamDataWriter {

    static final int BLOCK_SIZE = 8192;
    private static final int INITIAL_SIZE = 256;

    private final OutputStream outputStream;
    private final StringBuilder pending = new StringBuilder(INITIAL_SIZE);
    private boolean failed;

    /**
     * @param outputStream The stream the document is written to
     */
    YamlOutputBuffer(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(String str) {
        write(str, 0, str.length());
    }

    @Override
    public void write(String str, int off, int len) {
        try {
            if (len >= BLOCK_SIZE) {
                // a large piece, such as a long scalar, is encoded in blocks instead of being copied whole
                drain();
                writeBlocks(str, off, off + len);
                return;
            }
            pending.append(str, off, off + len);
            if (pending.length() >= BLOCK_SIZE) {
                drain();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void flush() {
        try {
            drain();
            outputStream.flush();
        } catch (IOException e) {
            failed = true;
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    private void writeBlocks(String str, int start, int end) throws IOException {
        int from = start;
        while (from < end) {
            int to = Math.min(from + BLOCK_SIZE, end);
            if (to < end && Character.isHighSurrogate(str.charAt(to - 1))) {
                // keeps a surrogate pair in one block, so that it is encoded as one character
                to--;
            }
            writeOut(str.substring(from, to));
            from = to;
        }
    }

    private void drain() throws IOException {
        if (!pending.isEmpty() || failed) {
            String text = pending.toString();
            // dropped before writing, so that text that the stream failed to take is not written again
            pending.setLength(0);
            writeOut(text);
        }
    }

    /**
     * Writes text to the stream. Once writing fails, nothing more is written, so that the emitter
     * finishing the document after the failure does not write the same bytes again.
     *
     * @param text The text
     * @throws IOException If the stream fails, or failed before
     */
    private void writeOut(String text) throws IOException {
        if (failed) {
            throw new IOException("A previous write to the output failed");
        }
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            failed = true;
            throw e;
        }
    }
}
