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
package io.micronaut.serde.jsonp;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

import java.util.Objects;

import static io.micronaut.serde.jsonp.PatchBuilder.OP_COPY;
import static io.micronaut.serde.jsonp.PatchBuilder.OP_MOVE;
import static io.micronaut.serde.jsonp.JsonpValueSupport.Pointer;

/**
 * Immutable JSON Patch implementation that applies operations through {@link Pointer}.
 *
 * @param operations The immutable operation array
 */
@Internal
record Patch(JsonArray operations) implements JsonPatch {

    static final String ADD = "add";
    static final String REMOVE = "remove";
    static final String REPLACE = "replace";
    static final String TEST = "test";

    /**
     * Applies each patch operation to an immutable rebuilt structure.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends JsonStructure> T apply(T target) {
        JsonStructure current = target;
        for (JsonValue item : operations) {
            JsonObject operation = item.asJsonObject();
            String op = operation.getString("op");
            String path = operation.getString("path");
            Pointer pointer = new Pointer(path);
            current = switch (op) {
                case ADD -> pointer.add(current, required(operation));
                case REMOVE -> pointer.remove(current);
                case REPLACE -> pointer.replace(current, required(operation));
                case OP_COPY -> pointer.add(current, new Pointer(operation.getString("from")).getValue(current));
                case OP_MOVE -> {
                    Pointer from = new Pointer(operation.getString("from"));
                    JsonValue value = from.getValue(current);
                    current = from.remove(current);
                    yield pointer.add(current, value);
                }
                case TEST -> {
                    if (!Objects.equals(pointer.getValue(current), operation.get(AnnotationMetadata.VALUE_MEMBER))) {
                        throw new JsonException("JSON patch test operation failed for path " + path);
                    }
                    yield current;
                }
                default -> throw new JsonException("Unsupported JSON patch operation: " + op);
            };
        }
        return (T) current;
    }

    /**
     * Reads the required operation value member and reports malformed operations consistently.
     */
    private static JsonValue required(JsonObject object) {
        JsonValue value = object.get(AnnotationMetadata.VALUE_MEMBER);
        if (value == null) {
            throw new JsonException("JSON patch operation is missing '" + AnnotationMetadata.VALUE_MEMBER + "'");
        }
        return value;
    }

    /**
     * Returns the immutable operation array backing this patch.
     */
    @Override
    public JsonArray toJsonArray() {
        return operations;
    }
}
