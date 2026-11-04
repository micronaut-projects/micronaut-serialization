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

import io.micronaut.serde.csv.apache.commons.decoder.DefaultCsvDecoder;
import io.micronaut.serde.csv.apache.commons.encoder.DefaultCsvEncoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvFormatConfigTest {

    private static final CsvFormatConfig CONFIG = CsvIntrospection.formatFor(CsvSemiColon.class);

    @Test
    void encodesUsingConfiguredDelimiterAndHeader() throws CsvException {
        DefaultCsvEncoder encoder = new DefaultCsvEncoder();
        String csv = encoder.encodeToString(List.of(Map.of("id", "1", "name", "Alice")), CONFIG);
        assertEquals("id;name\n1;Alice\n", csv);
    }

    @Test
    void decodesUsingConfiguredDelimiter() {
        DefaultCsvDecoder decoder = new DefaultCsvDecoder();
        Map<String, String> first = decoder.decode("id;name\n1;Alice\n", CONFIG).iterator().next();
        assertEquals("1", first.get("id"));
        assertEquals("Alice", first.get("name"));
    }
}
