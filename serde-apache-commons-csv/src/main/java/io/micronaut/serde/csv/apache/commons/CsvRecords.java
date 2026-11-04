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
package io.micronaut.serde.csv.apache.commons;

import io.micronaut.core.annotation.Internal;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Apache Commons CSV records to maps.
 *
 * @since 3.2.5
 */
@Internal
public final class CsvRecords {

    private static final String EMPTY_MAP_MESSAGE =
        "CSV data map is empty. It may be caused when no header passed at first line.";

    private CsvRecords() {
    }

    /**
     * Converts a CSV record to a map keyed by the given columns.
     *
     * @param record  The CSV record
     * @param columns The column names
     * @return The record map
     */
    public static Map<String, @Nullable String> toMap(CSVRecord record, List<String> columns) {
        Map<String, @Nullable String> map;
        if (columns.isEmpty()) {
            map = record.toMap();
        } else {
            List<String> values = record.toList();
            map = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                map.put(columns.get(i), i < values.size() ? values.get(i) : null);
            }
        }
        if (map.isEmpty()) {
            throw new IllegalStateException(EMPTY_MAP_MESSAGE);
        }
        return map;
    }
}
