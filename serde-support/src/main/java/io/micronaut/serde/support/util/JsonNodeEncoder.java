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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.BinaryCodecUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link io.micronaut.serde.Encoder} interface that encodes a
 * in-memory {@link io.micronaut.json.tree.JsonNode}.
 */
public abstract class JsonNodeEncoder extends LimitingStream implements Encoder {
    private JsonNodeEncoder(RemainingLimits remainingLimits) {
        super(remainingLimits);
    }

    /**
     * Creates a new instance.
     *
     * @return The {@link JsonNodeEncoder}
     */
    @NonNull
    public static JsonNodeEncoder create() {
        return create(DEFAULT_LIMITS);
    }

    /**
     * Creates a new instance.
     *
     * @param limits The limits
     * @return The {@link JsonNodeEncoder}
     */
    @NonNull
    @Internal
    public static JsonNodeEncoder create(RemainingLimits limits) {
        return new Outer(limits);
    }

    /**
     * Encode the given value.
     *
     * @param node The node to encode
     */
    protected abstract void encodeValue(JsonNode node);

    @Override
    public void encodeString(String value) {
        encodeValue(JsonNode.createStringNode(value));
    }

    @Override
    public void encodeBoolean(boolean value) {
        encodeValue(JsonNode.createBooleanNode(value));
    }

    @Override
    public void encodeByte(byte value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeShort(short value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeChar(char value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeInt(int value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeLong(long value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeFloat(float value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeDouble(double value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeBigInteger(BigInteger value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) {
        encodeValue(JsonNode.createNumberNode(value));
    }

    @Override
    public void encodeBinary(byte @NonNull [] data) throws IOException {
        // we're allowed to encode to string because JsonNodeDecoder can handle it
        BinaryCodecUtil.encodeToBase64String(this, data);
    }

    @Override
    public void encodeNull() {
        encodeValue(JsonNode.nullNode());
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws SerdeException {
        return new Array(this, childLimits());
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws SerdeException {
        return new Obj(this, childLimits());
    }

    /**
     * Obtains the completed value.
     *
     * @return The materialized {@link io.micronaut.json.tree.JsonNode}
     */
    public @NonNull JsonNode getCompletedValue() {
        throw new IllegalStateException("Can only get the completed value of the outermost encoder");
    }

    private static final class Obj extends JsonNodeEncoder {
        private final JsonNodeEncoder target;
        private final Map<String, JsonNode> nodes = new LinkedHashMap<>();
        private String currentKey;

        Obj(JsonNodeEncoder target, RemainingLimits remainingLimits) {
            super(remainingLimits);
            this.target = target;
        }

        @Override
        public void finishStructure() {
            target.encodeValue(JsonNode.createObjectNode(nodes));
        }

        @Override
        public void encodeKey(String key) {
            if (currentKey != null) {
                throw new IllegalStateException("Already have a key");
            }
            currentKey = key;
        }

        @Override
        protected void encodeValue(JsonNode node) {
            if (currentKey == null) {
                throw new IllegalStateException("Need a key");
            }
            nodes.put(currentKey, node);
            currentKey = null;
        }
    }

    private static final class Array extends JsonNodeEncoder {
        private final JsonNodeEncoder target;
        private final List<JsonNode> nodes = new ArrayList<>();

        Array(JsonNodeEncoder target, RemainingLimits remainingLimits) {
            super(remainingLimits);
            this.target = target;
        }

        @Override
        public void finishStructure() {
            target.encodeValue(JsonNode.createArrayNode(nodes));
        }

        @Override
        public void encodeKey(String key) {
            throw new IllegalStateException("Arrays don't have keys");
        }

        @Override
        protected void encodeValue(JsonNode node) {
            nodes.add(node);
        }
    }

    private static final class Outer extends JsonNodeEncoder {
        JsonNode result;

        Outer(RemainingLimits remainingLimits) {
            super(remainingLimits);
        }

        @Override
        public void finishStructure() {
            throw new IllegalStateException("Not a structure");
        }

        @Override
        public void encodeKey(String key) {
            throw new IllegalStateException("Not an object");
        }

        @Override
        protected void encodeValue(JsonNode node) {
            if (result != null) {
                throw new IllegalStateException("Already completed");
            }
            result = node;
        }

        @Override
        public JsonNode getCompletedValue() {
            if (result == null) {
                throw new IllegalStateException("Not completed");
            }
            return result;
        }
    }
}
