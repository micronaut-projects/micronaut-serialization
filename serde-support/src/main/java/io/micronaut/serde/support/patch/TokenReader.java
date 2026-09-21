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
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/** Forward-only cursor, initially positioned at its first token. */
@Internal
public interface TokenReader extends AutoCloseable {
    /**
     * Returns current token, or null at EOF.
     * @return Current token, or null at EOF
     */
    @Nullable PatchToken current();

    /**
     * Returns text for a key, string or number token.
     * @return Text for a key, string or number token
     */
    String text();

    /**
     * Advances to the next token.
     * @throws IOException If reading fails
     */
    void next() throws IOException;

    @Override
    default void close() throws IOException {
    }
}
