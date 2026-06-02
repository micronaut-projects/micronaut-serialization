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
package io.micronaut.serde.jsonb;

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import jakarta.json.bind.config.BinaryDataStrategy;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-B decoder wrapper around a Serde decoder.
 * <p>
 * The wrapper keeps normal Serde deserialization in control while applying the
 * JSON-B-specific scalar rules that differ from the generic decoder contract,
 * such as binary data strategies and untyped {@link BigDecimal} numbers.
 *
 * @param delegate The underlying Serde decoder
 * @param binaryDataStrategy The configured JSON-B binary data strategy
 */
record JsonbDecoder(Decoder delegate, String binaryDataStrategy) implements Decoder {

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        return new JsonbDecoder(delegate.decodeArray(type), binaryDataStrategy);
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return delegate.hasNextArrayValue();
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        return new JsonbDecoder(delegate.decodeObject(type), binaryDataStrategy);
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        return delegate.decodeKey();
    }

    @Override
    public String decodeString() throws IOException {
        return delegate.decodeString();
    }

    @Override
    public @Nullable String decodeStringNullable() throws IOException {
        return delegate.decodeStringNullable();
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return delegate.decodeBoolean();
    }

    @Override
    public @Nullable Boolean decodeBooleanNullable() throws IOException {
        return delegate.decodeBooleanNullable();
    }

    @Override
    public byte decodeByte() throws IOException {
        return delegate.decodeByte();
    }

    @Override
    public @Nullable Byte decodeByteNullable() throws IOException {
        return delegate.decodeByteNullable();
    }

    @Override
    public short decodeShort() throws IOException {
        return delegate.decodeShort();
    }

    @Override
    public @Nullable Short decodeShortNullable() throws IOException {
        return delegate.decodeShortNullable();
    }

    @Override
    public char decodeChar() throws IOException {
        return delegate.decodeChar();
    }

    @Override
    public @Nullable Character decodeCharNullable() throws IOException {
        return delegate.decodeCharNullable();
    }

    @Override
    public Number decodeNumber() throws IOException {
        return delegate.decodeBigDecimal();
    }

    @Override
    public @Nullable Number decodeNumberNullable() throws IOException {
        return delegate.decodeBigDecimalNullable();
    }

    @Override
    public int decodeInt() throws IOException {
        return delegate.decodeInt();
    }

    @Override
    public @Nullable Integer decodeIntNullable() throws IOException {
        return delegate.decodeIntNullable();
    }

    @Override
    public long decodeLong() throws IOException {
        return delegate.decodeLong();
    }

    @Override
    public @Nullable Long decodeLongNullable() throws IOException {
        return delegate.decodeLongNullable();
    }

    @Override
    public float decodeFloat() throws IOException {
        return delegate.decodeFloat();
    }

    @Override
    public @Nullable Float decodeFloatNullable() throws IOException {
        return delegate.decodeFloatNullable();
    }

    @Override
    public double decodeDouble() throws IOException {
        return delegate.decodeDouble();
    }

    @Override
    public @Nullable Double decodeDoubleNullable() throws IOException {
        return delegate.decodeDoubleNullable();
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        return delegate.decodeBigInteger();
    }

    @Override
    public @Nullable BigInteger decodeBigIntegerNullable() throws IOException {
        return delegate.decodeBigIntegerNullable();
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        return delegate.decodeBigDecimal();
    }

    @Override
    public @Nullable BigDecimal decodeBigDecimalNullable() throws IOException {
        return delegate.decodeBigDecimalNullable();
    }

    @Override
    public byte @Nullable [] decodeBinaryNullable() throws IOException {
        if (decodeNull()) {
            return null;
        }
        return decodeBinary();
    }

    @Override
    public byte[] decodeBinary() throws IOException {
        JsonNode node = delegate.decodeNode();
        if (node.isString()) {
            String value = node.getStringValue();
            try {
                if (BinaryDataStrategy.BASE_64_URL.equals(binaryDataStrategy)) {
                    return Base64.getUrlDecoder().decode(padBase64(value));
                }
                return Base64.getDecoder().decode(padBase64(value));
            } catch (IllegalArgumentException e) {
                throw createDeserializationException("Illegal base64 input: " + e.getMessage(), value);
            }
        }
        if (node.isArray()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (JsonNode value : node.values()) {
                buffer.write(value.isNull() ? 0 : checkedByte(value));
            }
            return buffer.toByteArray();
        }
        throw createDeserializationException("Expected binary data as base64 string or byte array", node.getValue());
    }

    private int checkedByte(JsonNode node) throws IOException {
        if (!node.isNumber()) {
            throw createDeserializationException("Expected byte array entry to be a number", node.getValue());
        }
        BigInteger integer = integralValue(node.getNumberValue());
        if (integer.compareTo(BigInteger.valueOf(Byte.MIN_VALUE)) < 0 || integer.compareTo(BigInteger.valueOf(Byte.MAX_VALUE)) > 0) {
            throw createDeserializationException("Byte array entry is out of range: " + node.getNumberValue(), node.getValue());
        }
        return integer.intValue();
    }

    private BigInteger integralValue(Number number) throws IOException {
        try {
            if (number instanceof BigInteger integer) {
                return integer;
            }
            if (number instanceof BigDecimal decimal) {
                return decimal.toBigIntegerExact();
            }
            if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                return BigInteger.valueOf(number.longValue());
            }
            if (number instanceof Float || number instanceof Double) {
                return BigDecimal.valueOf(number.doubleValue()).toBigIntegerExact();
            }
            return new BigDecimal(number.toString()).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw createDeserializationException("Byte array entry is not an integral value: " + number, number);
        }
    }

    @Override
    public boolean decodeNull() throws IOException {
        return delegate.decodeNull();
    }

    @Override
    public @Nullable Object decodeArbitrary() throws IOException {
        return toJsonbArbitrary(delegate.decodeNode());
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        return delegate.decodeNode();
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        return new JsonbDecoder(delegate.decodeBuffer(), binaryDataStrategy);
    }

    @Override
    public void skipValue() throws IOException {
        delegate.skipValue();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        delegate.finishStructure(consumeLeftElements);
    }

    @Override
    public void finishStructure() throws IOException {
        delegate.finishStructure();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return delegate.createDeserializationException(message, invalidValue);
    }

    private static String padBase64(String value) {
        int padding = value.length() % 4;
        return padding == 0 ? value : value + "=".repeat(4 - padding);
    }

    /**
     * Converts untyped JSON-B values to the representation required by the
     * Jakarta JSON-B default mapping rules. In particular, numbers under
     * {@code Object}, raw {@code List}, and raw {@code Map} must materialize as
     * {@link BigDecimal}, not as the narrower numeric type preserved by the
     * generic {@link io.micronaut.serde.Decoder#decodeArbitrary()} contract.
     *
     * @param node The JSON tree to convert
     * @return The JSON-B arbitrary value
     */
    private static @Nullable Object toJsonbArbitrary(JsonNode node) {
        if (node.isObject()) {
            Map<String, @Nullable Object> converted = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                converted.put(entry.getKey(), toJsonbArbitrary(entry.getValue()));
            }
            return converted;
        }
        if (node.isArray()) {
            List<@Nullable Object> converted = new ArrayList<>(node.size());
            for (JsonNode item : node.values()) {
                converted.add(toJsonbArbitrary(item));
            }
            return converted;
        }
        if (node.isNumber()) {
            Number number = node.getNumberValue();
            if (number instanceof BigDecimal decimal) {
                return decimal;
            }
            if (number instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            return new BigDecimal(String.valueOf(number));
        }
        if (node.isString()) {
            return node.getStringValue();
        }
        if (node.isBoolean()) {
            return node.getBooleanValue();
        }
        return null;
    }
}
