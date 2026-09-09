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
import io.micronaut.serde.toon.SerdeToonConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Writes a JSON tree as a TOON document.
 *
 * <p><strong>Scaffolding note:</strong> this initial implementation only
 * handles flat objects with primitive fields (no nested objects, arrays,
 * tabular forms, or quoting rules yet). It exists so that {@code ToonMapper}
 * can be wired and smoke-tested end-to-end before the full write-side
 * algorithm from the TOON specification (tabular/keyed-tabular/list form
 * detection, quoting, escaping, number formatting) is implemented.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
@Singleton
public final class ToonWriter {

    private final char delimiter;
    private final int indent;

    /**
     * Creates a TOON writer.
     *
     * @param toonConfiguration The TOON format configuration
     */
    public ToonWriter(SerdeToonConfiguration toonConfiguration) {
        this.delimiter = toonConfiguration.getDelimiter().getCharacter();
        this.indent = toonConfiguration.getIndent();
    }

    /**
     * Writes the given JSON tree to the output stream as a TOON document.
     *
     * @param outputStream The destination stream
     * @param tree         The tree to encode as TOON
     * @throws IOException If the TOON output cannot be written
     */
    public void write(OutputStream outputStream, JsonNode tree) throws IOException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writeObject(writer, tree);
        writer.flush();
    }

    private void writeObject(Writer writer, JsonNode tree) throws IOException {
        if (!tree.isObject()) {
            throw new IOException("Only flat objects are supported by this scaffolding implementation");
        }

        boolean first = true;
        for (var entry : tree.entries()) {
            JsonNode value = entry.getValue();
            if (!value.isValueNode()) {
                throw new IOException("Nested objects/arrays are not yet supported by this scaffolding implementation");
            }

            if (!first) {
                writer.write(System.lineSeparator());
            }

            first = false;
            writer.write(entry.getKey());
            writer.write(": ");
            writer.write(value.isNull() ? "null" : value.coerceStringValue());
        }
    }

    // TODO: Implement with full write algorithm
    char delimiter() {
        return delimiter;
    }

    int indent() {
        return indent;
    }
}
