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
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

final class JsonObjectNodeDecoder extends JsonNodeDecoder implements KeysAwareDecoder {
    private final Iterator<Map.Entry<String, JsonNode>> iterator;
    @Nullable
    private String nextKey = null;
    @Nullable
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
        if (nextValue == null || nextKey != null) {
            throw new IllegalStateException("Field name not parsed yet");
        }
        nextValue = null;
    }

    @Override
    public boolean hasNextArrayValue() {
        return false;
    }

    @Override
    public @Nullable String decodeKey() {
        if (nextKey != null) {
            String key = nextKey;
            nextKey = null;
            return key;
        }
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
    public int decodeKey(Keys keys) {
        if (nextKey != null) {
            return KeysAwareDecoder.MATCH_UNKNOWN_NAME;
        }
        if (nextValue != null) {
            throw new IllegalStateException("Field value not parsed yet");
        }
        if (!iterator.hasNext()) {
            return KeysAwareDecoder.MATCH_END_OBJECT;
        }
        Map.Entry<String, JsonNode> next = iterator.next();
        String key = next.getKey();
        int index = keys.indexOf(key);
        if (index == -1) {
            // MATCH_UNKNOWN_NAME means the current key did not match the supplied Keys.
            // Store it so the next decodeKey() call returns the same unknown name.
            nextKey = key;
            nextValue = next.getValue();
            return KeysAwareDecoder.MATCH_UNKNOWN_NAME;
        }
        nextValue = next.getValue();
        return index;
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) {
        if (!consumeLeftElements && (nextKey != null || nextValue != null || iterator.hasNext())) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
    }
}
