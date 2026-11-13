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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the Apache Commons CSV backend honors the {@link java.nio.charset.Charset}
 * passed to the encoding and decoding entry points instead of always using UTF-8.
 */
@MicronautTest
class CsvCharsetTest {

    @Inject
    ApacheCommonsCsvObjectMapper csvObjectMapper;

    @Test
    void roundTripPreservesCharactersEncodedWithIso88591() throws Exception {
        Person person = new Person("Zoé", 42);

        // Write with a header row so it can be read back as a typed value
        String csv = csvObjectMapper.writeToString(List.of(person), List.of("name", "age"));
        byte[] bytes = csv.getBytes(StandardCharsets.ISO_8859_1);

        // 'é' is a single byte 0xE9 in ISO-8859-1 (two bytes 0xC3 0xA9 in UTF-8)
        assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("Zoé"));
        // Confirm the bytes are genuinely ISO-8859-1 encoded, not UTF-8
        assertNotEquals("Zoé", new String(bytes, StandardCharsets.UTF_8));

        Iterable<Person> read = csvObjectMapper.read(new ByteArrayInputStream(bytes),
            StandardCharsets.ISO_8859_1, Person.class);
        assertEquals(new Person("Zoé", 42), read.iterator().next());
    }

    @Test
    void writeValuesHonorsCharset() throws Exception {
        Person person = new Person("Zoé", 42);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        csvObjectMapper.writeValues(out, List.of(person), StandardCharsets.ISO_8859_1);
        byte[] bytes = out.toByteArray();

        assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("Zoé"));
        assertNotEquals("Zoé", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void readHonorsCharset() throws Exception {
        // 'é' encoded as a single 0xE9 byte in ISO-8859-1
        byte[] bytes = "name,age\r\nZoé,42\r\n".getBytes(StandardCharsets.ISO_8859_1);

        Iterable<Person> read = csvObjectMapper.read(new ByteArrayInputStream(bytes),
            StandardCharsets.ISO_8859_1, Person.class);
        assertEquals(new Person("Zoé", 42), read.iterator().next());
    }
}
