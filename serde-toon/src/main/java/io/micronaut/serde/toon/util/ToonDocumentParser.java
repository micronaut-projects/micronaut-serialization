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

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A stateful recursive-descent parser over one TOON document's text,
 * instantiated fresh per {@link ToonDecoder#parse} call so the singleton
 * {@link ToonDecoder} bean stays thread-safe.
 *
 * <p>Delimiter and indentation are read from the document, not configured.
 * Each array/object header declares its own delimiter in its bracket
 * segment (comma unless a tab or pipe symbol follows the length).
 * Indentation depth is inferred by peeking a nested body's first line to
 * establish its indent, then requiring every sibling to match it exactly;
 * a shallower line ends the body, a deeper one is inconsistent
 * indentation.</p>
 *
 * <p>Operates in the specification's strict mode: it reads exactly the
 * count a header declares, and a declared-count, field-list-width, or
 * indentation mismatch is a {@link SerdeException}. An unquoted key is
 * read only up to its own text, matching what {@link ToonEncoder} emits;
 * a non-conforming unquoted key containing a raw {@code [} or {@code :}
 * is not specially handled.</p>
 *
 * <p>Guards against a maliciously deep document the same way every other
 * decoder in this codebase does: it extends {@link LimitingStream} and
 * checks the remaining nesting-depth budget at each point that descends one
 * level deeper into the resulting {@link JsonNode} tree - both the
 * indentation-driven recursion (nested objects, arrays) and the single-line
 * nested-field-group recursion in a tabular header's field list.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
final class ToonDocumentParser extends LimitingStream {

    private static final Pattern LINE_SPLIT = Pattern.compile("\r\n|\r|\n");
    private static final char BOM = (char) 0xFEFF;

    private final List<Line> lines;
    private int cursor;

    /**
     * Creates a parser over the given TOON document text, splitting and
     * classifying its lines up front.
     *
     * @param text            The TOON document text
     * @param remainingLimits The nesting-depth budget
     * @throws SerdeException If a line's indentation uses a tab
     */
    ToonDocumentParser(String text, LimitingStream.RemainingLimits remainingLimits) throws SerdeException {
        super(remainingLimits);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }

        List<Line> parsedLines = new ArrayList<>();
        for (String rawLine : LINE_SPLIT.split(text, -1)) {
            String line = ToonEscapes.stripTrailingWhitespace(rawLine);
            if (line.isBlank()) {
                continue;
            }

            int leadingSpaces = ToonEscapes.countLeadingSpaces(line);
            if (ToonEscapes.isCommentLine(line, leadingSpaces)) {
                continue;
            }

            parsedLines.add(new Line(leadingSpaces, line.substring(leadingSpaces)));
        }

        this.lines = parsedLines;
    }

    JsonNode parseDocument() throws SerdeException {
        if (lines.isEmpty()) {
            return JsonNode.createObjectNode(Map.of());
        }

        Line first = lines.getFirst();
        if (first.indent() != 0) {
            throw new SerdeException("Root content must not be indented: " + first.content());
        }

        JsonNode result;
        if (first.content().equals("[]")) {
            cursor = 1;
            result = JsonNode.createArrayNode(List.of());
        } else if (first.content().startsWith("[")) {
            HeaderTail header = parseHeaderTail(first.content());
            cursor = 1;
            result = parseArrayOrKeyedBody(header, first.indent());
        } else if (lines.size() == 1 && ToonEscapes.looksLikeScalar(first.content())) {
            cursor = 1;
            result = parseScalarToken(first.content());
        } else {
            result = parseObjectFields(-1);
        }

        if (cursor != lines.size()) {
            throw new SerdeException("Unexpected content after the root value: " + lines.get(cursor).content());
        }

        return result;
    }

    private JsonNode parseObjectFields(int parentIndent) throws SerdeException {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        continueObjectFields(parentIndent, values);
        return JsonNode.createObjectNode(values);
    }

    /**
     * Consumes every sibling field line more indented than {@code
     * parentIndent}, establishing that indent from the first such line and
     * requiring every further sibling to match it exactly.
     */
    private void continueObjectFields(int parentIndent, Map<String, JsonNode> values) throws SerdeException {
        if (cursor >= lines.size() || lines.get(cursor).indent() <= parentIndent) {
            return;
        }

        increaseDepth();
        try {
            int fieldsIndent = lines.get(cursor).indent();
            while (cursor < lines.size() && lines.get(cursor).indent() >= fieldsIndent) {
                requireConsistentIndent(lines.get(cursor).indent(), fieldsIndent, "sibling field");
                String content = lines.get(cursor).content();
                cursor++;
                parseOneField(content, fieldsIndent, values);
            }
        } finally {
            decreaseDepth();
        }
    }

    private void parseOneField(String content, int parentIndent, Map<String, JsonNode> out) throws SerdeException {
        KeyResult keyResult = readKey(content);
        String key = keyResult.key();
        String remainder = keyResult.remainder();

        JsonNode value;
        if (!remainder.isEmpty() && remainder.charAt(0) == '[') {
            HeaderTail header = parseHeaderTail(remainder);
            value = parseArrayOrKeyedBody(header, parentIndent);
        } else if (!remainder.isEmpty() && remainder.charAt(0) == ':') {
            String token = remainder.substring(1).trim();
            if (token.isEmpty()) {
                if (cursor < lines.size() && lines.get(cursor).indent() > parentIndent) {
                    value = parseObjectFields(parentIndent);
                } else {
                    value = JsonNode.createObjectNode(Map.of());
                }
            } else {
                value = token.equals("[]") ? JsonNode.createArrayNode(List.of()) : parseScalarToken(token);
            }
        } else {
            throw new SerdeException("Malformed TOON line (expected ':' or '[' after key '" + key + "'): " + content);
        }

        if (out.containsKey(key)) {
            throw new SerdeException("Duplicate key '" + key + "': " + content);
        }

        out.put(key, value);
    }

    private JsonNode parseArrayOrKeyedBody(HeaderTail header, int parentIndent) throws SerdeException {
        increaseDepth();
        try {
            List<FieldSpec> fields = header.fields();
            if (header.keyed()) {
                return parseKeyedTabularEntries(header, Objects.requireNonNull(fields, "keyed header without a field list"), parentIndent);
            }

            if (fields != null) {
                return parseTabularRows(header, fields, parentIndent);
            }

            String inlineTail = header.inlineTail();
            if (inlineTail != null) {
                return parseInlineArray(header, inlineTail);
            }

            return parseListItems(header, parentIndent);
        } finally {
            decreaseDepth();
        }
    }

    private JsonNode parseInlineArray(HeaderTail header, String tail) throws SerdeException {
        List<String> tokens = ToonEscapes.splitByDelimiter(tail, header.delimiter());
        if (tokens.size() != header.length()) {
            throw new SerdeException("Expected " + header.length() + " inline array value(s) but found " + tokens.size() + ": " + tail);
        }

        List<JsonNode> values = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            values.add(parseScalarToken(token));
        }

        return JsonNode.createArrayNode(values);
    }

    private JsonNode parseTabularRows(HeaderTail header, List<FieldSpec> fields, int parentIndent) throws SerdeException {
        int leafCount = countLeaves(fields);
        // Not pre-sized from header.length(): that count is untrusted,
        // read straight off the document, and pre-allocating against it
        // before confirming that many rows actually follow would let a
        // small malicious document (e.g. a declared count near
        // Integer.MAX_VALUE) force a huge allocation.
        List<JsonNode> rows = new ArrayList<>();
        Integer childIndent = null;

        for (int rowIndex = 0; rowIndex < header.length(); rowIndex++) {
            childIndent = nextLineIndent(parentIndent, childIndent, header.length(), rowIndex, "tabular row");
            String rowContent = lines.get(cursor).content();
            cursor++;
            List<String> cells = ToonEscapes.splitByDelimiter(rowContent, header.delimiter());
            if (cells.size() != leafCount) {
                throw new SerdeException("Expected " + leafCount + " cell(s) in tabular row but found " + cells.size() + ": " + rowContent);
            }

            rows.add(buildFromFieldSpecs(fields, new CellCursor(cells)));
        }

        return JsonNode.createArrayNode(rows);
    }

    private JsonNode parseKeyedTabularEntries(HeaderTail header, List<FieldSpec> fields, int parentIndent) throws SerdeException {
        int leafCount = countLeaves(fields);
        Map<String, JsonNode> entries = new LinkedHashMap<>();
        Integer childIndent = null;
        for (int entryIndex = 0; entryIndex < header.length(); entryIndex++) {
            childIndent = nextLineIndent(parentIndent, childIndent, header.length(), entryIndex, "keyed entry row");
            String content = lines.get(cursor).content();
            cursor++;

            KeyResult keyResult = readKey(content);
            if (keyResult.remainder().isEmpty() || keyResult.remainder().charAt(0) != ':') {
                throw new SerdeException("Malformed keyed tabular entry (expected 'key: values'): " + content);
            }

            List<String> cells = ToonEscapes.splitByDelimiter(keyResult.remainder().substring(1), header.delimiter());
            if (cells.size() != leafCount) {
                throw new SerdeException("Expected " + leafCount + " cell(s) in keyed entry row but found " + cells.size() + ": " + content);
            }

            if (entries.containsKey(keyResult.key())) {
                throw new SerdeException("Duplicate key '" + keyResult.key() + "': " + content);
            }

            entries.put(keyResult.key(), buildFromFieldSpecs(fields, new CellCursor(cells)));
        }

        return JsonNode.createObjectNode(entries);
    }

    private JsonNode parseListItems(HeaderTail header, int parentIndent) throws SerdeException {
        // Not pre-sized from header.length(); see parseTabularRows.
        List<JsonNode> items = new ArrayList<>();
        Integer childIndent = null;
        for (int itemIndex = 0; itemIndex < header.length(); itemIndex++) {
            childIndent = nextLineIndent(parentIndent, childIndent, header.length(), itemIndex, "list item");
            items.add(parseListItem(childIndent));
        }

        return JsonNode.createArrayNode(items);
    }

    private JsonNode parseListItem(int itemIndent) throws SerdeException {
        String content = lines.get(cursor).content();
        if (content.equals("-")) {
            cursor++;
            return JsonNode.createObjectNode(Map.of());
        }

        if (content.length() < 2 || content.charAt(1) != ' ') {
            throw new SerdeException("Malformed list item (expected '-' or '- ...'): " + content);
        }

        String rest = content.substring(1).trim();
        cursor++;
        if (rest.equals("[]")) {
            return JsonNode.createArrayNode(List.of());
        }

        if (rest.startsWith("[")) {
            HeaderTail header = parseHeaderTail(rest);
            return parseArrayOrKeyedBody(header, itemIndent);
        }

        if (ToonEscapes.looksLikeScalar(rest)) {
            return parseScalarToken(rest);
        }

        // Otherwise `rest` is the item object's first field, rendered as if
        // it were a normal field line at the item's own indent (mirroring
        // ToonEncoder#writeListItem, which renders the whole item as if it
        // started one level deeper and only then replaces the first line's
        // indentation with the hyphen marker).
        Map<String, JsonNode> values = new LinkedHashMap<>();
        parseOneField(rest, itemIndent, values);
        continueObjectFields(itemIndent, values);
        return JsonNode.createObjectNode(values);
    }

    /**
     * Establishes (on the first call for a given block, when {@code
     * childIndent} is {@code null}) or validates (on every later call) the
     * single indent every row/entry/item in a declared-count block must
     * share, relative to the header/parent line's own indent.
     *
     * @return The resolved indent, to pass as {@code childIndent} on the next call
     */
    private int nextLineIndent(int parentIndent, @Nullable Integer childIndent, int declaredCount, int foundSoFar, String what) throws SerdeException {
        if (cursor >= lines.size() || lines.get(cursor).indent() <= parentIndent) {
            throw new SerdeException("Expected " + declaredCount + " " + what + "(s) but found " + foundSoFar);
        }

        int indent = lines.get(cursor).indent();
        if (childIndent != null) {
            requireConsistentIndent(indent, childIndent, what);
        }

        return indent;
    }

    /**
     * Throws if {@code actualIndent} does not match {@code expectedIndent},
     * the single indent every line in an indentation-bound block must share.
     */
    private static void requireConsistentIndent(int actualIndent, int expectedIndent, String what) throws SerdeException {
        if (actualIndent != expectedIndent) {
            throw new SerdeException("Inconsistent indentation among " + what + " lines");
        }
    }

    private static KeyResult readKey(String content) throws SerdeException {
        if (content.isEmpty()) {
            throw new SerdeException("Malformed TOON line (missing key): " + content);
        }

        if (content.startsWith("\"")) {
            int end = ToonEscapes.findClosingQuote(content, 0);
            String key = ToonEscapes.unquote(content.substring(0, end + 1));
            return new KeyResult(key, content.substring(end + 1));
        }

        if (!ToonEscapes.isKeyStart(content.charAt(0))) {
            throw new SerdeException("Malformed TOON line (invalid or missing key): " + content);
        }

        int i = 1;
        while (i < content.length() && ToonEscapes.isKeyPart(content.charAt(i))) {
            i++;
        }

        return new KeyResult(content.substring(0, i), content.substring(i));
    }

    private HeaderTail parseHeaderTail(String remainder) throws SerdeException {
        int i = 1; // skip '['
        int lengthStart = i;
        while (i < remainder.length() && ToonEscapes.isAsciiDigit(remainder.charAt(i))) {
            i++;
        }

        int length = getHeaderTailLength(remainder, i, lengthStart);

        boolean keyed = false;
        if (i < remainder.length() && remainder.charAt(i) == ':') {
            keyed = true;
            i++;
        }

        char delimiter = ',';
        if (i < remainder.length() && (remainder.charAt(i) == '\t' || remainder.charAt(i) == '|')) {
            delimiter = remainder.charAt(i);
            i++;
        }

        if (i >= remainder.length() || remainder.charAt(i) != ']') {
            throw new SerdeException("Malformed TOON header (missing ']'): " + remainder);
        }
        i++;

        List<FieldSpec> fields = null;
        if (i < remainder.length() && remainder.charAt(i) == '{') {
            ParseResult<List<FieldSpec>> result = parseFieldList(remainder, i, delimiter);
            fields = result.value();
            i = result.nextIndex();
        }

        if (keyed && fields == null) {
            throw new SerdeException("Keyed TOON header requires a field list: " + remainder);
        }
        if (i >= remainder.length() || remainder.charAt(i) != ':') {
            throw new SerdeException("Malformed TOON header (missing ':'): " + remainder);
        }
        i++;

        String tail = i < remainder.length() ? remainder.substring(i).trim() : "";
        if (fields != null && !tail.isEmpty()) {
            throw new SerdeException("Unexpected content after a field-list header: " + remainder);
        }

        return new HeaderTail(length, keyed, delimiter, fields, tail.isEmpty() ? null : tail);
    }

    private static int getHeaderTailLength(String remainder, int i, int lengthStart) throws SerdeException {
        if (i == lengthStart) {
            throw new SerdeException("Malformed TOON header (missing length): " + remainder);
        }

        String lengthDigits = remainder.substring(lengthStart, i);
        if (lengthDigits.length() > 1 && lengthDigits.charAt(0) == '0') {
            throw new SerdeException("Malformed TOON header (leading zero in length): " + remainder);
        }

        int length;
        try {
            length = Integer.parseInt(lengthDigits);
        } catch (NumberFormatException e) {
            throw new SerdeException("TOON header length is too large: " + remainder);
        }

        return length;
    }

    private ParseResult<List<FieldSpec>> parseFieldList(String s, int start, char delimiter) throws SerdeException {
        increaseDepth();
        try {
            return parseFieldListEntries(s, start, delimiter);
        } finally {
            decreaseDepth();
        }
    }

    private ParseResult<List<FieldSpec>> parseFieldListEntries(String s, int start, char delimiter) throws SerdeException {
        int i = start + 1; // skip '{'
        List<FieldSpec> specs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (true) {
            if (i >= s.length()) {
                throw new SerdeException("Unterminated field list: " + s);
            }

            String name;
            if (s.charAt(i) == '"') {
                int end = ToonEscapes.findClosingQuote(s, i);
                name = ToonEscapes.unquote(s.substring(i, end + 1));
                i = end + 1;
            } else {
                int nameStart = i;
                while (i < s.length() && s.charAt(i) != delimiter && s.charAt(i) != '{' && s.charAt(i) != '}') {
                    i++;
                }

                if (i == nameStart) {
                    throw new SerdeException("Malformed field list (empty field name): " + s);
                }

                name = s.substring(nameStart, i);
                if (!ToonEscapes.isValidUnquotedKey(name)) {
                    throw new SerdeException("Malformed field list (invalid unquoted field name '" + name + "'): " + s);
                }
            }

            List<FieldSpec> nested = null;
            if (i < s.length() && s.charAt(i) == '{') {
                ParseResult<List<FieldSpec>> nestedResult = parseFieldList(s, i, delimiter);
                nested = nestedResult.value();
                i = nestedResult.nextIndex();
            }

            if (!names.add(name)) {
                throw new SerdeException("Duplicate field name '" + name + "' in field list: " + s);
            }

            specs.add(new FieldSpec(name, nested));

            if (i >= s.length()) {
                throw new SerdeException("Unterminated field list: " + s);
            }
            char c = s.charAt(i);
            if (c == delimiter) {
                i++;
                continue;
            }

            if (c == '}') {
                i++;
                break;
            }

            throw new SerdeException("Malformed field list (expected delimiter or '}'): " + s);
        }

        return new ParseResult<>(specs, i);
    }

    private static JsonNode buildFromFieldSpecs(List<FieldSpec> specs, CellCursor cells) throws SerdeException {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (FieldSpec spec : specs) {
            List<FieldSpec> nested = spec.nested();
            JsonNode value = nested != null ? buildFromFieldSpecs(nested, cells) : parseScalarToken(cells.next());
            values.put(spec.name(), value);
        }
        return JsonNode.createObjectNode(values);
    }

    private static int countLeaves(List<FieldSpec> specs) {
        int count = 0;
        for (FieldSpec spec : specs) {
            List<FieldSpec> nested = spec.nested();
            count += nested != null ? countLeaves(nested) : 1;
        }
        return count;
    }

    private static JsonNode parseScalarToken(String token) throws SerdeException {
        if (token.startsWith("\"")) {
            int end = ToonEscapes.findClosingQuote(token, 0);
            if (end != token.length() - 1) {
                throw new SerdeException("Unexpected characters after closing quote: " + token);
            }
            return JsonNode.createStringNode(ToonEscapes.unquote(token));
        }

        switch (token) {
            case "true" -> {
                return JsonNode.createBooleanNode(true);
            }

            case "false" -> {
                return JsonNode.createBooleanNode(false);
            }

            case "null" -> {
                return JsonNode.nullNode();
            }

            default -> {
            }
        }

        if (ToonEscapes.isNumberToken(token)) {
            return parseNumberToken(token);
        }

        return JsonNode.createStringNode(token);
    }

    private static JsonNode parseNumberToken(String token) {
        if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
            return JsonNode.createNumberNode(new BigDecimal(token));
        }

        try {
            return JsonNode.createNumberNode(Long.parseLong(token));
        } catch (NumberFormatException e) {
            return JsonNode.createNumberNode(new BigInteger(token));
        }
    }

    /**
     * One non-blank, non-comment source line: its raw indentation (leading
     * space count) and its content with leading indentation and trailing
     * whitespace already stripped.
     *
     * @param indent  The number of leading space characters
     * @param content The line content, with indentation and trailing whitespace stripped
     */
    private record Line(int indent, String content) {
    }

    /**
     * One field name in a tabular/keyed-tabular header's field list: either
     * a leaf column ({@code nested == null}) or a nested field group folded
     * from a uniform nested-object column.
     *
     * @param name   The field name
     * @param nested The nested field group's own fields, or {@code null} for a leaf column
     */
    private record FieldSpec(String name, @Nullable List<FieldSpec> nested) {
    }

    /**
     * A parsed array/keyed-tabular header: its declared length, whether it
     * is a keyed header, the delimiter this header (and only this header)
     * uses, its field list if it has one, and any inline content following
     * its closing colon on the same physical line.
     *
     * @param length     The declared element/row/entry count
     * @param keyed      Whether this is a keyed-tabular header
     * @param delimiter  The delimiter this header declares, read from its own bracket segment
     * @param fields     The field list, or {@code null} for an inline or list-form header
     * @param inlineTail Content following the closing colon on the same line, or {@code null}
     */
    private record HeaderTail(
        int length,
        boolean keyed,
        char delimiter,
        @Nullable List<FieldSpec> fields,
        @Nullable String inlineTail
    ) {
    }

    private record KeyResult(String key, String remainder) {
    }

    private record ParseResult<T>(T value, int nextIndex) {
    }

    /**
     * A single read-once cursor over a tabular/keyed-tabular row's cells,
     * consumed depth-first as {@link FieldSpec} leaves are visited.
     */
    private static final class CellCursor {
        private final List<String> cells;
        private int index;

        CellCursor(List<String> cells) {
            this.cells = cells;
        }

        String next() {
            return cells.get(index++);
        }
    }
}
