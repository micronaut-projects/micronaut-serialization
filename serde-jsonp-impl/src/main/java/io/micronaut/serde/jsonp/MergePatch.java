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

import io.micronaut.core.annotation.Internal;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.micronaut.serde.jsonp.JsonpValueSupport.JsonObjectValue;

/**
 * Immutable JSON Merge Patch implementation using provider-owned JSON-P values.
 *
 * @param patch The immutable patch value
 */
@Internal
record MergePatch(JsonValue patch) implements JsonMergePatch {

    /**
     * Applies merge-patch object semantics and returns a rebuilt JSON-P value tree.
     */
    @Override
    public JsonValue apply(JsonValue target) {
        if (!(patch instanceof JsonObject patchObject)) {
            return patch;
        }
        Map<String, JsonValue> result = target instanceof JsonObject object ? new LinkedHashMap<>(object) : new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : patchObject.entrySet()) {
            if (entry.getValue() == JsonValue.NULL) {
                result.remove(entry.getKey());
            } else {
                result.compute(entry.getKey(), (_, old) -> applyMerge(old == null ? JsonValue.NULL : old, entry.getValue()));
            }
        }
        return new JsonObjectValue(result);
    }

    /**
     * Returns the immutable JSON-P value that represents this merge patch.
     */
    @Override
    public JsonValue toJsonValue() {
        return patch;
    }

    /**
     * Recursively applies nested merge-patch values.
     */
    private JsonValue applyMerge(JsonValue target, JsonValue patchValue) {
        if (patchValue instanceof JsonObject) {
            return new MergePatch(patchValue).apply(target);
        }
        return patchValue;
    }
}
