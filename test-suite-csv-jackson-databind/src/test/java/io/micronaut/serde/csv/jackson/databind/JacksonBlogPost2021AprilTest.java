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
package io.micronaut.serde.csv.jackson.databind;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonBlogPost2021AprilTest {

    private static final String SIMPLE_CSV = "1,2,true\n"
        + "2,9,false\n"
        + "-13,0,true\n";

    private static final String HEADER_CSV = "x, y, visible\n" + SIMPLE_CSV;

    private final CsvMapper mapper = new CsvMapper();

    @Test
    void testAsListOfLists() throws Exception {
        List<List<String>> all = mapper
            .readerFor(new TypeReference<List<List<String>>>() {
            })
            .with(CsvReadFeature.WRAP_AS_ARRAY)
            .readValue(SIMPLE_CSV);

        assertListOfLists(all);
    }

    @Test
    void testAsSequenceOfListsOfStrings() throws Exception {
        MappingIterator<List<String>> iterator = mapper
            .readerForListOf(String.class)
            .with(CsvReadFeature.WRAP_AS_ARRAY)
            .readValues(SIMPLE_CSV);

        List<List<String>> all = iterator.readAll();

        assertListOfLists(all);
    }

    @Test
    void testAsSequenceOfMaps() throws Exception {
        CsvSchema schema = CsvSchema.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("visible")
            .build();

        try (MappingIterator<Map<String, String>> iterator = mapper
            .readerForMapOf(String.class)
            .with(schema)
            .readValues(SIMPLE_CSV)) {
            assertTrue(iterator.hasNextValue());
            Map<String, String> map = iterator.nextValue();
            assertEquals(3, map.size());
            assertEquals("1", map.get("x"));
            assertEquals("2", map.get("y"));
            assertEquals("true", map.get("visible"));

            assertTrue(iterator.hasNextValue());
            map = iterator.nextValue();
            assertEquals(3, map.size());
            assertEquals("2", map.get("x"));
            assertEquals("9", map.get("y"));
            assertEquals("false", map.get("visible"));

            assertTrue(iterator.hasNextValue());
            map = iterator.nextValue();
            assertEquals(3, map.size());
            assertEquals("-13", map.get("x"));
            assertEquals("0", map.get("y"));
            assertEquals("true", map.get("visible"));

            assertFalse(iterator.hasNextValue());
        }
    }

    @Test
    void testAsSequenceOfPojosWithHeader() throws Exception {
        CsvSchema schemaWithHeader = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<Point> iterator = mapper
            .readerFor(Point.class)
            .with(schemaWithHeader)
            .readValues(HEADER_CSV)) {
            assertTrue(iterator.hasNextValue());
            Point point = iterator.nextValue();
            assertEquals(1, point.x);
            assertEquals(2, point.y);
            assertTrue(point.visible);

            assertTrue(iterator.hasNextValue());
            point = iterator.nextValue();
            assertEquals(2, point.x);
            assertEquals(9, point.y);
            assertFalse(point.visible);

            assertTrue(iterator.hasNextValue());
            point = iterator.nextValue();
            assertEquals(-13, point.x);
            assertEquals(0, point.y);
            assertTrue(point.visible);

            assertFalse(iterator.hasNextValue());
        }
    }

    private static void assertListOfLists(List<List<String>> all) {
        assertEquals(3, all.size());
        assertEquals(Arrays.asList("1", "2", "true"), all.get(0));
        assertEquals(Arrays.asList("2", "9", "false"), all.get(1));
        assertEquals(Arrays.asList("-13", "0", "true"), all.get(2));
    }

    @JsonPropertyOrder({ "x", "y", "visible" })
    static class Point {
        public int x;
        public int y;
        public boolean visible;
    }
}
