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
package io.micronaut.serde.csv.introspection;

import io.micronaut.serde.csv.CsvExplicitColumns;
import io.micronaut.serde.csv.CsvFormatted;
import io.micronaut.serde.csv.CsvOrdered;
import io.micronaut.serde.csv.Person;
import io.micronaut.serde.csv.annotation.CsvFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvIntrospectionTest {

    @Test
    void resolvesFormatAnnotation() {
        CsvFormatConfig config = CsvIntrospection.formatFor(CsvFormatted.class);
        assertTrue(config.configured());
        assertFalse(config.explicitColumns());
        assertTrue(config.hasHeader());
        assertTrue(config.writeHeader());
        assertEquals(';', config.delimiter());
        assertEquals(CsvFormat.Header.PRESENT, config.header());
        assertEquals(CsvFormat.Shape.FIRST, config.shape());
        assertEquals(List.of("id", "name"), config.columns());
    }

    @Test
    void resolvesExplicitColumns() {
        CsvFormatConfig config = CsvIntrospection.formatFor(CsvExplicitColumns.class);
        assertTrue(config.configured());
        assertTrue(config.explicitColumns());
        assertTrue(config.writeHeader());
        assertEquals(List.of("x", "y"), config.columns());
    }

    @Test
    void resolvesPropertyOrderNameAndIgnore() {
        assertEquals(List.of("a_renamed", "b", "c"), CsvIntrospection.columnsFor(CsvOrdered.class));
    }

    @Test
    void unannotatedTypeUsesPropertyNames() {
        CsvFormatConfig config = CsvIntrospection.formatFor(Person.class);
        assertFalse(config.configured());
        assertEquals(',', config.delimiter());
        assertEquals(List.of("name", "age"), config.columns());
    }

    @Test
    void unknownTypeResolvesToDefault() {
        assertFalse(CsvIntrospection.formatFor(String.class).configured());
        assertEquals(List.of(), CsvIntrospection.columnsFor(String.class));
    }
}
