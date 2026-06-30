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
package io.micronaut.serde.csv;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Converts comma-separated rows to {@code List<List<String>>} arguments.
 * Jackson dataformat csv behavior returns List<List<String>> for non defined schema table as well Serde-csv, whereas it returns List<Map<String, String>> when the CSV has a header row and first-row header handling is enabled.
 * Each Map<String, String> represents one row keyed by the header names. We can use a bean type such as Argument.listOf(CsvPointRow.class) when the schema is fixed and should bind to bean properties.
 *
 * @see <a href="https://github.com/FasterXML/jackson-dataformats-text/tree/3.x/csv#data-binding-with-schema">Csv without schema</a>
 * @since 3.1.0
 * @author Hamza Mousrij
 */
@Internal
@Singleton
public class CsvConverter {

    static JsonNode parse(String csv, Argument<?> type, SerdeCsvConfiguration csvConfiguration) {
        if (type.getFirstTypeVariable()
            .filter(typeVariable -> Iterable.class.isAssignableFrom(typeVariable.getType()))
            .isPresent()) {
            return parseNoSchemaRows(csv);
        }
        if (csvConfiguration.getHeader() == SerdeCsvConfiguration.Header.FIRST_ROW) {
            return parseWithSchema(csv);
        }
        return parseNoSchema(csv);
    }

    static String write(JsonNode tree, SerdeCsvConfiguration csvConfiguration) throws SerdeException {
        return write(tree, csvConfiguration, null);
    }

    static String write(JsonNode tree, SerdeCsvConfiguration csvConfiguration, @Nullable List<String> headers) throws SerdeException {
        if (tree.isNull()) {
            return "";
        }
        var builder = new StringBuilder();
        appendCsvDocument(builder, tree, csvConfiguration, headers);
        return builder.toString();
    }

    private static void appendCsvDocument(StringBuilder builder,
                                          JsonNode tree,
                                          SerdeCsvConfiguration csvConfiguration,
                                          @Nullable List<String> configuredHeaders) throws SerdeException {
        if (tree.isArray()) {
            if (csvConfiguration.getWriteHeader() == SerdeCsvConfiguration.Header.FIRST_ROW) {
                List<String> headers = configuredHeaders == null ? headersFromRows(tree) : configuredHeaders;
                if (!headers.isEmpty()) {
                    appendRow(builder, headers);
                }
                for (JsonNode row : tree.values()) {
                    appendSchemaRow(builder, row, headers);
                }
            } else {
                for (JsonNode row : tree.values()) {
                    appendRow(builder, row);
                }
            }
        } else if (tree.isObject()) {
            if (csvConfiguration.getWriteHeader() == SerdeCsvConfiguration.Header.FIRST_ROW) {
                List<String> headers = configuredHeaders == null ? headersFromRow(tree) : configuredHeaders;
                appendRow(builder, headers);
                appendObjectRow(builder, tree, headers);
            } else {
                appendRow(builder, tree);
            }
        } else {
            appendCell(builder, cell(tree));
            builder.append('\n');
        }
    }

    /**
     * Returns CSV rows as JSON objects where keys are column indexes counted from zero.
     * For example:
     * <pre>
     * [
     *   {"0": "1", "1": "2", "2": "true"},
     *   {"0": "2", "1": "9", "2": "false"}
     * ]
     * </pre>
     *
     * @param csv The CSV content
     * @return The indexed object rows
     */
    private static JsonNode parseNoSchema(String csv) {
        List<JsonNode> rows = parseRows(csv).stream()
            .map(cells -> {
                var index = new AtomicInteger(0);

                Map<String, JsonNode> rowMap = cells.stream()
                    .collect(Collectors.toMap(
                        cell -> Integer.toString(index.getAndIncrement()),
                        JsonNode::createStringNode,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new
                    ));

                return JsonNode.createObjectNode(rowMap);
            })
            .toList();

        return JsonNode.createArrayNode(rows);
    }

    private static JsonNode parseWithSchema(String csv) {
        List<List<String>> rows = parseRows(csv);
        var objects = new ArrayList<JsonNode>();
        if (rows.isEmpty()) {
            return JsonNode.createArrayNode(objects);
        }
        List<String> headers = rows.get(0);

        var row = rows.stream()
            .skip(1)
            .map(cells -> {
                var index = new AtomicInteger(0);

                Map<String, JsonNode> rowMap = cells.stream()
                    .collect(Collectors.toMap(
                        cell -> headers.get(index.getAndIncrement()),
                        JsonNode::createStringNode,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new
                    ));
                return JsonNode.createObjectNode(rowMap);
            })
            .toList();
        return JsonNode.createArrayNode(row);

    }

    /**
     * Returns CSV rows as JSON arrays so each CSV value stays as a cell value.
     * For example:
     * <pre>
     * [
     *   ["1", "2", "true"],
     *   ["2", "9", "false"]
     * ]
     * </pre>
     *
     * @param csv The CSV content
     * @return The array rows
     */
    private static JsonNode parseNoSchemaRows(String csv) {
        var rows = parseRows(csv).stream()
            .map(cells -> {
                var row = cells.stream()
                    .map(JsonNode::createStringNode)
                    .toList();
                return JsonNode.createArrayNode(row);
            })
            .toList();

        return JsonNode.createArrayNode(rows);
    }

    static List<List<String>> parseRows(String csv) {
        return csv.lines()
            .filter(line -> !line.isBlank())
            .map(line -> Arrays.stream(line.split(",", -1))
                .map(String::trim)
                .collect(Collectors.toList()))
            .collect(Collectors.toList());
    }

    private static void appendRow(StringBuilder builder, JsonNode node) throws SerdeException {
        if (node.isArray()) {
            var index = 0;
            for (JsonNode value : node.values()) {
                appendSeparator(builder, index++);
                appendCell(builder, cell(value));
            }
        } else if (node.isObject()) {
            var index = 0;
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                appendSeparator(builder, index++);
                appendCell(builder, cell(entry.getValue()));
            }
        } else {
            appendCell(builder, cell(node));
        }
        builder.append('\n');
    }

    private static void appendRow(StringBuilder builder, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            appendSeparator(builder, i);
            appendCell(builder, row.get(i));
        }
        builder.append('\n');
    }

    private static void appendObjectRow(StringBuilder builder, JsonNode node, List<String> headers) throws SerdeException {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            appendSeparator(builder, i);
            JsonNode cell = node.get(header);
            appendCell(builder, cell == null ? "" : cell(cell));
        }
        builder.append('\n');
    }

    private static void appendSchemaRow(StringBuilder builder, JsonNode node, List<String> headers) throws SerdeException {
        if (node.isObject()) {
            appendObjectRow(builder, node, headers);
        } else {
            appendRow(builder, node);
        }
    }

    private static void appendSeparator(StringBuilder builder, int index) {
        if (index > 0) {
            builder.append(',');
        }
    }

    // Applying Csv escaping i.e:  a "quoted" value -> "a ""quoted"" value"
    private static void appendCell(StringBuilder builder, String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            builder.append(value);
            return;
        }
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                builder.append('"');
            }
            builder.append(c);
        }
        builder.append('"');
    }

    // Builds the header list for an array of object rows.
    private static List<String> headersFromRows(JsonNode rows) {
        var headers = new LinkedHashSet<String>();
        for (JsonNode row : rows.values()) {
            if (row.isObject()) {
                for (Map.Entry<String, JsonNode> entry : row.entries()) {
                    headers.add(entry.getKey());
                }
            }
        }
        return List.copyOf(headers);
    }

    // Builds headers from a single object.
    private static List<String> headersFromRow(JsonNode row) {
        var headers = new ArrayList<String>();
        for (Map.Entry<String, JsonNode> entry : row.entries()) {
            headers.add(entry.getKey());
        }
        return headers;
    }

    private static String cell(JsonNode node) throws SerdeException {
        if (node.isNull()) {
            return "";
        }
        if (node.isObject()) {
            throw new SerdeException("CSV does not support object values for properties (nested objects)");
        }
        if (node.isArray()) {
            var builder = new StringBuilder();
            var index = 0;
            for (JsonNode value : node.values()) {
                appendCellSeparator(builder, index++);
                builder.append(cell(value));
            }
            return builder.toString();
        }
        return node.coerceStringValue();
    }

    private static void appendCellSeparator(StringBuilder builder, int index) {
        if (index > 0) {
            builder.append(';');
        }
    }

}
