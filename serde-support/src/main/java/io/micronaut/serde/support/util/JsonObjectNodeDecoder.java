/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support.util;

import io.micronaut.json.tree.JsonNode;

import java.util.Iterator;
import java.util.Map;

final class JsonObjectNodeDecoder extends JsonNodeDecoder {
    private final Iterator<Map.Entry<String, JsonNode>> iterator;
    private JsonNode nextValue = null;

    JsonObjectNodeDecoder(JsonNode node, RemainingLimits remainingLimits) {
        super(remainingLimits);
        iterator = node.entries().iterator();
    }

    @Override
    protected JsonNode peekValue() {
        if (nextValue == null) {
            throw new IllegalStateException("Field name not parsed yet");
        }
        return nextValue;
    }

    @Override
    public void skipValue() {
        if (nextValue == null) {
            throw new IllegalStateException("Field name not parsed yet");
        }
        nextValue = null;
    }

    @Override
    public boolean hasNextArrayValue() {
        return false;
    }

    @Override
    public String decodeKey() {
        if (nextValue != null) {
            throw new IllegalStateException("Field value not parsed yet");
        }
        if (iterator.hasNext()) {
            Map.Entry<String, JsonNode> next = iterator.next();
            nextValue = next.getValue();
            return next.getKey();
        } else {
            return null;
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) {
        if (!consumeLeftElements && (nextValue != null || iterator.hasNext())) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
    }
}
