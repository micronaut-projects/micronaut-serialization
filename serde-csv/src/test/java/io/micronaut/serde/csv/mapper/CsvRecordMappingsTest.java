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
package io.micronaut.serde.csv.mapper;

import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CsvRecordMappingsTest {

    @Test
    void identityMappingMutatesInPlace() {
        CsvFormatConfig config = config(List.of("id", "name"), List.of("id", "name"));
        Map<String, @Nullable String> values = new LinkedHashMap<>();
        values.put("id", "1");
        values.put("name", "");

        Map<String, @Nullable String> result = CsvRecordMappings.toPropertyMap(config, values, CsvRecordMappings.mapping(config));

        assertSame(values, result);
        assertEquals("1", result.get("id"));
        assertNull(result.get("name"));
    }

    @Test
    void partialMappingRenamesOnlyMappedColumns() {
        CsvFormatConfig config = config(List.of("first_name", "age"), List.of("firstName", "age"));
        Map<String, @Nullable String> values = new LinkedHashMap<>();
        values.put("first_name", "Ada");
        values.put("age", "36");

        Map<String, @Nullable String> result = CsvRecordMappings.toPropertyMap(config, values, CsvRecordMappings.mapping(config));

        assertSame(values, result);
        assertEquals("Ada", result.get("firstName"));
        assertEquals("36", result.get("age"));
        assertFalse(result.containsKey("first_name"));
    }

    @Test
    void fallsBackWhenTargetCollidesWithColumn() {
        // Column "x" maps to property "a", while column "a" maps to property "b".
        CsvFormatConfig config = config(List.of("x", "a"), List.of("a", "b"));
        Map<String, @Nullable String> values = new LinkedHashMap<>();
        values.put("x", "x-value");
        values.put("a", "a-value");

        Map<String, @Nullable String> result = CsvRecordMappings.toPropertyMap(config, values, CsvRecordMappings.mapping(config));

        assertNotSame(values, result);
        assertEquals("x-value", result.get("a"));
        assertEquals("a-value", result.get("b"));
    }

    @Test
    void doesNotTreatRenamedKeysAsSources() {
        // Column "x" maps to property "a", while column "a" maps to property "b". The "a"
        // column is absent, so the value moved from "x" must not be renamed again to "b".
        CsvFormatConfig config = config(List.of("x", "a"), List.of("a", "b"));
        Map<String, @Nullable String> values = new LinkedHashMap<>();
        values.put("x", "x-value");

        Map<String, @Nullable String> result = CsvRecordMappings.toPropertyMap(config, values, CsvRecordMappings.mapping(config));

        assertNotSame(values, result);
        assertEquals("x-value", result.get("a"));
        assertFalse(result.containsKey("b"));
    }

    @Test
    void mappingWithoutColumnsNormalizesIntoNewMap() {
        Map<String, @Nullable String> values = new LinkedHashMap<>();
        values.put("a", "");

        Map<String, @Nullable String> result = CsvRecordMappings.toPropertyMap(
            CsvFormatConfig.DEFAULT,
            values,
            CsvRecordMappings.mapping(CsvFormatConfig.DEFAULT)
        );

        assertNotSame(values, result);
        assertNull(result.get("a"));
        assertEquals("", values.get("a"));
    }

    private static CsvFormatConfig config(List<String> columns, List<String> propertyNames) {
        return CsvFormatConfig.DEFAULT.withColumns(false, columns, propertyNames);
    }
}
