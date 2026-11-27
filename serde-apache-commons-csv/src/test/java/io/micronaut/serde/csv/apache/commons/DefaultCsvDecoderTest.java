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
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultCsvDecoderTest {

    private static final CsvFormatConfig DEFAULT_CONFIG = CsvFormatConfig.DEFAULT;

    @Test
    void testDecodeSimpleCsvWithDefaultDecoder() {
        DefaultCsvDecoder decoder = new DefaultCsvDecoder();
        var records = decoder.decode("name,age\nAlice,30\nBob,25", DEFAULT_CONFIG);
        assertEquals(2, count(records));
    }

    @Test
    void testDecodeCsvWithCustomHeader() {
        DefaultCsvDecoder decoder = new DefaultCsvDecoder();
        var records = decoder.decode("30,Alice\n25,Bob", List.of("age", "name"));
        assertEquals(2, count(records));
    }

    @Test
    void testDecodeEmptyCsv() {
        DefaultCsvDecoder decoder = new DefaultCsvDecoder();
        var records = decoder.decode("", DEFAULT_CONFIG);
        assertEquals(0, count(records));
    }

    private static long count(Iterable<?> records) {
        return StreamSupport.stream(records.spliterator(), false).count();
    }
}
