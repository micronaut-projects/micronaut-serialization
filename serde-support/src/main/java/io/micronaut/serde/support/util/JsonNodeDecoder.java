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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.BinaryCodecUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface that
 * uses the {@link io.micronaut.json.tree.JsonNode} abstraction.
 */
@Internal
public abstract sealed class JsonNodeDecoder extends LimitingStream implements Decoder permits JsonArrayNodeDecoder, JsonNodeDecoder.Buffered, JsonObjectNodeDecoder {
    JsonNodeDecoder(LimitingStream.RemainingLimits remainingLimits) {
        super(remainingLimits);
    }

    public static JsonNodeDecoder create(JsonNode node, LimitingStream.RemainingLimits remainingLimits) {
        return new Buffered(node, remainingLimits);
    }

    protected abstract JsonNode peekValue() throws IOException;

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isArray()) {
            skipValue();
            return new JsonArrayNodeDecoder(peeked, childLimits());
        } else {
            throw createDeserializationException("Not an array", toArbitrary(peeked));
        }
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isObject()) {
            skipValue();
            return new JsonObjectNodeDecoder(peeked, childLimits());
        } else {
            throw createDeserializationException("Not an array", toArbitrary(peeked));
        }
    }

    @Override
    public String decodeString() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isString()) {
            skipValue();
            return peeked.getStringValue();
        } else {
            throw createDeserializationException("Not a string", toArbitrary(peeked));
        }
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isBoolean()) {
            skipValue();
            return peeked.getBooleanValue();
        } else {
            throw createDeserializationException("Not a boolean", toArbitrary(peeked));
        }
    }

    @Override
    public byte decodeByte() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return (byte) peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public short decodeShort() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return (short) peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public char decodeChar() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return (char) peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public int decodeInt() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public long decodeLong() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getLongValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getFloatValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public double decodeDouble() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getDoubleValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getBigIntegerValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getBigDecimalValue();
        } else {
            throw createDeserializationException("Not a number", toArbitrary(peeked));
        }
    }

    @Override
    public byte @NonNull [] decodeBinary() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isString()) {
            return BinaryCodecUtil.decodeFromBase64String(this);
        } else {
            return BinaryCodecUtil.decodeFromArray(this);
        }
    }

    @Override
    public boolean decodeNull() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNull()) {
            skipValue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Object decodeArbitrary() throws IOException {
        return toArbitrary(decodeNode());
    }

    @NonNull
    @Override
    public JsonNode decodeNode() throws IOException {
        JsonNode node = peekValue();
        skipValue();
        return node;
    }

    private static Object toArbitrary(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isNumber()) {
            return node.getNumberValue();
        } else if (node.isBoolean()) {
            return node.getBooleanValue();
        } else if (node.isString()) {
            return node.getStringValue();
        } else if (node.isArray()) {
            List<Object> transformed = new ArrayList<>(node.size());
            for (JsonNode value : node.values()) {
                transformed.add(toArbitrary(value));
            }
            return transformed;
        } else if (node.isObject()) {
            Map<String, Object> transformed = CollectionUtils.newLinkedHashMap(node.size());
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                transformed.put(entry.getKey(), toArbitrary(entry.getValue()));
            }
            return transformed;
        } else {
            throw new AssertionError(node);
        }
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        JsonNode peeked = peekValue();
        skipValue();
        return new Buffered(peeked, ourLimits());
    }

    @Override
    public IOException createDeserializationException(String message, Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message, null, invalidValue);
        } else {
            return new SerdeException(message);
        }
    }

    static final class Buffered extends JsonNodeDecoder {
        private final JsonNode node;

        Buffered(JsonNode node, RemainingLimits remainingLimits) {
            super(remainingLimits);
            this.node = node;
        }

        @Override
        public boolean hasNextArrayValue() {
            return false;
        }

        @Override
        public String decodeKey() {
            throw new IllegalStateException("Can't be called on buffered node");
        }

        @Override
        public void skipValue() {
            // No-op: Allow to read multiple times
        }

        @Override
        public void finishStructure(boolean consumeLeftElements) {
            throw new IllegalStateException("Can't be called on buffered node");
        }

        @Override
        protected JsonNode peekValue() {
            return node;
        }
    }
}
