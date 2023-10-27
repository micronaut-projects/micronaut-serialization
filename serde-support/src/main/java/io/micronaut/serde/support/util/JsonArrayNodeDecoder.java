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

final class JsonArrayNodeDecoder extends JsonNodeDecoder {

    private final Iterator<JsonNode> iterator;
    private JsonNode peeked;

    JsonArrayNodeDecoder(JsonNode node, RemainingLimits remainingLimits) {
        super(remainingLimits);
        iterator = node.values().iterator();
        skipValue();
    }

    @Override
    public boolean hasNextArrayValue() {
        return peeked != null;
    }

    @Override
    public String decodeKey() {
        throw new IllegalStateException("Arrays have no keys");
    }

    @Override
    public void skipValue() {
        if (iterator.hasNext()) {
            peeked = iterator.next();
        } else {
            peeked = null;
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) {
        if (!consumeLeftElements && peeked != null) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
    }

    @Override
    protected JsonNode peekValue() {
        if (peeked == null) {
            throw new IllegalStateException("No more elements");
        }
        return peeked;
    }
}
