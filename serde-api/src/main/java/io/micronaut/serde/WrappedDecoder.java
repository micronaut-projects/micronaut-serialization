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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;

import java.io.IOException;

/**
 * Decoder wrapper that exposes the wrapped decoder for backend-specific integrations.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public interface WrappedDecoder extends Decoder {

    /**
     * Returns the wrapped decoder.
     *
     * @return The wrapped decoder
     * @throws IOException If an unrecoverable error occurs
     */
    Decoder wrappedDecoder() throws IOException;

    /**
     * Unwrap all nested decoder wrappers.
     *
     * @param decoder The decoder
     * @return The unwrapped decoder
     * @throws IOException If an unrecoverable error occurs
     */
    static Decoder unwrap(Decoder decoder) throws IOException {
        while (decoder instanceof WrappedDecoder wrappedDecoder) {
            decoder = wrappedDecoder.wrappedDecoder();
        }
        return decoder;
    }
}
