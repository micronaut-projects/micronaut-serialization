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

import io.micronaut.serde.csv.apache.commons.mapper.ApacheCommonsCsvMapper;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ApacheCommonsCsvMapperTest {

    @Inject
    ApacheCommonsCsvMapper mapper;

    @Test
    void testMapRecordsToPojoWithDefaultMapper() throws CsvException {
        var people = mapper.readAll("name,age\nAlice,30\nBob,25", Person.class).iterator();
        Person first = people.next();
        assertEquals("Alice", first.name());
        assertEquals(30, first.age());
        assertTrue(people.hasNext());
        assertEquals("Bob", people.next().name());
        assertFalse(people.hasNext());
    }

    @Test
    void testWriteMapsToCsvStringWithDefaultMapper() throws CsvException {
        Map<String, String> alice = new LinkedHashMap<>();
        alice.put("name", "Alice");
        alice.put("age", "30");
        Map<String, String> bob = new LinkedHashMap<>();
        bob.put("name", "Bob");
        bob.put("age", "25");
        var values = List.of(alice, bob);
        String csv = mapper.writeToString(values);
        assertTrue(csv.contains("Alice,30"));
        assertTrue(csv.contains("Bob,25"));
    }

    @Test
    void testReadFromInputStreamWithDefaultMapper() throws CsvException {
        var input = new ByteArrayInputStream("name,age\nAlice,30\nBob,25".getBytes(StandardCharsets.UTF_8));
        var people = mapper.read(input, "UTF-8", Person.class).iterator();
        Person first = people.next();
        assertEquals("Alice", first.name());
        assertEquals(30, first.age());
        assertTrue(people.hasNext());
    }
}
