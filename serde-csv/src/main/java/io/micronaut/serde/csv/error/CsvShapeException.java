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
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Locale;

/**
 * Exception thrown when CSV record shape doesn't match the expected shape.
 * @since 3.2.5
 */
public final class CsvShapeException extends CsvException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final CsvFormat.Shape expectedShape;
    private final int actualRecordCount;

    /**
     * Creates a new shape exception with a generated message.
     *
     * @param expectedShape     The expected shape
     * @param actualRecordCount The actual number of records
     */
    public CsvShapeException(CsvFormat.Shape expectedShape, int actualRecordCount) {
        this(buildMessage(expectedShape, actualRecordCount), expectedShape, actualRecordCount, null);
    }

    /**
     * Creates a new shape exception.
     *
     * @param message           The detail message
     * @param expectedShape     The expected shape
     * @param actualRecordCount The actual number of records
     * @param cause             The cause, or {@code null}
     */
    public CsvShapeException(String message, CsvFormat.Shape expectedShape, int actualRecordCount, @Nullable Throwable cause) {
        super(message, cause);
        this.expectedShape = expectedShape;
        this.actualRecordCount = actualRecordCount;
    }

    /**
     * Returns the expected CSV shape.
     *
     * @return the expected shape
     */
    public CsvFormat.Shape getExpectedShape() {
        return expectedShape;
    }

    /**
     * Returns the actual number of records found.
     *
     * @return the actual record count
     */
    public int getActualRecordCount() {
        return actualRecordCount;
    }

    private static String buildMessage(CsvFormat.Shape expectedShape, int actualRecordCount) {
        return "CSV shape mismatch: expected " + expectedShape.name().toLowerCase(Locale.ROOT) + " record(s), got " + actualRecordCount;
    }
}
