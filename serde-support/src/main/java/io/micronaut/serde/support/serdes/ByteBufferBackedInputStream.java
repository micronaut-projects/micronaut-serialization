/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support.serdes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Fork from `com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream` licensed with Apache 2.0. License.
 * Simple {@link InputStream} implementation that exposes currently
 * available content of a {@link ByteBuffer}.
 */
public class ByteBufferBackedInputStream extends InputStream {
    protected final ByteBuffer _b;

    public ByteBufferBackedInputStream(ByteBuffer buf) { _b = buf; }

    @Override public int available() { return _b.remaining(); }

    @Override
    public int read() throws IOException { return _b.hasRemaining() ? (_b.get() & 0xFF) : -1; }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        if (!_b.hasRemaining()) return -1;
        len = Math.min(len, _b.remaining());
        _b.get(bytes, off, len);
        return len;
    }
}
