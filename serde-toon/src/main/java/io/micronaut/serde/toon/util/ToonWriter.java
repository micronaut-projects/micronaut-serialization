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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes a JSON tree as a TOON document.
 *
 * <p>Array encoding form (inline, tabular, keyed-tabular, or list) is chosen
 * per the specification by inspecting every element of an array (or every
 * value of an object, for keyed-tabular form) before any output is written -
 * this is a whole-subtree decision, not a per-element streaming one, which is
 * why this class walks a buffered {@link JsonNode} tree rather than
 * implementing the push-style {@code Encoder} interface used by the
 * Jackson/BSON format bindings.</p>
 *
 * <p><strong>Known simplification:</strong> a uniform array/keyed-object is
 * only recognized as tabular-eligible when every element declares its keys in
 * the exact same order; elements with the same key set but different
 * insertion order fall back to list form instead of being detected as
 * tabular. This is conservative (never misencodes), just not maximally
 * compact for that shape.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
@Singleton
public final class ToonWriter {

    private final char delimiter;
    private final String indentUnit;

    /**
     * Creates a TOON writer.
     *
     * @param toonConfiguration The TOON format configuration
     */
    public ToonWriter(SerdeToonConfiguration toonConfiguration) {
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

        List<String> lines = new ArrayList<>();
        writeRoot(lines, tree);

        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer.write(String.join("\n", lines));
        writer.flush();
    }

    private void writeRoot(List<String> lines, JsonNode tree) {
        if (tree.isNull()) {
            lines.add("null");
        } else if (tree.isObject()) {
            if (tree.size() == 0) {
                // An empty document decodes back to {} per the root-form rules.
                return;
            }

            if (isKeyedTabularEligible(tree)) {
                writeKeyedTabular(lines, null, tree, 0);
            } else {
                writeObjectFields(lines, tree, 0);
            }
        } else if (tree.isArray()) {
            writeArrayNode(lines, null, tree, 0);
        } else {
            lines.add(encodeScalar(tree));
        }
    }

    private void writeObjectFields(List<String> lines, JsonNode object, int depth) {
        for (Map.Entry<String, JsonNode> entry : object.entries()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isValueNode()) {
                lines.add(indent(depth) + quoteKey(key) + ": " + encodeScalar(value));
            } else if (value.isObject()) {
                if (value.size() == 0) {
                    lines.add(indent(depth) + quoteKey(key) + ":");
                } else if (isKeyedTabularEligible(value)) {
                    writeKeyedTabular(lines, key, value, depth);
                } else {
                    lines.add(indent(depth) + quoteKey(key) + ":");
                    writeObjectFields(lines, value, depth + 1);
                }
            } else {
                writeArrayNode(lines, key, value, depth);
            }
        }
    }

    private void writeArrayNode(List<String> lines, @Nullable String key, JsonNode array, int depth) {
        String prefix = indent(depth) + keyPrefix(key);
        int size = array.size();
        if (size == 0) {
            lines.add(key == null ? prefix + "[]" : prefix + ": []");
            return;
        }

        List<JsonNode> elements = CollectionUtils.iterableToList(array.values());
        if (allPrimitive(elements)) {
            writeInlineArray(lines, prefix, elements);
        } else if (isTabularEligible(elements)) {
            writeTabularArray(lines, prefix, elements, depth);
        } else {
            writeListArray(lines, prefix, elements, depth);
        }
    }

    private void writeInlineArray(List<String> lines, String prefix, List<JsonNode> elements) {
        String cells = elements.stream().map(this::encodeScalar).collect(Collectors.joining(String.valueOf(delimiter)));
        lines.add(prefix + bracketSegment(elements.size(), false) + ": " + cells);
    }

    private void writeTabularArray(List<String> lines, String prefix, List<JsonNode> elements, int depth) {
        JsonNode representative = elements.get(0);
        List<String> fieldOrder = keysOf(representative);
        String header = bracketSegment(elements.size(), false) + buildFieldList(fieldOrder, representative);
        lines.add(prefix + header + ":");
        for (JsonNode element : elements) {
            lines.add(indent(depth + 1) + buildRow(fieldOrder, element));
        }
    }

    private void writeListArray(List<String> lines, String prefix, List<JsonNode> elements, int depth) {
        lines.add(prefix + bracketSegment(elements.size(), false) + ":");
        for (JsonNode element : elements) {
            writeListItem(lines, element, depth + 1);
        }
    }

    private void writeListItem(List<String> lines, JsonNode item, int depth) {
        if (item.isValueNode()) {
            lines.add(indent(depth) + "- " + encodeScalar(item));
            return;
        }

        if (item.size() == 0) {
            // Bare "-" for an empty object. The spec requires "- [0<delim?>]:"
            // for an empty array - not "- []" - even though decoders accept
            // both; encoders must not emit the latter.
            lines.add(item.isArray() ? indent(depth) + "- " + bracketSegment(0, false) + ":" : indent(depth) + "-");
            return;
        }

        List<String> nested = new ArrayList<>();
        if (item.isObject()) {
            writeObjectFields(nested, item, depth + 1);
        } else {
            writeArrayNode(nested, null, item, depth + 1);
        }

        // The item's first physical line is hyphenated in place of its normal
        // indentation; every subsequent line (siblings, or that first field's
        // own nested continuation) is already at the correct depth because it
        // was rendered as if depth + 1 were the item's own depth.
        String childIndent = indent(depth + 1);
        String first = nested.getFirst();
        lines.add(indent(depth) + "- " + first.substring(childIndent.length()));
        lines.addAll(nested.subList(1, nested.size()));
    }

    private void writeKeyedTabular(List<String> lines, @Nullable String key, JsonNode object, int depth) {
        List<Map.Entry<String, JsonNode>> entries = CollectionUtils.iterableToList(object.entries());
        JsonNode representative = entries.get(0).getValue();
        List<String> fieldOrder = keysOf(representative);
        String header = bracketSegment(entries.size(), true) + buildFieldList(fieldOrder, representative);
        lines.add(indent(depth) + keyPrefix(key) + header + ":");
        for (Map.Entry<String, JsonNode> entry : entries) {
            lines.add(indent(depth + 1) + quoteKey(entry.getKey()) + ": " + buildRow(fieldOrder, entry.getValue()));
        }
    }

    /**
     * An array is tabular-eligible when it has at least two elements, every
     * element is a non-empty object, every element declares the same keys in
     * the same order, and every resulting column is either a
     * uniform-primitive column or itself a uniform, tabular-eligible column
     * of nested objects.
     *
     * <p>A single element is never treated as eligible: with nothing to
     * compare it against, "uniform" is vacuously true, which would make an
     * ordinary single-field nested object (or a single-entry map value)
     * misencode as a one-row tabular/keyed-tabular block instead of a plain
     * nested object.</p>
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
            List<JsonNode> columnValues = elements.stream().map(e -> requireField(e, field)).toList();
            if (!isUniformColumn(columnValues)) {
                return false;
            }
        }

        return true;
    }

    private boolean isUniformColumn(List<JsonNode> columnValues) {
        if (columnValues.stream().allMatch(JsonNode::isValueNode)) {
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
        List<String> cells = new ArrayList<>();
        for (String field : fieldOrder) {
            appendLeafCells(cells, requireField(element, field));
        }

        return String.join(String.valueOf(delimiter), cells);
    }

    /**
     * Looks up a field that is guaranteed present because {@code field} was
     * itself derived from this same node's own key set.
     */
    private static JsonNode requireField(JsonNode node, String field) {
        return Objects.requireNonNull(node.get(field), () -> "field not present: " + field);
    }

    private void appendLeafCells(List<String> cellsOut, JsonNode value) {
        if (value.isObject()) {
            for (Map.Entry<String, JsonNode> entry : value.entries()) {
                appendLeafCells(cellsOut, entry.getValue());
            }
        } else {
            cellsOut.add(encodeScalar(value));
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
        return elements.stream().allMatch(JsonNode::isValueNode);
    }

    private static List<String> keysOf(JsonNode object) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : object.entries()) {
            keys.add(entry.getKey());
        }
        return keys;
    }
}
