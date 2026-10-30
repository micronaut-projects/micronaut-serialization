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
package io.micronaut.serde.csv.error;

import io.micronaut.serde.csv.annotation.CsvFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExceptionTest {

    @Test
    void parseExceptionIncludesLineAndColumn() {
        CsvParseException exception = new CsvParseException("Broken row", 3, 7);

        assertEquals("Broken row at line 3, column 7", exception.getMessage());
        assertEquals(3, exception.getLineNumber());
        assertEquals(7, exception.getColumnNumber());
    }

    @Test
    void parseExceptionOmitsUnknownPosition() {
        CsvParseException exception = new CsvParseException("Broken row");

        assertEquals("Broken row", exception.getMessage());
        assertEquals(-1, exception.getLineNumber());
        assertEquals(-1, exception.getColumnNumber());
    }

    @Test
    void mappingExceptionIncludesTargetType() {
        RuntimeException cause = new RuntimeException("bad value");
        CsvMappingException exception = new CsvMappingException("Cannot map", String.class, cause);

        assertEquals("Cannot map (target type: java.lang.String)", exception.getMessage());
        assertEquals(String.class, exception.getTargetType());
        assertSame(cause, exception.getCause());
    }

    @Test
    void shapeExceptionDescribesMismatch() {
        CsvShapeException exception = new CsvShapeException(CsvFormat.Shape.SINGLE, 2);

        assertTrue(exception.getMessage().contains("expected single"));
        assertTrue(exception.getMessage().contains("got 2"));
        assertEquals(CsvFormat.Shape.SINGLE, exception.getExpectedShape());
        assertEquals(2, exception.getActualRecordCount());
    }
}
