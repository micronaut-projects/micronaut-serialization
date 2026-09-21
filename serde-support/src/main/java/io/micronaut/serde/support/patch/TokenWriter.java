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

import java.io.IOException;

/** Consumer of JSON tokens. */
@Internal
@FunctionalInterface
public interface TokenWriter {
    /**
     * Writes a token. Text is empty for structural and literal tokens.
     * @param token Token kind
     * @param text Token text
     * @throws IOException If writing fails
     */
    void write(PatchToken token, String text) throws IOException;
}
