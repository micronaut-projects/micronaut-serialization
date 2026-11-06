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
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.toon.SerdeToonConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Builds a JSON tree from a TOON document.
 *
 * <p>Each call reads the stream and parses the text with a fresh {@link
 * ToonDocumentParser}, which does the line pre-processing and
 * recursive-descent parsing.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.3.0
 */
@Internal
@Singleton
public final class ToonDecoder {

    private final long maxInputSize;

    /**
     * Creates a TOON decoder.
     *
     * @param toonConfiguration The TOON format configuration
     */
    public ToonDecoder(SerdeToonConfiguration toonConfiguration) {
        this.maxInputSize = toonConfiguration.getMaxInputSize();
    }

    /**
     * Parses a TOON document from the given stream into a JSON tree, using
     * {@link LimitingStream#DEFAULT_LIMITS} as the nesting-depth budget.
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
        String text = decodeUtf8Strict(readInput(stream));
        return new ToonDocumentParser(text, remainingLimits).parseDocument();
    }

    /**
     * Reads the whole stream, failing once it exceeds the configured maximum
     * input size. At most one byte beyond the limit is read.
     */
    private byte[] readInput(InputStream stream) throws IOException {
        if (maxInputSize == SerdeToonConfiguration.UNLIMITED) {
            return stream.readAllBytes();
        }

        // Reading a long limit in a single call is capped by the array size.
        int readLimit = (int) Math.min(maxInputSize + 1, Integer.MAX_VALUE - 8L);
        byte[] bytes = stream.readNBytes(readLimit);
        if (bytes.length > maxInputSize) {
            throw new SerdeException("TOON input exceeds the maximum input size of " + maxInputSize + " bytes");
        }

        return bytes;
    }

    /**
     * Decodes the given bytes as UTF-8, rejecting ill-formed input instead
     * of silently splicing in {@code U+FFFD} replacement characters (the
     * behavior {@code new String(bytes, UTF_8)} would otherwise use).
     */
    private static String decodeUtf8Strict(byte[] bytes) throws SerdeException {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            throw new SerdeException("Invalid UTF-8 in TOON input", e);
        }
    }
}
