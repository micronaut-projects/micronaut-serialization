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
package io.micronaut.serde.xml;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * The output of an XML writer. The JDK writer encodes text one byte at a time, which a
 * {@link java.io.ByteArrayOutputStream} serves with a lock and a capacity check per byte. This
 * buffer is not synchronized; without a target it grows to hold the document, with a target it
 * passes its content on whenever it is full and when it is flushed. Once writing to the target
 * fails, the buffer drops its content and writes nothing more, so that closing the writer after the
 * failure does not write the same bytes again.
 *
 * @since 3.3
 */
@Internal
final class XmlOutputBuffer extends OutputStream {

    static final int INITIAL_SIZE = 512;
    static final int TARGET_BUFFER_SIZE = 8192;

    @Nullable
    private final OutputStream target;
    private byte[] buffer;
    private int count;
    private boolean failed;

    /**
     * A buffer that holds the whole document.
     */
    XmlOutputBuffer() {
        this.target = null;
        this.buffer = new byte[INITIAL_SIZE];
    }

    /**
     * A buffer that passes its content on to a target.
     *
     * @param target The target
     */
    XmlOutputBuffer(OutputStream target) {
        this.target = target;
        this.buffer = new byte[TARGET_BUFFER_SIZE];
    }

    @Override
    public void write(int b) throws IOException {
        if (count == buffer.length) {
            makeRoom(1);
        }
        buffer[count++] = (byte) b;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        if (length > buffer.length - count) {
            makeRoom(length);
            OutputStream out = target;
            if (out != null && length > buffer.length) {
                writeOut(out, bytes, offset, length);
                return;
            }
        }
        System.arraycopy(bytes, offset, buffer, count, length);
        count += length;
    }

    @Override
    public void flush() throws IOException {
        OutputStream out = target;
        if (out != null) {
            drain(out);
            try {
                out.flush();
            } catch (IOException | RuntimeException e) {
                failed = true;
                throw e;
            }
        }
    }

    /**
     * @return The bytes written
     */
    byte[] toByteArray() {
        return Arrays.copyOf(buffer, count);
    }

    private void makeRoom(int length) throws IOException {
        OutputStream out = target;
        if (out != null) {
            drain(out);
        } else {
            buffer = Arrays.copyOf(buffer, Math.max(count + length, buffer.length << 1));
        }
    }

    private void drain(OutputStream out) throws IOException {
        int length = count;
        // dropped before writing, so that bytes that the target failed to take are not written again
        count = 0;
        if (length > 0 || failed) {
            writeOut(out, buffer, 0, length);
        }
    }

    private void writeOut(OutputStream out, byte[] bytes, int offset, int length) throws IOException {
        if (failed) {
            throw new IOException("A previous write to the output failed");
        }
        try {
            out.write(bytes, offset, length);
        } catch (IOException | RuntimeException e) {
            failed = true;
            throw e;
        }
    }
}
