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
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonValue;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

import static io.micronaut.serde.jsonp.JsonpValueSupport.ArrayBuilder;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonNumberValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonStringValue;
import static io.micronaut.serde.jsonp.JsonpValueSupport.ObjectBuilder;

/**
 * Mutable JSON Patch operation builder backed by this provider's JSON-P value model.
 */
@Internal
final class PatchBuilder implements JsonPatchBuilder {
    public static final String OP_MOVE = "move";
    public static final String OP_COPY = "copy";
    public static final String OP_TEST = "test";
    public static final String FROM = "from";
    public static final String OP = "op";
    public static final String PATH = "path";
    public static final String OP_ADD = "add";
    public static final String OP_REMOVE = "remove";
    public static final String OP_REPLACE = "replace";
    private final ArrayBuilder operations = new ArrayBuilder();

    /**
     * Creates an empty patch builder that records operations in insertion order.
     */
    PatchBuilder() {
    }

    /**
     * Creates a patch builder initialized from an existing operation array.
     *
     * @param array The operation array to copy
     */
    PatchBuilder(JsonArray array) {
        array.forEach(operations::add);
    }

    /**
     * Adds an add operation using the supplied JSON-P value without further conversion.
     */
    @Override
    public JsonPatchBuilder add(String path, JsonValue value) {
        return operation(OP_ADD, path, null, value);
    }

    /**
     * Adds an add operation after normalizing the string to this provider's JsonString implementation.
     */
    @Override
    public JsonPatchBuilder add(String path, String value) {
        return add(path, new JsonStringValue(value));
    }

    /**
     * Adds an add operation after normalizing the int to this provider's BigDecimal-backed JsonNumber.
     */
    @Override
    public JsonPatchBuilder add(String path, int value) {
        return add(path, new JsonNumberValue(BigDecimal.valueOf(value)));
    }

    /**
     * Adds an add operation after normalizing the boolean to the JSON-P singleton value.
     */
    @Override
    public JsonPatchBuilder add(String path, boolean value) {
        return add(path, value ? JsonValue.TRUE : JsonValue.FALSE);
    }

    /**
     * Adds a remove operation for the supplied JSON Pointer path.
     */
    @Override
    public JsonPatchBuilder remove(String path) {
        return operation(OP_REMOVE, path, null, null);
    }

    /**
     * Adds a replace operation using the supplied JSON-P value without further conversion.
     */
    @Override
    public JsonPatchBuilder replace(String path, JsonValue value) {
        return operation(OP_REPLACE, path, null, value);
    }

    /**
     * Adds a replace operation after normalizing the string to this provider's JsonString implementation.
     */
    @Override
    public JsonPatchBuilder replace(String path, String value) {
        return replace(path, new JsonStringValue(value));
    }

    /**
     * Adds a replace operation after normalizing the int to this provider's BigDecimal-backed JsonNumber.
     */
    @Override
    public JsonPatchBuilder replace(String path, int value) {
        return replace(path, new JsonNumberValue(BigDecimal.valueOf(value)));
    }

    /**
     * Adds a replace operation after normalizing the boolean to the JSON-P singleton value.
     */
    @Override
    public JsonPatchBuilder replace(String path, boolean value) {
        return replace(path, value ? JsonValue.TRUE : JsonValue.FALSE);
    }

    /**
     * Adds a move operation that carries both target and source JSON Pointer paths.
     */
    @Override
    public JsonPatchBuilder move(String path, String from) {
        return operation(OP_MOVE, path, from, null);
    }

    /**
     * Adds a copy operation that carries both target and source JSON Pointer paths.
     */
    @Override
    public JsonPatchBuilder copy(String path, String from) {
        return operation(OP_COPY, path, from, null);
    }

    /**
     * Adds a test operation using the supplied JSON-P value without further conversion.
     */
    @Override
    public JsonPatchBuilder test(String path, JsonValue value) {
        return operation(OP_TEST, path, null, value);
    }

    /**
     * Adds a test operation after normalizing the string to this provider's JsonString implementation.
     */
    @Override
    public JsonPatchBuilder test(String path, String value) {
        return test(path, new JsonStringValue(value));
    }

    /**
     * Adds a test operation after normalizing the int to this provider's BigDecimal-backed JsonNumber.
     */
    @Override
    public JsonPatchBuilder test(String path, int value) {
        return test(path, new JsonNumberValue(BigDecimal.valueOf(value)));
    }

    /**
     * Adds a test operation after normalizing the boolean to the JSON-P singleton value.
     */
    @Override
    public JsonPatchBuilder test(String path, boolean value) {
        return test(path, value ? JsonValue.TRUE : JsonValue.FALSE);
    }

    /**
     * Builds an immutable JSON Patch from the accumulated operation array.
     */
    @Override
    public JsonPatch build() {
        return new Patch(operations.build());
    }

    /**
     * Appends one normalized operation object to the builder state.
     */
    private JsonPatchBuilder operation(String op, String path, @Nullable String from, @Nullable JsonValue value) {
        ObjectBuilder builder = new ObjectBuilder();
        builder.add(OP, op).add(PATH, path);
        if (from != null) {
            builder.add(FROM, from);
        }
        if (value != null) {
            builder.add(AnnotationMetadata.VALUE_MEMBER, value);
        }
        operations.add(builder);
        return this;
    }
}
