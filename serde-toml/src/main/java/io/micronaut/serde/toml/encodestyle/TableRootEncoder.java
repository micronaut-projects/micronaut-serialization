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
package io.micronaut.serde.toml.encodestyle;

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.micronaut.serde.toml.encodestyle.InlineRootEncoder.renderInlineValue;
import static io.micronaut.serde.toml.encodestyle.TomlStyleRenderer.renderKeySegment;

/**
 * Root encoder for TOML table output style.
 */
@Internal
public final class TableRootEncoder extends TomlStyleEncoder {

    /**
     * @param outputStream The target output stream
     * @param remainingLimits The remaining encoder limits
     */
    public TableRootEncoder(OutputStream outputStream,
                            LimitingStream.RemainingLimits remainingLimits) {
        super(outputStream, remainingLimits);
    }

    @Override
    protected void appendCompletedDocument(StringBuilder builder, JsonNode value) throws IOException {
        appendTableDocument(builder, value);
    }

    /**
     * Append a complete table-style TOML document.
     *
     * @param builder The target builder
     * @param value The root TOML value
     * @throws IOException If the root value cannot be rendered
     */
    public static void appendTableDocument(StringBuilder builder, JsonNode value) throws IOException {
        if (!value.isObject()) {
            throw new SerdeException("TOML root value must be an object");
        }
        appendTable(builder, List.of(), value);
    }

    // used in appendTableDocument
    private static void appendTable(StringBuilder builder, List<String> path, JsonNode objectValue) {
        if (!path.isEmpty()) {
            appendSectionBreak(builder);
            builder.append('[')
                .append(renderKeyPath(path))
                .append("]\n");
        }
        /*
         * Creating scalar value inline
         *    name = 'Hammer'
              sku = 738594937
              tags = [1, 2, 3]
         */
        for (Map.Entry<String, JsonNode> entry : objectValue.entries()) {
            JsonNode value = entry.getValue();
            if (!isTableValue(value)) {
                builder.append(renderKeySegment(entry.getKey()))
                    .append(" = ")
                    .append(renderInlineValue(value))
                    .append('\n');
            }
        }
        /*
        * Treat object nodes and arrays of object nodes as sections i.e.
        *
          [author]
          name = 'Ada'

          [[products]]
          name = 'Hammer'
        * */
        for (Map.Entry<String, JsonNode> entry : objectValue.entries()) {
            List<String> keyPath = appendPath(path, entry.getKey());
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                appendTable(builder, keyPath, value);
            } else if (value.isArray() && isArrayOfObjects(value)) {
                appendArrayOfTables(builder, keyPath, value);
            }
        }
    }

    // used in appendTable and recursively
    private static void appendArrayOfTables(StringBuilder builder, List<String> path, JsonNode arrayValue) {
        for (JsonNode objectValue : arrayValue.values()) {
            appendSectionBreak(builder);
            builder.append("[[")
                .append(renderKeyPath(path))
                .append("]]\n");
            for (Map.Entry<String, JsonNode> entry : objectValue.entries()) {
                JsonNode entryValue = entry.getValue();
                if (!isTableValue(entryValue)) {
                    builder.append(renderKeySegment(entry.getKey()))
                        .append(" = ")
                        .append(renderInlineValue(entryValue))
                        .append('\n');
                }
            }
            for (Map.Entry<String, JsonNode> entry : objectValue.entries()) {
                List<String> childPath = appendPath(path, entry.getKey());
                JsonNode entryValue = entry.getValue();
                if (entryValue.isObject()) {
                    appendTable(builder, childPath, entryValue);
                } else if (entryValue.isArray() && isArrayOfObjects(entryValue)) {
                    appendArrayOfTables(builder, childPath, entryValue);
                }
            }
        }
    }

    private static boolean isTableValue(JsonNode value) {
        return value.isObject()
            || (value.isArray() && isArrayOfObjects(value));
    }

    private static boolean isArrayOfObjects(JsonNode arrayValue) {
        if (arrayValue.size() == 0) {
            return false;
        }
        for (JsonNode value : arrayValue.values()) {
            if (!value.isObject()) {
                return false;
            }
        }
        return true;
    }

    private static void appendSectionBreak(StringBuilder builder) {
        int length = builder.length();
        if (length == 0) {
            return;
        }
        if (builder.charAt(length - 1) != '\n') {
            builder.append('\n');
            length++;
        }
        if (length < 2 || builder.charAt(length - 2) != '\n') {
            builder.append('\n');
        }
    }

    private static List<String> appendPath(List<String> path, String key) {
        List<String> newPath = new ArrayList<>(path.size() + 1);
        newPath.addAll(path);
        newPath.add(key);
        return newPath;
    }

    private static String renderKeyPath(List<String> path) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(renderKeySegment(path.get(i)));
        }
        return builder.toString();
    }
}
