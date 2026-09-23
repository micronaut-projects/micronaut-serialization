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
import io.micronaut.core.util.functional.ThrowingConsumer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Writes a JSON tree as a TOON document.
 *
 * <p>Array form (inline, tabular, keyed-tabular, or list) is chosen by
 * inspecting all elements of an array, or all values of an object for
 * keyed-tabular form, before writing.</p>
 *
 * <p>An array or keyed object is tabular-eligible only when every element
 * declares the same set of keys (order may vary per element); a differing
 * key set falls back to list form. The header - and the order cells are
 * emitted in for every row - is taken from the first element.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.1
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

    private void writeRoot(ThrowingConsumer<String, IOException> consumer, JsonNode tree) throws IOException {
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

    private void writeObjectFields(ThrowingConsumer<String, IOException> consumer, JsonNode object, int depth) throws IOException {
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

    private void writeArrayNode(ThrowingConsumer<String, IOException> consumer, @Nullable String key, JsonNode array, int depth) throws IOException {
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

    private void writeInlineArray(ThrowingConsumer<String, IOException> consumer, String prefix, List<JsonNode> elements) throws IOException {
        StringBuilder sb = new StringBuilder(prefix).append(bracketSegment(elements.size(), false)).append(": ");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(encodeScalar(elements.get(i)));
        }
        consumer.accept(sb.toString());
    }

    private void writeTabularArray(ThrowingConsumer<String, IOException> consumer, String prefix, List<JsonNode> elements, int depth) throws IOException {
        JsonNode representative = elements.getFirst();
        List<String> fieldOrder = keysOf(representative);
        String header = bracketSegment(elements.size(), false) + buildFieldList(fieldOrder, representative);
        consumer.accept(prefix + header + ":");
        String rowIndent = indent(depth + 1);
        for (JsonNode element : elements) {
            consumer.accept(rowIndent + buildRow(fieldOrder, representative, element));
        }
    }

    private void writeListArray(ThrowingConsumer<String, IOException> consumer, String prefix, List<JsonNode> elements, int depth) throws IOException {
        consumer.accept(prefix + bracketSegment(elements.size(), false) + ":");
        for (JsonNode element : elements) {
            writeListItem(consumer, element, depth + 1);
        }
    }

    private void writeListItem(ThrowingConsumer<String, IOException> consumer, JsonNode item, int depth) throws IOException {
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
        ThrowingConsumer<String, IOException> itemConsumer = new ThrowingConsumer<String, IOException>() {
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
            writeArrayAsListItem(itemConsumer, item, depth);
        }
    }

    /**
     * Writes a bare array occupying a list item's position. The header line
     * is rendered one level deeper than {@code depth} so the wrapping
     * consumer in {@link #writeListItem} can strip it back down to
     * {@code depth} and replace it with the item's {@code "- "} marker, but
     * the array's body must land one level below that marker - i.e. at
     * {@code depth + 1}, not {@code depth + 2} - so it is written using
     * {@code depth} itself rather than {@code depth + 1}.
     */
    private void writeArrayAsListItem(ThrowingConsumer<String, IOException> consumer, JsonNode array, int depth) throws IOException {
        String prefix = indent(depth + 1);
        List<JsonNode> elements = CollectionUtils.iterableToList(array.values());
        // Tabular form (§9.3) is only valid at the document root or in
        // object-field position, never as a list item, so an otherwise
        // tabular-eligible array falls back to list form here.
        if (allPrimitive(elements)) {
            writeInlineArray(consumer, prefix, elements);
        } else {
            writeListArray(consumer, prefix, elements, depth);
        }
    }

    private void writeKeyedTabular(ThrowingConsumer<String, IOException> consumer, @Nullable String key, JsonNode object, int depth) throws IOException {
        List<Map.Entry<String, JsonNode>> entries = CollectionUtils.iterableToList(object.entries());
        JsonNode representative = entries.getFirst().getValue();
        List<String> fieldOrder = keysOf(representative);
        String header = bracketSegment(entries.size(), true) + buildFieldList(fieldOrder, representative);
        consumer.accept(indent(depth) + keyPrefix(key) + header + ":");
        String entryIndent = indent(depth + 1);
        for (Map.Entry<String, JsonNode> entry : entries) {
            consumer.accept(entryIndent + quoteKey(entry.getKey()) + ": " + buildRow(fieldOrder, representative, entry.getValue()));
        }
    }

    /**
     * An array is tabular-eligible when it is non-empty, every element is a
     * non-empty object with the same set of keys (order may vary per
     * element), and every column is a uniform-primitive column or itself a
     * uniform, tabular-eligible column of nested objects. The header - and
     * the order cells are emitted in for every row - is taken from the
     * first element; {@link #requireField} looks fields up by name, so a
     * later element's own key order doesn't matter. Per §9.3 there is no
     * minimum element count - a single-element array is still eligible - so
     * this method is not used for keyed (map-form) tabular eligibility,
     * which per §9.5 requires at least two entries; see
     * {@link #isKeyedTabularEligible}.
     */
    private boolean isTabularEligible(List<JsonNode> elements) {
        if (elements.isEmpty()) {
            return false;
        }

        List<String> fieldOrder = List.of();
        Set<String> fieldSet = Set.of();
        for (JsonNode element : elements) {
            if (!element.isObject() || element.size() == 0) {
                return false;
            }

            List<String> keys = keysOf(element);
            if (fieldOrder.isEmpty()) {
                fieldOrder = keys;
                fieldSet = new HashSet<>(keys);
            } else if (!fieldSet.equals(new HashSet<>(keys))) {
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
        List<JsonNode> values = CollectionUtils.iterableToList(object.values());
        // §9.5: keyed (map-form) tabular blocks require at least two
        // entries, unlike plain tabular arrays (§9.3), which have no
        // minimum - a single entry would be ambiguous with an ordinary
        // single-field object.
        return values.size() >= 2 && isTabularEligible(values);
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

    /**
     * Builds a tabular row's cells in header order. {@code representative}
     * is the same element {@link #buildFieldList} derived the header from;
     * for a nested field group, {@code element}'s own key order may differ
     * from it (only the key set is required to match), so nested cells are
     * walked in {@code representative}'s field order rather than
     * {@code element}'s.
     */
    private String buildRow(List<String> fieldOrder, JsonNode representative, JsonNode element) {
        StringBuilder sb = new StringBuilder();
        boolean[] first = {true};
        for (String field : fieldOrder) {
            appendLeafCells(sb, requireField(representative, field), requireField(element, field), first);
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

    private void appendLeafCells(StringBuilder sb, JsonNode representativeValue, JsonNode value, boolean[] first) {
        if (representativeValue.isObject()) {
            for (String field : keysOf(representativeValue)) {
                appendLeafCells(sb, requireField(representativeValue, field), requireField(value, field), first);
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
}
