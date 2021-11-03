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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface that
 * uss the {@link io.micronaut.json.tree.JsonNode} abstraction.
 */
@Internal
public abstract class JsonNodeDecoder implements Decoder {
    private JsonNodeDecoder() {
    }

    public static JsonNodeDecoder create(JsonNode node) {
        return new Buffered(node);
    }

    protected abstract JsonNode peekValue();

    @Override
    public Decoder decodeArray() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isArray()) {
            skipValue();
            return new Array(peeked);
        } else {
            throw createDeserializationException("Not an array");
        }
    }

    @Override
    public Decoder decodeObject() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isObject()) {
            skipValue();
            return new Obj(peeked);
        } else {
            throw createDeserializationException("Not an array");
        }
    }

    @Override
    public String decodeString() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isString()) {
            skipValue();
            return peeked.getStringValue();
        } else {
            throw createDeserializationException("Not a string");
        }
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isBoolean()) {
            skipValue();
            return peeked.getBooleanValue();
        } else {
            throw createDeserializationException("Not a boolean");
        }
    }

    @Override
    public byte decodeByte() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return (byte) peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public short decodeShort() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return (short) peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public char decodeChar() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return (char) peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public int decodeInt() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getIntValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public long decodeLong() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getLongValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getFloatValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public double decodeDouble() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getDoubleValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getBigIntegerValue();
        } else {
            throw createDeserializationException("Not a number");
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getBigDecimalValue();
        } else {
            throw createDeserializationException("Not a number");
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
        Object result = toArbitrary(peekValue());
        skipValue();
        return result;
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
            List<Object> transformed = new ArrayList<>();
            for (JsonNode value : node.values()) {
                transformed.add(toArbitrary(value));
            }
            return transformed;
        } else if (node.isObject()) {
            Map<String, Object> transformed = new LinkedHashMap<>();
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
        return new Buffered(peeked);
    }

    @Override
    public IOException createDeserializationException(String message) {
        return new SerdeException(message);
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return false;
    }

    private static class Obj extends JsonNodeDecoder {
        private final Iterator<Map.Entry<String, JsonNode>> iterator;
        private JsonNode nextValue = null;

        Obj(JsonNode node) {
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
        public void skipValue() throws IOException {
            if (nextValue == null) {
                throw new IllegalStateException("Field name not parsed yet");
            }
            nextValue = null;
        }

        @Override
        public boolean hasNextArrayValue() throws IOException {
            return false;
        }

        @Override
        public String decodeKey() throws IOException {
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
        public void finishStructure() throws IOException {
            if (nextValue != null || iterator.hasNext()) {
                throw new IllegalStateException("Not all elements have been consumed yet");
            }
        }
    }

    private static class Array extends JsonNodeDecoder {
        private final Iterator<JsonNode> iterator;
        private JsonNode peeked;

        Array(JsonNode node) {
            iterator = node.values().iterator();
            skipValue();
        }

        @Override
        public boolean hasNextArrayValue() throws IOException {
            return peeked != null;
        }

        @Override
        public String decodeKey() throws IOException {
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
        public void finishStructure() throws IOException {
            if (peeked != null) {
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

    private static class Buffered extends JsonNodeDecoder {
        private final JsonNode node;
        private boolean complete = false;

        Buffered(JsonNode node) {
            this.node = node;
        }

        @Override
        public boolean hasNextArrayValue() throws IOException {
            return false;
        }

        @Override
        public String decodeKey() throws IOException {
            throw new IllegalStateException("Can't be called on buffered node");
        }

        @Override
        public void skipValue() throws IOException {
            if (complete) {
                throw new IllegalStateException("Already drained");
            }
            complete = true;
        }

        @Override
        public void finishStructure() throws IOException {
            throw new IllegalStateException("Can't be called on buffered node");
        }

        @Override
        protected JsonNode peekValue() {
            return node;
        }
    }
}
