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
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.toon.SerdeToonConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a JSON tree from a TOON document.
 *
 * <p><strong>Scaffolding note:</strong> this initial implementation only
 * handles flat objects with unquoted primitive fields (no nested objects,
 * arrays, tabular forms, quoting, or comments yet). It exists so that
 * {@code ToonMapper} can be wired and smoke-tested end-to-end before the
 * full parse-side algorithm from the TOON specification is implemented.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
@Singleton
public final class ToonTreeAdapter {

    /**
     * Creates a TOON tree adapter.
     *
     * @param toonConfiguration The TOON format configuration, reserved for
     *                          use once indentation-aware parsing is implemented
     */
    public ToonTreeAdapter(SerdeToonConfiguration toonConfiguration) {
        // Configuration is not yet consulted by this scaffolding implementation.
    }

    /**
     * Parses a TOON document from the given stream into a JSON tree.
     *
     * @param stream The TOON input stream
     * @return The parsed JSON tree
     * @throws IOException If the TOON input cannot be read
     */
    public JsonNode parse(InputStream stream) throws IOException {
        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            int colon = line.indexOf(": ");
            if (colon < 0) {
                throw new SerdeException("Malformed TOON line (expected 'key: value'): " + line);
            }

            String key = line.substring(0, colon);
            String value = line.substring(colon + 2);
            values.put(key, "null".equals(value) ? JsonNode.nullNode() : JsonNode.createStringNode(value));
        }

        return JsonNode.createObjectNode(values);
    }
}
