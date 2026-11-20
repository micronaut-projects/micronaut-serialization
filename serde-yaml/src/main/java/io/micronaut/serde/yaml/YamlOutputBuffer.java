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
 * {@value #BLOCK_SIZE} characters and when it is flushed. Like the writer of the emitter, it reports
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

    /**
     * @param outputStream The stream the document is written to
     */
    YamlOutputBuffer(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(String str) {
        pending.append(str);
        drainIfFull();
    }

    @Override
    public void write(String str, int off, int len) {
        pending.append(str, off, off + len);
        drainIfFull();
    }

    @Override
    public void flush() {
        try {
            drain();
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void drainIfFull() {
        if (pending.length() >= BLOCK_SIZE) {
            try {
                drain();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void drain() throws IOException {
        if (!pending.isEmpty()) {
            outputStream.write(pending.toString().getBytes(StandardCharsets.UTF_8));
            pending.setLength(0);
        }
    }
}
