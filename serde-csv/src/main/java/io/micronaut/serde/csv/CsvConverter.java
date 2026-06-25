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
import jakarta.inject.Singleton;
import jakarta.json.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Converts comma-separated rows to {@code List<List<String>>} arguments.
 * Jackson dataformat csv behaviour returns List<List<String>> for non defined schema table
 *
 * @see <a href="https://github.com/FasterXML/jackson-dataformats-text/blob/3.x/csv/src/test/java/tools/jackson/dataformat/csv/deser/BlogPost2021AprilTest.java">Csv without schema</a>
 * @see <a href="https://github.com/FasterXML/jackson-dataformats-text/blob/3.x/csv/src/test/java/tools/jackson/dataformat/csv/deser/BlogPost2021AprilTest.java">Jackson Test behavior</a>
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

    /*
    * return line-delimited Json (Jsonl) where the keys are integers counted from zero.
    *  i.e :
    *    [
    *      {"0": "1", "1": "2", "2": "true"},
    *      {"0": "2", "1": "9", "2": "false"}
    *    ]
    *
    * */
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
            .map(cells-> {
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

    /*
     * return List<List<String>>, so csv values stays as list of cell values
     *  i.e :
     *    [
     *      {"0": "1", "1": "2", "2": "true"},
     *      {"0": "2", "1": "9", "2": "false"}
     *    ]
     *
     * */
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

}
