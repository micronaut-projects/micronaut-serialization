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
package io.micronaut.serde.patch;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * A malformed or unsuccessful JSON Patch operation.
 * @since 3.2.0
 */
@Experimental
public final class JsonPatchException extends SerdeException {
    private final int operationIndex;
    private final String operation;
    private final String pointer;

    /**
     * Creates an operation failure.
     * @param operationIndex Zero-based index, or -1 for a document-level failure
     * @param operation Operation name, or empty when unavailable
     * @param pointer Failing pointer, or empty for the root
     * @param message Failure description
     * @since 3.2.0
     */
    public JsonPatchException(int operationIndex, String operation, String pointer, String message) {
        super("JSON Patch operation " + operationIndex + " (" + operation + ", " + pointer + "): " + message);
        this.operationIndex = operationIndex;
        this.operation = operation;
        this.pointer = pointer;
    }

    /**
     * Returns zero-based operation index, or -1 for a document-level failure.
     * @return Zero-based operation index, or -1 for a document-level failure
     * @since 3.2.0
     */
    public int getOperationIndex() {
        return operationIndex;
    }

    /**
     * Returns operation name.
     * @return Operation name
     * @since 3.2.0
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns failing JSON pointer.
     * @return Failing JSON pointer
     * @since 3.2.0
     */
    public String getPointer() {
        return pointer;
    }
}
