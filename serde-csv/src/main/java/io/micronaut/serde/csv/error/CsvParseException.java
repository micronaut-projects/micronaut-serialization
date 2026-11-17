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

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * Exception thrown when CSV parsing fails due to malformed input.
 * @since 3.2.5
 */
public final class CsvParseException extends CsvException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int lineNumber;
    private final int columnNumber;

    /**
     * Creates a new parse exception with unknown line and column.
     *
     * @param message The detail message
     */
    public CsvParseException(String message) {
        this(message, -1, -1, null);
    }

    /**
     * Creates a new parse exception with unknown line and column.
     *
     * @param message The detail message
     * @param cause   The cause, or {@code null}
     */
    public CsvParseException(String message, @Nullable Throwable cause) {
        this(message, -1, -1, cause);
    }

    /**
     * Creates a new parse exception.
     *
     * @param message      The detail message
     * @param lineNumber   The line number, or -1 if unknown
     * @param columnNumber The column number, or -1 if unknown
     */
    public CsvParseException(String message, int lineNumber, int columnNumber) {
        this(message, lineNumber, columnNumber, null);
    }

    /**
     * Creates a new parse exception.
     *
     * @param message      The detail message
     * @param lineNumber   The line number, or -1 if unknown
     * @param columnNumber The column number, or -1 if unknown
     * @param cause        The cause, or {@code null}
     */
    public CsvParseException(String message, int lineNumber, int columnNumber, @Nullable Throwable cause) {
        super(buildMessage(message, lineNumber, columnNumber), cause);
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Returns the line number where the parse error occurred, or -1 if unknown.
     *
     * @return the line number
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the column number where the parse error occurred, or -1 if unknown.
     *
     * @return the column number
     */
    public int getColumnNumber() {
        return columnNumber;
    }

    private static String buildMessage(String message, int lineNumber, int columnNumber) {
        if (lineNumber >= 0 && columnNumber >= 0) {
            return message + " at line " + lineNumber + ", column " + columnNumber;
        } else if (lineNumber >= 0) {
            return message + " at line " + lineNumber;
        }
        return message;
    }
}
