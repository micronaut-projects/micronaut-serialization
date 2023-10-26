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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LookaheadDecoder;

import java.io.IOException;
import java.util.Map;

/**
 * Implementation of the {@link Decoder} interface that
 * uses the {@link JsonNode} abstraction.
 */
@Internal
public non-sealed class LookaheadObjectDecoder extends JsonNodeDecoder implements LookaheadDecoder {

    private final Map<String, JsonNode> visitedNodes = CollectionUtils.newLinkedHashMap(20);
    private String currentKey;
    private boolean finished;
    private final Decoder decoder;
    private JsonNode peekedNode;

    public LookaheadObjectDecoder(RemainingLimits remainingLimits, Decoder decoder) {
        super(remainingLimits);
        this.decoder = decoder;
    }

    @Override
    public Decoder replay() throws IOException {
        return new ReplayObjectDecoder(
            ourLimits(),
            visitedNodes.entrySet().iterator(),
            finished,
            decoder
        );
    }

    @Override
    public boolean hasNextArrayValue() {
        return false;
    }

    @Override
    public String decodeKey() throws IOException {
        if (currentKey != null && peekedNode != null) {
            throw new IllegalStateException("Value not read");
        }
        if (finished) {
            return null;
        }
        currentKey = decoder.decodeKey();
        if (currentKey == null) {
            finished = true;
        } else {
            peekedNode = decoder.decodeNode();
            visitedNodes.put(currentKey, peekedNode);
        }
        return currentKey;
    }

    @Override
    public void skipValue() throws IOException {
        if (currentKey == null || peekedNode == null) {
            throw new IllegalStateException("Current key not read or null");
        }
        currentKey = null;
        peekedNode = null;
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        String key = decodeKey();
        while (key != null) {
            skipValue();
            key = decodeKey();
        }
        decoder.finishStructure();
    }

    @Override
    protected JsonNode peekValue() {
        if (peekedNode == null) {
            throw new IllegalStateException("Current key not read or null");
        }
        return peekedNode;
    }

}
