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
 * segment (comma unless a tab or pipe symbol follows the length). The
 * document's indent step is inferred from its first nesting transition and
 * enforced on every later one (see {@link #validateNestedIndent}).</p>
 *
 * <p>Follows the specification's strict mode: a header's declared count and
 * field-list width must match exactly, or the parser throws a
 * {@link SerdeException}. Two leniencies beyond strict mode:</p>
 *
 * <ul>
 *   <li>A key is read as the literal text up to the first unquoted
 *   {@code :} (or, for an object field, {@code :} or {@code [}), regardless
 *   of whether it matches {@link ToonEncoder}'s stricter unquoted-key
 *   grammar. Whitespace directly adjacent to that delimiter is still
 *   rejected, not trimmed.</li>
 *   <li>A blank line is an error only <em>between</em> two rows/entries/items
 *   of a declared-count body, or between two of a list item's own
 *   continuation fields. A blank line nested deeper inside an item, between
 *   an ordinary object's sibling fields, or between top-level constructs is
 *   skipped.</li>
 *   <li>Indentation is not required to be a multiple of a fixed default of 2
 *   spaces; the document's own indent step, inferred from its first nesting
 *   transition, is enforced instead.</li>
 * </ul>
 *
 * <p>Guards against a maliciously deep document by extending
 * {@link LimitingStream} and charging the nesting-depth budget at each
 * point that descends one level deeper into the resulting {@link JsonNode}
 * tree - both the indentation-driven recursion and the single-line
 * nested-field-group recursion in a tabular header's field list.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.1
 */
final class ToonDocumentParser extends LimitingStream {

    private static final Pattern LINE_SPLIT = Pattern.compile("\r\n|\r|\n");
    private static final char BOM = (char) 0xFEFF;

    private final List<Line> lines;
    private int cursor;

    /**
     * The document's indent step, in spaces - the difference in leading
     * spaces between a block and its nested body. Established from the very
     * first such nesting transition encountered anywhere in
     * the document, then required of every later transition; {@code -1}
     * means not yet established.
     */
    private int indentSize = -1;

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
        int lineNumber = 0;
        boolean pendingBlankLine = false;
        for (String rawLine : LINE_SPLIT.split(text, -1)) {
            lineNumber++;
            String line = ToonEscapes.stripTrailingWhitespace(rawLine);
            if (line.isBlank()) {
                pendingBlankLine = true;
                continue;
            }

            int leadingSpaces;
            try {
                leadingSpaces = ToonEscapes.countLeadingSpaces(line);
            } catch (SerdeException e) {
                throw annotateWithLine(e, lineNumber);
            }
            if (ToonEscapes.isCommentLine(line, leadingSpaces)) {
                continue;
            }

            parsedLines.add(new Line(lineNumber, leadingSpaces, line.substring(leadingSpaces), pendingBlankLine));
            pendingBlankLine = false;
        }

        this.lines = parsedLines;
    }

    JsonNode parseDocument() throws SerdeException {
        if (lines.isEmpty()) {
            return JsonNode.createObjectNode(Map.of());
        }

        Line first = lines.getFirst();
        if (first.indent() != 0) {
            throw new SerdeException("Root content must not be indented at line " + first.lineNumber() + ": " + first.content());
        }

        JsonNode result;
        if (first.content().equals("[]")) {
            cursor = 1;
            result = JsonNode.createArrayNode(List.of());
        } else if (first.content().startsWith("[")) {
            HeaderTail header = parseHeaderTail(first.content(), first.lineNumber());
            cursor = 1;
            result = parseArrayOrKeyedBody(header, first.indent(), first.lineNumber());
        } else {
            // No delimiter is in play for a lone root scalar, so a trailing
            // tab here is never a significant empty-cell marker.
            String rootScalarCandidate = stripInsignificantTrailingTab(first.content());
            if (lines.size() == 1 && looksLikeScalarAtLine(rootScalarCandidate, first.lineNumber())) {
                cursor = 1;
                result = parseScalarToken(rootScalarCandidate, first.lineNumber());
            } else {
                result = parseObjectFields(-1);
            }
        }

        if (cursor != lines.size()) {
            Line extra = lines.get(cursor);
            throw new SerdeException("Unexpected content after the root value at line " + extra.lineNumber() + ": " + extra.content());
        }

        return result;
    }

    private JsonNode parseObjectFields(int parentIndent) throws SerdeException {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        continueObjectFields(parentIndent, values, false);
        return JsonNode.createObjectNode(values);
    }

    /**
     * Consumes every sibling field line more indented than {@code
     * parentIndent}, establishing that indent from the first such line and
     * requiring every further sibling to match it exactly.
     *
     * @param insideListItemBody Whether these fields are a list item's own
     *                           continuation fields, where a blank line
     *                           between them is a strict-mode error, unlike
     *                           between an ordinary object's fields
     */
    private void continueObjectFields(int parentIndent, Map<String, JsonNode> values, boolean insideListItemBody) throws SerdeException {
        if (cursor >= lines.size() || lines.get(cursor).indent() <= parentIndent) {
            return;
        }

        increaseDepth();
        try {
            Line firstField = lines.get(cursor);
            int fieldsIndent = firstField.indent();
            validateNestedIndent(parentIndent, firstField);
            while (cursor < lines.size() && lines.get(cursor).indent() >= fieldsIndent) {
                Line current = lines.get(cursor);
                requireConsistentIndent(current, fieldsIndent, "sibling field");
                if (insideListItemBody && current.precededByBlankLine()) {
                    throw new SerdeException("Blank line inside list item block at line " + current.lineNumber());
                }
                cursor++;
                parseOneField(current, fieldsIndent, values);
            }
        } finally {
            decreaseDepth();
        }
    }

    private void parseOneField(Line line, int parentIndent, Map<String, JsonNode> out) throws SerdeException {
        KeyResult keyResult = readKey(line.content(), line.lineNumber());
        String key = keyResult.key();
        String remainder = keyResult.remainder();

        JsonNode value;
        if (!remainder.isEmpty() && remainder.charAt(0) == '[') {
            HeaderTail header = parseHeaderTail(remainder, line.lineNumber());
            value = parseArrayOrKeyedBody(header, parentIndent, line.lineNumber());
        } else if (!remainder.isEmpty() && remainder.charAt(0) == ':') {
            String token = remainder.substring(1).trim();
            if (token.isEmpty()) {
                if (cursor < lines.size() && lines.get(cursor).indent() > parentIndent) {
                    value = parseObjectFields(parentIndent);
                } else {
                    value = JsonNode.createObjectNode(Map.of());
                }
            } else {
                value = token.equals("[]") ? JsonNode.createArrayNode(List.of()) : parseScalarToken(token, line.lineNumber());
            }
        } else {
            throw new SerdeException("Malformed TOON line at line " + line.lineNumber() + " (expected ':' or '[' after key '" + key + "'): " + line.content());
        }

        if (out.containsKey(key)) {
            throw new SerdeException("Duplicate key '" + key + "' at line " + line.lineNumber() + ": " + line.content());
        }

        out.put(key, value);
    }

    private JsonNode parseArrayOrKeyedBody(HeaderTail header, int parentIndent, int headerLineNumber) throws SerdeException {
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
                return parseInlineArray(header, inlineTail, headerLineNumber);
            }

            return parseListItems(header, parentIndent);
        } finally {
            decreaseDepth();
        }
    }

    private JsonNode parseInlineArray(HeaderTail header, String tail, int lineNumber) throws SerdeException {
        List<String> tokens;
        try {
            tokens = ToonEscapes.splitByDelimiter(tail, header.delimiter());
        } catch (SerdeException e) {
            throw annotateWithLine(e, lineNumber);
        }
        if (tokens.size() != header.length()) {
            throw new SerdeException("Expected " + header.length() + " inline array value(s) but found " + tokens.size() + " at line " + lineNumber + ": " + tail);
        }

        List<JsonNode> values = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            values.add(parseScalarToken(token, lineNumber));
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
            Line rowLine = lines.get(cursor);
            cursor++;
            List<String> cells;
            try {
                cells = ToonEscapes.splitByDelimiter(rowLine.content(), header.delimiter());
            } catch (SerdeException e) {
                throw annotateWithLine(e, rowLine.lineNumber());
            }
            if (cells.size() != leafCount) {
                throw new SerdeException("Expected " + leafCount + " cell(s) in tabular row but found " + cells.size() + " at line " + rowLine.lineNumber() + ": " + rowLine.content());
            }

            rows.add(buildFromFieldSpecs(fields, new CellCursor(cells), rowLine.lineNumber()));
        }

        return JsonNode.createArrayNode(rows);
    }

    private JsonNode parseKeyedTabularEntries(HeaderTail header, List<FieldSpec> fields, int parentIndent) throws SerdeException {
        int leafCount = countLeaves(fields);
        Map<String, JsonNode> entries = new LinkedHashMap<>();
        Integer childIndent = null;
        for (int entryIndex = 0; entryIndex < header.length(); entryIndex++) {
            childIndent = nextLineIndent(parentIndent, childIndent, header.length(), entryIndex, "keyed entry row");
            Line entryLine = lines.get(cursor);
            cursor++;

            KeyResult keyResult = readEntryKey(entryLine.content(), entryLine.lineNumber());
            if (keyResult.remainder().isEmpty() || keyResult.remainder().charAt(0) != ':') {
                throw new SerdeException("Malformed keyed tabular entry at line " + entryLine.lineNumber() + " (expected 'key: values'): " + entryLine.content());
            }

            String valuesText = keyResult.remainder().substring(1);
            if (valuesText.isEmpty()) {
                // splitByDelimiter("") returns a single empty token, which
                // would silently match leafCount == 1 instead of being
                // treated as a missing-cells error. Not trimmed: a lone tab
                // is a significant empty-cell marker under the tab
                // delimiter, per stripTrailingWhitespace.
                throw new SerdeException("Expected " + leafCount + " cell(s) in keyed entry row but found none at line " + entryLine.lineNumber() + ": " + entryLine.content());
            }

            List<String> cells;
            try {
                cells = ToonEscapes.splitByDelimiter(valuesText, header.delimiter());
            } catch (SerdeException e) {
                throw annotateWithLine(e, entryLine.lineNumber());
            }
            if (cells.size() != leafCount) {
                throw new SerdeException("Expected " + leafCount + " cell(s) in keyed entry row but found " + cells.size() + " at line " + entryLine.lineNumber() + ": " + entryLine.content());
            }

            if (entries.containsKey(keyResult.key())) {
                throw new SerdeException("Duplicate key '" + keyResult.key() + "' at line " + entryLine.lineNumber() + ": " + entryLine.content());
            }

            entries.put(keyResult.key(), buildFromFieldSpecs(fields, new CellCursor(cells), entryLine.lineNumber()));
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
        Line itemLine = lines.get(cursor);
        String content = itemLine.content();
        // No delimiter is in play for a bare dash marker either, so a
        // trailing tab here is likewise never a significant empty-cell
        // marker; see stripInsignificantTrailingTab's other call site.
        if (stripInsignificantTrailingTab(content).equals("-")) {
            cursor++;
            return JsonNode.createObjectNode(Map.of());
        }

        if (content.length() < 2 || content.charAt(1) != ' ') {
            throw new SerdeException("Malformed list item at line " + itemLine.lineNumber() + " (expected '-' or '- ...'): " + content);
        }

        String rest = content.substring(1).trim();
        cursor++;
        if (rest.equals("[]")) {
            return JsonNode.createArrayNode(List.of());
        }

        if (looksLikeScalarAtLine(rest, itemLine.lineNumber())) {
            return parseScalarToken(rest, itemLine.lineNumber());
        }

        // A list item that is itself an array will have its depth charged
        // in parseArrayOrKeyedBody.
        if (rest.startsWith("[")) {
            HeaderTail header = parseHeaderTail(rest, itemLine.lineNumber());
            // A keyless header with a field list (tabular "- [N]{f}:" or
            // keyed-tabular "- [N:]{f}:") is only valid at the document
            // root, not as a list item.
            if (header.fields() != null) {
                throw new SerdeException("A keyless header with a field list is only valid at the document root, not as a list item, at line " + itemLine.lineNumber() + ": " + content);
            }
            return parseArrayOrKeyedBody(header, itemIndent, itemLine.lineNumber());
        }

        // Otherwise `rest` is the item object's first field, rendered as if
        // it were a normal field line one indent step in from the item's own
        // indent - matching where it sits after the "- " marker - so any
        // body nested under it validates against that depth. Sibling fields
        // still resolve their own depth from the first sibling line, via
        // continueObjectFields.
        increaseDepth();
        try {
            Map<String, JsonNode> values = new LinkedHashMap<>();
            int fieldIndent = itemIndent + indentSize;
            parseOneField(new Line(itemLine.lineNumber(), itemLine.indent(), rest, false), fieldIndent, values);
            continueObjectFields(itemIndent, values, true);
            return JsonNode.createObjectNode(values);
        } finally {
            decreaseDepth();
        }
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
            int atLine = cursor < lines.size() ? lines.get(cursor).lineNumber() : (lines.isEmpty() ? 1 : lines.get(lines.size() - 1).lineNumber());
            throw new SerdeException("Expected " + declaredCount + " " + what + "(s) but found " + foundSoFar + " at line " + atLine);
        }

        Line currentLine = lines.get(cursor);
        if (childIndent != null) {
            // A blank line between rows/entries/items in a declared-count
            // body is a strict-mode error, unlike between the header and
            // the first one, between sibling object fields, or between
            // top-level constructs, which are skipped elsewhere.
            if (currentLine.precededByBlankLine()) {
                throw new SerdeException("Blank line inside " + what + " block at line " + currentLine.lineNumber());
            }
            requireConsistentIndent(currentLine, childIndent, what);
        } else {
            validateNestedIndent(parentIndent, currentLine);
        }

        return currentLine.indent();
    }

    /**
     * Throws if {@code actualIndent} does not match {@code expectedIndent},
     * the single indent every line in an indentation-bound block must share.
     */
    private static void requireConsistentIndent(Line line, int expectedIndent, String what) throws SerdeException {
        if (line.indent() != expectedIndent) {
            throw new SerdeException("Inconsistent indentation among " + what + " lines at line " + line.lineNumber()
                + " (expected " + expectedIndent + " spaces, found " + line.indent() + ")");
        }
    }

    /**
     * Establishes the document's indent step from the first nesting
     * transition, then validates every later transition against it. Not
     * called for the root level's own fields, which sit at indent 0 with no
     * step to measure (see {@link #continueObjectFields},
     * {@link #nextLineIndent}).
     */
    private void validateNestedIndent(int parentIndent, Line line) throws SerdeException {
        if (parentIndent < 0) {
            return;
        }

        int step = line.indent() - parentIndent;
        if (indentSize == -1) {
            indentSize = step;
            return;
        }

        if (step != indentSize) {
            throw new SerdeException("Non-multiple indentation at line " + line.lineNumber() + ": " + line.indent()
                + " leading space(s) with document indent size " + indentSize + " (expected " + (parentIndent + indentSize) + ")");
        }
    }

    private static KeyResult readKey(String content, int lineNumber) throws SerdeException {
        return readKey(content, lineNumber, true);
    }

    /**
     * Reads a keyed-tabular entry's own key. Unlike an object field's key,
     * this never stops at an unquoted {@code '['}: an entry line has no
     * header syntax of its own, so a bracket in the key text (e.g. {@code
     * k[2]: 5}) is just part of the literal key, not a nested header.
     */
    private static KeyResult readEntryKey(String content, int lineNumber) throws SerdeException {
        return readKey(content, lineNumber, false);
    }

    private static KeyResult readKey(String content, int lineNumber, boolean stopAtBracket) throws SerdeException {
        if (content.isEmpty()) {
            throw new SerdeException("Malformed TOON line (missing key) at line " + lineNumber + ": " + content);
        }

        if (content.startsWith("\"")) {
            try {
                int end = ToonEscapes.findClosingQuote(content, 0);
                String key = ToonEscapes.unquote(content.substring(0, end + 1));
                return new KeyResult(key, content.substring(end + 1));
            } catch (SerdeException e) {
                throw annotateWithLine(e, lineNumber);
            }
        }

        // An unquoted key is the literal text before the first unquoted
        // ':' (or, for an object field, ':' or '['), regardless of whether
        // it matches the encoder's own unquoted-key grammar (that grammar
        // governs encoder quoting, not decoder acceptance). Whitespace
        // directly adjacent to the delimiter is still rejected, not trimmed.
        int i = 0;
        while (i < content.length() && content.charAt(i) != ':' && (!stopAtBracket || content.charAt(i) != '[')) {
            i++;
        }

        String key = content.substring(0, i);
        if (key.isEmpty() || !key.equals(key.strip())) {
            throw new SerdeException("Malformed TOON line (invalid or missing key) at line " + lineNumber + ": " + content);
        }

        return new KeyResult(key, content.substring(i));
    }

    private HeaderTail parseHeaderTail(String remainder, int lineNumber) throws SerdeException {
        int i = 1; // skip '['
        int lengthStart = i;
        while (i < remainder.length() && ToonEscapes.isAsciiDigit(remainder.charAt(i))) {
            i++;
        }

        int length = getHeaderTailLength(remainder, i, lengthStart, lineNumber);

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
            throw new SerdeException("Malformed TOON header (missing ']') at line " + lineNumber + ": " + remainder);
        }
        i++;

        List<FieldSpec> fields = null;
        if (i < remainder.length() && remainder.charAt(i) == '{') {
            ParseResult<List<FieldSpec>> result = parseFieldList(remainder, i, delimiter, lineNumber);
            fields = result.value();
            i = result.nextIndex();
        }

        if (keyed && fields == null) {
            throw new SerdeException("Keyed TOON header requires a field list at line " + lineNumber + ": " + remainder);
        }
        if (i >= remainder.length() || remainder.charAt(i) != ':') {
            throw new SerdeException("Malformed TOON header (missing ':') at line " + lineNumber + ": " + remainder);
        }
        i++;

        // Only the mandatory leading space (before the inline value list
        // begins) is stripped here, not trailing whitespace: the line's own
        // trailing spaces were already stripped during pre-processing, and a
        // remaining trailing tab is not incidental whitespace when tab is
        // the active delimiter - it marks an intentionally-empty last cell.
        // When tab is not the active delimiter, a trailing tab has no such
        // meaning and is stripped like any other incidental whitespace, so
        // it does not get mistaken for a one-token inline array body.
        String tail = i < remainder.length() ? stripLeadingSpaces(remainder.substring(i)) : "";
        tail = stripInsignificantTrailingTab(tail, delimiter);
        if (fields != null && !tail.isEmpty()) {
            throw new SerdeException("Unexpected content after a field-list header at line " + lineNumber + ": " + remainder);
        }

        return new HeaderTail(length, keyed, delimiter, fields, tail.isEmpty() ? null : tail);
    }

    private static String stripLeadingSpaces(String s) {
        int start = 0;
        while (start < s.length() && s.charAt(start) == ' ') {
            start++;
        }
        return s.substring(start);
    }

    /**
     * Strips a trailing tab from a header's inline tail, unless {@code
     * delimiter} is tab, in which case a trailing tab marks an
     * intentionally-empty last cell and is kept.
     */
    private static String stripInsignificantTrailingTab(String s, char delimiter) {
        if (delimiter == '\t') {
            return s;
        }
        return stripInsignificantTrailingTab(s);
    }

    /**
     * Strips a trailing tab where no delimiter is in play at all - a bare
     * root scalar or a bare list-item dash marker - so it can never be an
     * intentionally-empty cell and is always incidental whitespace.
     */
    private static String stripInsignificantTrailingTab(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\t') {
            end--;
        }
        return s.substring(0, end);
    }

    /**
     * Calls {@link ToonEscapes#looksLikeScalar}, annotating any thrown
     * {@link SerdeException} (from an unterminated quoted scalar) with the
     * source line number, like every other line-unaware {@link ToonEscapes}
     * call in this file.
     */
    private static boolean looksLikeScalarAtLine(String content, int lineNumber) throws SerdeException {
        try {
            return ToonEscapes.looksLikeScalar(content);
        } catch (SerdeException e) {
            throw annotateWithLine(e, lineNumber);
        }
    }

    /**
     * Wraps a {@link SerdeException} thrown by a line-unaware helper (one of
     * the {@link ToonEscapes} string utilities) with the source line number
     * of the call site. Always appends, rather than checking whether the
     * message already looks annotated: the wrapped helpers never embed "at
     * line" themselves, so a text-based check would misfire whenever the
     * raw (user-controlled) input text they echo back happens to contain
     * that literal substring.
     */
    private static SerdeException annotateWithLine(SerdeException e, int lineNumber) {
        return new SerdeException(e.getMessage() + " at line " + lineNumber, e);
    }

    private static int getHeaderTailLength(String remainder, int i, int lengthStart, int lineNumber) throws SerdeException {
        if (i == lengthStart) {
            throw new SerdeException("Malformed TOON header (missing length) at line " + lineNumber + ": " + remainder);
        }

        String lengthDigits = remainder.substring(lengthStart, i);
        if (lengthDigits.length() > 1 && lengthDigits.charAt(0) == '0') {
            throw new SerdeException("Malformed TOON header (leading zero in length) at line " + lineNumber + ": " + remainder);
        }

        int length;
        try {
            length = Integer.parseInt(lengthDigits);
        } catch (NumberFormatException e) {
            throw new SerdeException("TOON header length is too large at line " + lineNumber + ": " + remainder);
        }

        return length;
    }

    private ParseResult<List<FieldSpec>> parseFieldList(String s, int start, char delimiter, int lineNumber) throws SerdeException {
        increaseDepth();
        try {
            return parseFieldListEntries(s, start, delimiter, lineNumber);
        } finally {
            decreaseDepth();
        }
    }

    private ParseResult<List<FieldSpec>> parseFieldListEntries(String s, int start, char delimiter, int lineNumber) throws SerdeException {
        int i = start + 1; // skip '{'
        List<FieldSpec> specs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (true) {
            if (i >= s.length()) {
                throw new SerdeException("Unterminated field list at line " + lineNumber + ": " + s);
            }

            String name;
            if (s.charAt(i) == '"') {
                try {
                    int end = ToonEscapes.findClosingQuote(s, i);
                    name = ToonEscapes.unquote(s.substring(i, end + 1));
                    i = end + 1;
                } catch (SerdeException e) {
                    throw annotateWithLine(e, lineNumber);
                }
            } else {
                int nameStart = i;
                while (i < s.length() && s.charAt(i) != delimiter && s.charAt(i) != '{' && s.charAt(i) != '}') {
                    i++;
                }

                if (i == nameStart) {
                    throw new SerdeException("Malformed field list (empty field name) at line " + lineNumber + ": " + s);
                }

                name = s.substring(nameStart, i);
                // A field name is accepted literally regardless of whether
                // it matches the encoder's unquoted-key grammar (same
                // leniency as readKey). Whitespace directly adjacent to
                // the delimiter/brace is still rejected, not trimmed.
                if (!name.equals(name.strip())) {
                    throw new SerdeException("Malformed field list (whitespace around unquoted field name '" + name + "') at line " + lineNumber + ": " + s);
                }
            }

            List<FieldSpec> nested = null;
            if (i < s.length() && s.charAt(i) == '{') {
                ParseResult<List<FieldSpec>> nestedResult = parseFieldList(s, i, delimiter, lineNumber);
                nested = nestedResult.value();
                i = nestedResult.nextIndex();
            }

            if (!names.add(name)) {
                throw new SerdeException("Duplicate field name '" + name + "' in field list at line " + lineNumber + ": " + s);
            }

            specs.add(new FieldSpec(name, nested));

            if (i >= s.length()) {
                throw new SerdeException("Unterminated field list at line " + lineNumber + ": " + s);
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

            throw new SerdeException("Malformed field list (expected delimiter or '}') at line " + lineNumber + ": " + s);
        }

        return new ParseResult<>(specs, i);
    }

    /**
     * Rebuilds one tabular/keyed-tabular row's nested field groups into a
     * {@link JsonNode} object, per row. Depth is charged here, not just once
     * while the field list was parsed: that charge is released long before
     * any row is built, so without a charge of its own here, a header with a
     * deeply nested field list would let every row rebuild that nesting
     * uncounted against the shared budget.
     */
    private JsonNode buildFromFieldSpecs(List<FieldSpec> specs, CellCursor cells, int lineNumber) throws SerdeException {
        increaseDepth();
        try {
            Map<String, JsonNode> values = new LinkedHashMap<>();
            for (FieldSpec spec : specs) {
                List<FieldSpec> nested = spec.nested();
                JsonNode value = nested != null ? buildFromFieldSpecs(nested, cells, lineNumber) : parseScalarToken(cells.next(), lineNumber);
                values.put(spec.name(), value);
            }
            return JsonNode.createObjectNode(values);
        } finally {
            decreaseDepth();
        }
    }

    private int countLeaves(List<FieldSpec> specs) throws SerdeException {
        increaseDepth();
        try {
            int count = 0;
            for (FieldSpec spec : specs) {
                List<FieldSpec> nested = spec.nested();
                count += nested != null ? countLeaves(nested) : 1;
            }
            return count;
        } finally {
            decreaseDepth();
        }
    }

    private static JsonNode parseScalarToken(String token, int lineNumber) throws SerdeException {
        if (token.startsWith("\"")) {
            int end;
            try {
                end = ToonEscapes.findClosingQuote(token, 0);
            } catch (SerdeException e) {
                throw annotateWithLine(e, lineNumber);
            }
            if (end != token.length() - 1) {
                throw new SerdeException("Unexpected characters after closing quote at line " + lineNumber + ": " + token);
            }
            try {
                return JsonNode.createStringNode(ToonEscapes.unquote(token));
            } catch (SerdeException e) {
                throw annotateWithLine(e, lineNumber);
            }
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
            return parseNumberToken(token, lineNumber);
        }

        return JsonNode.createStringNode(token);
    }

    private static JsonNode parseNumberToken(String token, int lineNumber) throws SerdeException {
        if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
            try {
                return JsonNode.createNumberNode(new BigDecimal(token));
            } catch (NumberFormatException e) {
                // The decode number grammar matched, but the exponent is too
                // large for BigDecimal's own int-sized exponent field.
                throw new SerdeException("Number out of range at line " + lineNumber + ": " + token, e);
            }
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
     * @param lineNumber          The 1-based source line number
     * @param indent              The number of leading space characters
     * @param content             The line content, with indentation and trailing whitespace stripped
     * @param precededByBlankLine Whether a blank line was skipped immediately
     *                            before this line; used by {@link #nextLineIndent}
     *                            to reject a blank line inside a declared-count body
     */
    private record Line(int lineNumber, int indent, String content, boolean precededByBlankLine) {
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
