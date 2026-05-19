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
package io.micronaut.serde.exceptions;

import io.micronaut.core.annotation.Internal;

/**
 * Signals that a scalar decoder encountered a serialized null value where a non-null value was expected.
 */
@Internal
public final class NullValueSerdeException extends SerdeException {

    private NullValueSerdeException(String message) {
        super(message);
    }

    /**
     * Creates a null-value sentinel exception without filling in a stack trace.
     *
     * @return The exception
     */
    public static NullValueSerdeException create() {
        return new NullValueSerdeException("Null value");
    }

    /**
     * Creates a null-value sentinel exception with the same message shape as an unexpected token exception.
     *
     * @param expected The expected token
     * @param actual The actual token
     * @return The exception
     */
    public static NullValueSerdeException unexpectedToken(Object expected, Object actual) {
        return new NullValueSerdeException("Unexpected token " + actual + ", expected " + expected);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
