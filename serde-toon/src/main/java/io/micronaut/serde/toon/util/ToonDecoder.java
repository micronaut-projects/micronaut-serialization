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
package io.micronaut.serde.toon.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.LimitingStream;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds a JSON tree from a TOON document.
 *
 * <p>Each call reads the stream and parses the text with a fresh {@link
 * ToonDocumentParser}, which does the line pre-processing and
 * recursive-descent parsing.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
@Singleton
public final class ToonDecoder {

    /**
     * Creates a TOON decoder.
     */
    public ToonDecoder() {
    }

    /**
     * Parses a TOON document from the given stream into a JSON tree, with no
     * nesting-depth limit beyond the JVM's own stack.
     *
     * @param stream The TOON input stream
     * @return The parsed JSON tree
     * @throws IOException If the TOON input cannot be read or is malformed
     */
    public JsonNode parse(InputStream stream) throws IOException {
        return parse(stream, LimitingStream.DEFAULT_LIMITS);
    }

    /**
     * Parses a TOON document from the given stream into a JSON tree,
     * rejecting input nested deeper than {@code remainingLimits} allows.
     *
     * @param stream          The TOON input stream
     * @param remainingLimits The nesting-depth budget, e.g. from {@link
     *                        LimitingStream#limitsFromConfiguration}
     * @return The parsed JSON tree
     * @throws IOException If the TOON input cannot be read, is malformed, or nests too deeply
     */
    public JsonNode parse(InputStream stream, LimitingStream.RemainingLimits remainingLimits) throws IOException {
        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return new ToonDocumentParser(text, remainingLimits).parseDocument();
    }
}
