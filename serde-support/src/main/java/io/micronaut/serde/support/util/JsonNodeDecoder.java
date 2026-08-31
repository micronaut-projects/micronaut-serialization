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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.config.CoercionPolicy.Coercion;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.NullValueSerdeException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.BinaryCodecUtil;
import org.jspecify.annotations.Nullable;

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
    final CoercionPolicy coercionPolicy;

    JsonNodeDecoder(LimitingStream.RemainingLimits remainingLimits, CoercionPolicy coercionPolicy) {
        super(remainingLimits);
        this.coercionPolicy = coercionPolicy;
    }

    public static JsonNodeDecoder create(JsonNode node, LimitingStream.RemainingLimits remainingLimits) {
        return create(node, remainingLimits, CoercionPolicy.LENIENT);
    }

    /**
     * Create a decoder that only performs the coercions the given policy allows.
     *
     * @param node            The node to read
     * @param remainingLimits The remaining stream limits
     * @param coercionPolicy  The coercions this decoder may perform
     * @return The decoder
     */
    public static JsonNodeDecoder create(JsonNode node, LimitingStream.RemainingLimits remainingLimits, CoercionPolicy coercionPolicy) {
        return new Buffered(node, remainingLimits, coercionPolicy);
    }

    /**
     * Check the coercion needed to read the given number node as an integer type.
     */
    private void checkIntegerNode(JsonNode node) throws IOException {
        if (!coercionPolicy.isAllowed(Coercion.FLOAT_AS_INT)) {
            Number number = node.getNumberValue();
            if (number instanceof Double || number instanceof Float || number instanceof BigDecimal) {
                throw createDeserializationException(Coercion.FLOAT_AS_INT.message(), number);
            }
        }
    }

    /**
     * Read the given node as a number, coercing it if the policy allows. Mirrors what the
     * streaming decoders do for the same input.
     *
     * @return The coerced node, or {@code null} if no coercion is allowed for it
     */
    @Nullable
    private Number coerceToNumber(JsonNode node, boolean integral) throws IOException {
        if (node.isString()) {
            if (!coercionPolicy.isAllowed(Coercion.STRING_AS_NUMBER)) {
                return null;
            }
            String string = node.getStringValue();
            try {
                return integral ? new BigInteger(string) : new BigDecimal(string);
            } catch (NumberFormatException _) {
                throw createDeserializationException("Unable to coerce string to number", string);
            }
        }
        if (node.isBoolean()) {
            if (!coercionPolicy.isAllowed(Coercion.BOOLEAN_AS_NUMBER)) {
                return null;
            }
            return node.getBooleanValue() ? 1 : 0;
        }
        return null;
    }

    /**
     * Read the given node as a boolean, coercing it if the policy allows.
     */
    @Nullable
    private Boolean coerceToBoolean(JsonNode node) {
        if (node.isNumber() && coercionPolicy.isAllowed(Coercion.NUMBER_AS_BOOLEAN)) {
            return node.getNumberValue().doubleValue() != 0;
        }
        if (node.isString() && coercionPolicy.isAllowed(Coercion.STRING_AS_BOOLEAN)) {
            return Boolean.parseBoolean(node.getStringValue());
        }
        return null;
    }

    /**
     * Check the coercion needed to unwrap a single element array into a single value.
     */
    private void checkUnwrapArray() throws IOException {
        if (!coercionPolicy.isAllowed(Coercion.UNWRAP_SINGLE_VALUE_ARRAY)) {
            throw createDeserializationException(Coercion.UNWRAP_SINGLE_VALUE_ARRAY.message(), null);
        }
    }

    protected abstract JsonNode peekValue() throws IOException;

    @Override
    public final CoercionPolicy getCoercionPolicy() {
        return coercionPolicy;
    }

    private static NullValueSerdeException unexpectedNullToken(String expected) {
        return NullValueSerdeException.unexpectedToken(expected, "NULL");
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isArray()) {
            skipValue();
            return new JsonArrayNodeDecoder(peeked, childLimits(), coercionPolicy);
        } else {
            throw createDeserializationException("Not an array", toArbitrary(peeked));
        }
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isObject()) {
            skipValue();
            return new JsonObjectNodeDecoder(peeked, childLimits(), coercionPolicy);
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
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("STRING");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.STRING)) {
                String unwrapped = decoder.decodeString();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one string, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else if ((peeked.isNumber() || peeked.isBoolean()) && coercionPolicy.isAllowed(Coercion.SCALAR_AS_STRING)) {
            skipValue();
            return peeked.coerceStringValue();
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
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("BOOLEAN");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.BOOLEAN)) {
                boolean unwrapped = decoder.decodeBoolean();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one boolean, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Boolean coerced = coerceToBoolean(peeked);
            if (coerced == null) {
                throw createDeserializationException("Not a boolean", toArbitrary(peeked));
            }
            skipValue();
            return coerced;
        }
    }

    @Override
    public byte decodeByte() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            checkIntegerNode(peeked);
            skipValue();
            return (byte) peeked.getIntValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.BYTE)) {
                byte unwrapped = decoder.decodeByte();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one byte, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, true);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return (byte) coerced.intValue();
        }
    }

    @Override
    public short decodeShort() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            checkIntegerNode(peeked);
            skipValue();
            return (short) peeked.getIntValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.SHORT)) {
                short unwrapped = decoder.decodeShort();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one short, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, true);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return (short) coerced.intValue();
        }
    }

    @Override
    public char decodeChar() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            checkIntegerNode(peeked);
            skipValue();
            return (char) peeked.getIntValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.CHAR)) {
                char unwrapped = decoder.decodeChar();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one char, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, true);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return (char) coerced.intValue();
        }
    }

    @Override
    public int decodeInt() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            checkIntegerNode(peeked);
            skipValue();
            return peeked.getIntValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.INT)) {
                int unwrapped = decoder.decodeInt();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one int, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, true);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return coerced.intValue();
        }
    }

    @Override
    public long decodeLong() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            checkIntegerNode(peeked);
            skipValue();
            return peeked.getLongValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.LONG)) {
                long unwrapped = decoder.decodeLong();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one long, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, true);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return coerced.longValue();
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getFloatValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.FLOAT)) {
                float unwrapped = decoder.decodeFloat();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one float, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, false);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return coerced.floatValue();
        }
    }

    @Override
    public double decodeDouble() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getDoubleValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray(Argument.DOUBLE)) {
                double unwrapped = decoder.decodeDouble();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one double, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, false);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return coerced.doubleValue();
        }
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            checkIntegerNode(peeked);
            skipValue();
            return peeked.getBigIntegerValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray()) {
                BigInteger unwrapped = decoder.decodeBigInteger();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one BigInteger, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, true);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return coerced instanceof BigInteger big ? big : BigInteger.valueOf(coerced.longValue());
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        JsonNode peeked = peekValue();
        if (peeked.isNumber()) {
            skipValue();
            return peeked.getBigDecimalValue();
        } else if (peeked.isNull()) {
            throw unexpectedNullToken("NUMBER");
        } else if (peeked.isArray()) {
            checkUnwrapArray();
            try (Decoder decoder = decodeArray()) {
                BigDecimal unwrapped = decoder.decodeBigDecimal();
                if (decoder.hasNextArrayValue()) {
                    throw createDeserializationException("Expected one BigDecimal, but got array of multiple values", null);
                } else {
                    return unwrapped;
                }
            }
        } else {
            Number coerced = coerceToNumber(peeked, false);
            if (coerced == null) {
                throw createDeserializationException("Not a number", toArbitrary(peeked));
            }
            skipValue();
            return coerced instanceof BigDecimal big ? big : new BigDecimal(coerced.toString());
        }
    }

    @Override
    public byte[] decodeBinary() throws IOException {
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
    public @Nullable Object decodeArbitrary() throws IOException {
        return toArbitrary(decodeNode());
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        JsonNode node = peekValue();
        skipValue();
        return node;
    }

    @Nullable
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
        return new Buffered(peeked, ourLimits(), coercionPolicy);
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message, null, invalidValue);
        } else {
            return new SerdeException(message);
        }
    }

    static final class Buffered extends JsonNodeDecoder {
        private final JsonNode node;
        private boolean complete = false;

        Buffered(JsonNode node, RemainingLimits remainingLimits, CoercionPolicy coercionPolicy) {
            super(remainingLimits, coercionPolicy);
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
            if (complete) {
                throw new IllegalStateException("Already drained");
            }
            complete = true;
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
