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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Protects caller-owned streams from parser and generator close policies. */
@Internal
public final class PatchStreams {
    private PatchStreams() {
    }

    /**
     * Protects an input stream.
     * @param input Caller input
     * @return Non-closing wrapper
     */
    public static InputStream input(InputStream input) {
        return new FilterInputStream(input) {
            @Override
            public void close() {
                // The caller owns the stream.
            }
        };
    }

    /**
     * Protects an output stream.
     * @param output Caller output
     * @return Non-closing wrapper
     */
    public static OutputStream output(OutputStream output) {
        return new FilterOutputStream(output) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }
}
