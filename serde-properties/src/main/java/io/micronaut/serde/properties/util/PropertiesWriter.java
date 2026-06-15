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
package io.micronaut.serde.properties.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.properties.SerdePropertiesConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a JSON tree as a Java {@code .properties} document.
 *
 * <p>Array index and bracketed output is controlled by {@link SerdePropertiesConfiguration}.</p>
 *
 * @since 3.1.0
 */
@Internal
@Singleton
public final class PropertiesWriter {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final SerdePropertiesConfiguration.ArrayIndexStyle arrayIndexStyle;

    /**
     * Creates a properties writer.
     *
     * @param propertiesConfiguration The properties format configuration
     */
    public PropertiesWriter(SerdePropertiesConfiguration propertiesConfiguration) {
        this.arrayIndexStyle = propertiesConfiguration.getArrayIndexStyle();
    }

    /**
     * Writes the given JSON tree to the output stream as a flattened
     * {@code .properties} document.
     *
     * @param outputStream The destination stream
     * @param tree The tree to flatten into properties output
     * @throws IOException If the properties output cannot be written
     */
    public void write(OutputStream outputStream, JsonNode tree) throws IOException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        new TreeWriter(writer, arrayIndexStyle).writeProperties(tree);
        writer.flush();
    }

    private static final class TreeWriter {
        private final Writer writer;
        private final SerdePropertiesConfiguration.ArrayIndexStyle arrayIndexStyle;
        private final StringBuilder path = new StringBuilder();

        private TreeWriter(Writer writer, SerdePropertiesConfiguration.ArrayIndexStyle arrayIndexStyle) {
            this.writer = writer;
            this.arrayIndexStyle = arrayIndexStyle;
        }

        private void writeProperties(JsonNode node) throws IOException {
            if (node.isObject()) {
                for (Map.Entry<String, JsonNode> entry : node.entries()) {
                    int length = path.length();
                    if (length > 0) {
                        path.append('.');
                    }
                    path.append(entry.getKey());
                    writeProperties(entry.getValue());
                    path.setLength(length);
                }
            } else if (node.isArray()) {
                if (path.length() == 0) {
                    throw new IOException("Cannot write a root array as .properties");
                }
                int index = 0;
                for (JsonNode value : node.values()) {
                    int length = path.length();
                    appendArrayIndex(index);
                    writeProperties(value);
                    path.setLength(length);
                    index++;
                }
            } else if (path.length() == 0) {
                if (!node.isNull()) {
                    throw new IOException("Cannot write a root scalar as .properties");
                }
            } else {
                writePropertyLine(path.toString(), node.coerceStringValue());
            }
        }

        private void writePropertyLine(String key, String value) throws IOException {
            StringBuilder keyBuilder = new StringBuilder(key.length());
            PropertiesEscapes.appendKey(keyBuilder, key);
            writer.write(keyBuilder.toString());
            writer.write('=');
            if (!value.isEmpty() && value.charAt(0) == ' ') {
                writer.write('\\');
            }
            StringBuilder valueBuilder = PropertiesEscapes.appendValue(value);
            writer.write(valueBuilder == null ? value : valueBuilder.toString());
            writer.write(LINE_SEPARATOR);
        }

        private void appendArrayIndex(int index) {
            if (arrayIndexStyle == SerdePropertiesConfiguration.ArrayIndexStyle.DOTTED) {
                path.append('.').append(index + 1);
            } else {
                path.append('[').append(index).append(']');
            }
        }
    }
}
