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
 * Exception thrown when CSV data cannot be mapped to the target type.
 * @since 3.2.5
 */
public final class CsvMappingException extends CsvException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Class<?> targetType;

    /**
     * Creates a new mapping exception.
     *
     * @param message    The detail message
     * @param targetType The type the CSV data failed to map to
     */
    public CsvMappingException(String message, Class<?> targetType) {
        this(message, targetType, null);
    }

    /**
     * Creates a new mapping exception.
     *
     * @param message    The detail message
     * @param targetType The type the CSV data failed to map to
     * @param cause      The cause, or {@code null}
     */
    public CsvMappingException(String message, Class<?> targetType, @Nullable Throwable cause) {
        super(message + " (target type: " + targetType.getName() + ")", cause);
        this.targetType = targetType;
    }

    /**
     * Returns the target type that the CSV data failed to map to.
     *
     * @return the target type
     */
    public Class<?> getTargetType() {
        return targetType;
    }
}
