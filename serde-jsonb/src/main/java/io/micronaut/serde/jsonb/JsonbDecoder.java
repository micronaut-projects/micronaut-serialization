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
import java.util.Base64;

record JsonbDecoder(Decoder delegate, String binaryDataStrategy) implements Decoder {

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        delegate.decodeArray(type);
        return this;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return delegate.hasNextArrayValue();
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        delegate.decodeObject(type);
        return this;
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
                buffer.write(value.isNull() ? 0 : value.getIntValue());
            }
            return buffer.toByteArray();
        }
        throw createDeserializationException("Expected binary data as base64 string or byte array", node.getValue());
    }

    @Override
    public boolean decodeNull() throws IOException {
        return delegate.decodeNull();
    }

    @Override
    public @Nullable Object decodeArbitrary() throws IOException {
        return delegate.decodeArbitrary();
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
}
