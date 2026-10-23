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
package io.micronaut.serde.csv.jackson;

import io.micronaut.serde.csv.error.CsvException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvFormatConfigTest {

    @Test
    void encodesUsingConfiguredDelimiterAndHeader() throws CsvException {
        JacksonCsvMapper mapper = new JacksonCsvMapper();
        String csv = mapper.writeToString(List.of(new CsvSemiColon("a", "Alice")));
        assertEquals("id;name\na;Alice\n", csv);
    }

    @Test
    void decodesUsingConfiguredDelimiter() {
        JacksonCsvMapper mapper = new JacksonCsvMapper();
        CsvSemiColon first = mapper.readAll("id;name\na;Alice\n", CsvSemiColon.class).iterator().next();
        assertEquals(new CsvSemiColon("a", "Alice"), first);
    }
}
