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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.toon.SerdeToonConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a JSON tree as a TOON document.
 *
 * <p>Array form (inline, tabular, keyed-tabular, or list) is chosen by
 * inspecting all elements of an array, or all values of an object for
 * keyed-tabular form, before writing.</p>
 *
 * <p>An array or keyed object is tabular-eligible only when every element
 * declares its keys in the same order; the same keys in a different order
 * fall back to list form.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
@Singleton
public final class ToonEncoder {

    private final char delimiter;
    private final String indentUnit;

    /**
     * Creates a TOON encoder.
     *
     * @param toonConfiguration The TOON format configuration
     */
    public ToonEncoder(SerdeToonConfiguration toonConfiguration) {
        this.delimiter = toonConfiguration.getDelimiter().getCharacter();
        this.indentUnit = " ".repeat(toonConfiguration.getIndent());
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

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            boolean[] firstLine = {true};
            writeRoot(line -> {
                if (!firstLine[0]) {
                    writer.write('\n');
                } else {
                    firstLine[0] = false;
                }
                writer.write(line);
            }, tree);
            writer.flush();
        }
    }

    private void writeRoot(LineConsumer consumer, JsonNode tree) throws IOException {
        if (tree.isNull()) {
            consumer.accept("null");
        } else if (tree.isObject()) {
            if (tree.size() == 0) {
                // An empty document decodes back to {} per the root-form rules.
                return;
            }

            if (isKeyedTabularEligible(tree)) {
                writeKeyedTabular(consumer, null, tree, 0);
            } else {
                writeObjectFields(consumer, tree, 0);
            }
        } else if (tree.isArray()) {
            writeArrayNode(consumer, null, tree, 0);
        } else {
            consumer.accept(encodeScalar(tree));
        }
    }

    private void writeObjectFields(LineConsumer consumer, JsonNode object, int depth) throws IOException {
        for (Map.Entry<String, JsonNode> entry : object.entries()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isValueNode()) {
                consumer.accept(indent(depth) + quoteKey(key) + ": " + encodeScalar(value));
            } else if (value.isObject()) {
                if (value.size() == 0) {
                    consumer.accept(indent(depth) + quoteKey(key) + ":");
                } else if (isKeyedTabularEligible(value)) {
                    writeKeyedTabular(consumer, key, value, depth);
                } else {
                    consumer.accept(indent(depth) + quoteKey(key) + ":");
                    writeObjectFields(consumer, value, depth + 1);
                }
            } else {
                writeArrayNode(consumer, key, value, depth);
            }
        }
    }

    private void writeArrayNode(LineConsumer consumer, @Nullable String key, JsonNode array, int depth) throws IOException {
        String prefix = indent(depth) + keyPrefix(key);
        int size = array.size();
        if (size == 0) {
            consumer.accept(key == null ? prefix + "[]" : prefix + ": []");
            return;
        }

        List<JsonNode> elements = CollectionUtils.iterableToList(array.values());
        if (allPrimitive(elements)) {
            writeInlineArray(consumer, prefix, elements);
        } else if (isTabularEligible(elements)) {
            writeTabularArray(consumer, prefix, elements, depth);
        } else {
            writeListArray(consumer, prefix, elements, depth);
        }
    }

    private void writeInlineArray(LineConsumer consumer, String prefix, List<JsonNode> elements) throws IOException {
        StringBuilder sb = new StringBuilder(prefix).append(bracketSegment(elements.size(), false)).append(": ");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(encodeScalar(elements.get(i)));
        }
        consumer.accept(sb.toString());
    }

    private void writeTabularArray(LineConsumer consumer, String prefix, List<JsonNode> elements, int depth) throws IOException {
        JsonNode representative = elements.getFirst();
        List<String> fieldOrder = keysOf(representative);
        String header = bracketSegment(elements.size(), false) + buildFieldList(fieldOrder, representative);
        consumer.accept(prefix + header + ":");
        String rowIndent = indent(depth + 1);
        for (JsonNode element : elements) {
            consumer.accept(rowIndent + buildRow(fieldOrder, element));
        }
    }

    private void writeListArray(LineConsumer consumer, String prefix, List<JsonNode> elements, int depth) throws IOException {
        consumer.accept(prefix + bracketSegment(elements.size(), false) + ":");
        for (JsonNode element : elements) {
            writeListItem(consumer, element, depth + 1);
        }
    }

    private void writeListItem(LineConsumer consumer, JsonNode item, int depth) throws IOException {
        if (item.isValueNode()) {
            consumer.accept(indent(depth) + "- " + encodeScalar(item));
            return;
        }

        if (item.size() == 0) {
            // Bare "-" for an empty object. The spec requires "- [0<delim?>]:"
            // for an empty array - not "- []" - even though decoders accept
            // both; encoders must not emit the latter.
            consumer.accept(item.isArray() ? indent(depth) + "- " + bracketSegment(0, false) + ":" : indent(depth) + "-");
            return;
        }

        // The item's first physical line is hyphenated in place of its normal
        // indentation; every subsequent line (siblings, or that first field's
        // own nested continuation) is already at the correct depth because it
        // was rendered as if depth + 1 were the item's own depth.
        String childIndent = indent(depth + 1);
        String listPrefix = indent(depth) + "- ";
        LineConsumer itemConsumer = new LineConsumer() {
            private boolean first = true;

            @Override
            public void accept(String line) throws IOException {
                if (first) {
                    first = false;
                    consumer.accept(listPrefix + line.substring(childIndent.length()));
                } else {
                    consumer.accept(line);
                }
            }
        };

        if (item.isObject()) {
            writeObjectFields(itemConsumer, item, depth + 1);
        } else {
            writeArrayNode(itemConsumer, null, item, depth + 1);
        }
    }

    private void writeKeyedTabular(LineConsumer consumer, @Nullable String key, JsonNode object, int depth) throws IOException {
        List<Map.Entry<String, JsonNode>> entries = CollectionUtils.iterableToList(object.entries());
        JsonNode representative = entries.getFirst().getValue();
        List<String> fieldOrder = keysOf(representative);
        String header = bracketSegment(entries.size(), true) + buildFieldList(fieldOrder, representative);
        consumer.accept(indent(depth) + keyPrefix(key) + header + ":");
        String entryIndent = indent(depth + 1);
        for (Map.Entry<String, JsonNode> entry : entries) {
            consumer.accept(entryIndent + quoteKey(entry.getKey()) + ": " + buildRow(fieldOrder, entry.getValue()));
        }
    }

    /**
     * An array is tabular-eligible when it has at least two elements, every
     * element is a non-empty object with the same keys in the same order,
     * and every column is a uniform-primitive column or itself a uniform,
     * tabular-eligible column of nested objects. A single element is never
     * eligible, so an ordinary single-field nested object or single-entry
     * map does not encode as a one-row tabular block.
     */
    private boolean isTabularEligible(List<JsonNode> elements) {
        if (elements.size() < 2) {
            return false;
        }

        List<String> fieldOrder = List.of();
        for (JsonNode element : elements) {
            if (!element.isObject() || element.size() == 0) {
                return false;
            }

            List<String> keys = keysOf(element);
            if (fieldOrder.isEmpty()) {
                fieldOrder = keys;
            } else if (!fieldOrder.equals(keys)) {
                return false;
            }
        }

        for (String field : fieldOrder) {
            List<JsonNode> columnValues = new ArrayList<>(elements.size());
            for (JsonNode e : elements) {
                columnValues.add(requireField(e, field));
            }
            if (!isUniformColumn(columnValues)) {
                return false;
            }
        }

        return true;
    }

    private boolean isUniformColumn(List<JsonNode> columnValues) {
        boolean allValueNodes = true;
        for (JsonNode node : columnValues) {
            if (!node.isValueNode()) {
                allValueNodes = false;
                break;
            }
        }
        if (allValueNodes) {
            return true;
        }
        return isTabularEligible(columnValues);
    }

    private boolean isKeyedTabularEligible(JsonNode object) {
        return isTabularEligible(CollectionUtils.iterableToList(object.values()));
    }

    private String buildFieldList(List<String> fieldOrder, JsonNode representativeElement) {
        StringBuilder sb = new StringBuilder();
        appendJsonNodeField(fieldOrder, representativeElement, sb);
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String field, JsonNode representativeValue) {
        sb.append(quoteKey(field));
        if (representativeValue.isObject()) {
            List<String> nestedFields = keysOf(representativeValue);
            appendJsonNodeField(nestedFields, representativeValue, sb);
        }
    }

    private void appendJsonNodeField(List<String> fieldOrder, JsonNode representativeElement, StringBuilder sb) {
        sb.append('{');
        for (int i = 0; i < fieldOrder.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }

            String field = fieldOrder.get(i);
            appendField(sb, field, requireField(representativeElement, field));
        }
        sb.append('}');
    }

    private String buildRow(List<String> fieldOrder, JsonNode element) {
        StringBuilder sb = new StringBuilder();
        boolean[] first = {true};
        for (String field : fieldOrder) {
            appendLeafCells(sb, requireField(element, field), first);
        }
        return sb.toString();
    }

    /**
     * Looks up a field that is guaranteed present because {@code field} was
     * itself derived from this same node's own key set.
     */
    private static JsonNode requireField(JsonNode node, String field) {
        return Objects.requireNonNull(node.get(field), () -> "field not present: " + field);
    }

    private void appendLeafCells(StringBuilder sb, JsonNode value, boolean[] first) {
        if (value.isObject()) {
            for (Map.Entry<String, JsonNode> entry : value.entries()) {
                appendLeafCells(sb, entry.getValue(), first);
            }
        } else {
            if (!first[0]) {
                sb.append(delimiter);
            } else {
                first[0] = false;
            }
            sb.append(encodeScalar(value));
        }
    }

    private String bracketSegment(int length, boolean keyed) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(length);
        if (keyed) {
            sb.append(':');
        }

        if (delimiter == '\t' || delimiter == '|') {
            sb.append(delimiter);
        }

        sb.append(']');
        return sb.toString();
    }

    private String encodeScalar(JsonNode value) {
        if (value.isNull()) {
            return "null";
        }

        if (value.isBoolean()) {
            return Boolean.toString(value.getBooleanValue());
        }

        if (value.isNumber()) {
            return ToonNumbers.format(value.getNumberValue());
        }

        return ToonEscapes.quoteValueIfNeeded(value.getStringValue(), delimiter);
    }

    private String quoteKey(String key) {
        return ToonEscapes.quoteKeyIfNeeded(key);
    }

    private String keyPrefix(@Nullable String key) {
        return key == null ? "" : quoteKey(key);
    }

    private String indent(int depth) {
        return indentUnit.repeat(depth);
    }

    private static boolean allPrimitive(List<JsonNode> elements) {
        for (JsonNode element : elements) {
            if (!element.isValueNode()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> keysOf(JsonNode object) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : object.entries()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    @FunctionalInterface
    private interface LineConsumer {
        void accept(String line) throws IOException;
    }
}
