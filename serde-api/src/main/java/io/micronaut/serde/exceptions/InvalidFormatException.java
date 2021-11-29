/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.exceptions;

import java.util.Optional;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionError;

/**
 * Error for when a value cannot be converted to the desired type.
 *
 * @since 1.0.0
 */
public class InvalidFormatException extends SerdeException {
    private final Object originalValue;

    public InvalidFormatException(
            String message,
            @Nullable Exception cause,
            @Nullable Object originalValue) {
        super(message, cause);
        this.originalValue = originalValue;
    }

    @Override
    public synchronized Exception getCause() {
        return (Exception) super.getCause();
    }

    /**
     * @return The original value
     */
    public @Nullable Object getOriginalValue() {
        return originalValue;
    }

    /**
     * Converts the exception to a conversion error.
     * @return The conversion error.
     */
    public @NonNull ConversionError toConversionError() {
        return new ConversionError() {
            @Override
            public Exception getCause() {
                final Exception cause = InvalidFormatException.this.getCause();
                if (cause != null) {
                    return cause;
                } else {
                    return InvalidFormatException.this;
                }
            }

            @Override
            public Optional<Object> getOriginalValue() {
                return Optional.ofNullable(originalValue);
            }
        };
    }
}
