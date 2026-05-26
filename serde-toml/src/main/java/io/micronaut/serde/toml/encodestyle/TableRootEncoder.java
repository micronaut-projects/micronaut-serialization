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
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.toml.entities.ArrayValue;
import io.micronaut.serde.toml.entities.ObjectValue;
import io.micronaut.serde.toml.entities.TomlValue;

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
    protected void appendCompletedDocument(StringBuilder builder, TomlValue value) throws IOException {
        appendTableDocument(builder, value);
    }

    /**
     * Append a complete table-style TOML document.
     *
     * @param builder The target builder
     * @param value The root TOML value
     * @throws IOException If the root value cannot be rendered
     */
    public static void appendTableDocument(StringBuilder builder, TomlValue value) throws IOException {
        if (!(value instanceof ObjectValue objectValue)) {
            throw new SerdeException("TOML root value must be an object");
        }
        appendTable(builder, List.of(), objectValue);
    }

    // used in appendTableDocument
    private static void appendTable(StringBuilder builder, List<String> path, ObjectValue objectValue) {
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
        for (Map.Entry<String, TomlValue> entry : objectValue.values().entrySet()) {
            TomlValue value = entry.getValue();
            if (!isTableValue(value)) {
                builder.append(renderKeySegment(entry.getKey()))
                    .append(" = ")
                    .append(renderInlineValue(value))
                    .append('\n');
            }
        }
        /*
        * Trait ObjectValue and Array of Objects as sections i.e.
        *
          [author]
          name = 'Ada'

          [[products]]
          name = 'Hammer'
        * */
        for (Map.Entry<String, TomlValue> entry : objectValue.values().entrySet()) {
            List<String> keyPath = appendPath(path, entry.getKey());
            TomlValue value = entry.getValue();
            if (value instanceof ObjectValue nested) {
                appendTable(builder, keyPath, nested);
            } else if (value instanceof ArrayValue arrayValue && isArrayOfObjects(arrayValue)) {
                appendArrayOfTables(builder, keyPath, arrayValue);
            }
        }
    }

    // used in appendTable and recursively
    private static void appendArrayOfTables(StringBuilder builder, List<String> path, ArrayValue arrayValue) {
        for (TomlValue value : arrayValue.values()) {
            ObjectValue objectValue = (ObjectValue) value;
            appendSectionBreak(builder);
            builder.append("[[")
                .append(renderKeyPath(path))
                .append("]]\n");
            for (Map.Entry<String, TomlValue> entry : objectValue.values().entrySet()) {
                TomlValue entryValue = entry.getValue();
                if (!isTableValue(entryValue)) {
                    builder.append(renderKeySegment(entry.getKey()))
                        .append(" = ")
                        .append(renderInlineValue(entryValue))
                        .append('\n');
                }
            }
            for (Map.Entry<String, TomlValue> entry : objectValue.values().entrySet()) {
                List<String> childPath = appendPath(path, entry.getKey());
                TomlValue entryValue = entry.getValue();
                if (entryValue instanceof ObjectValue nested) {
                    appendTable(builder, childPath, nested);
                } else if (entryValue instanceof ArrayValue nestedArray && isArrayOfObjects(nestedArray)) {
                    appendArrayOfTables(builder, childPath, nestedArray);
                }
            }
        }
    }

    private static boolean isTableValue(TomlValue value) {
        return value instanceof ObjectValue
            || (value instanceof ArrayValue arrayValue && isArrayOfObjects(arrayValue));
    }

    private static boolean isArrayOfObjects(ArrayValue arrayValue) {
        return !arrayValue.values().isEmpty() && arrayValue.values().stream().allMatch(ObjectValue.class::isInstance);
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
