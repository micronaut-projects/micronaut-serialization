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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Decoder that delegates to another decoder.
 *
 * @author Jonas Konrad
 * @since 2.3.0
 */
@SuppressWarnings("resource")
@Internal
public abstract class DelegatingDecoder implements Decoder {
    protected abstract Decoder delegate() throws IOException;

    public Decoder delegateForDecodeValue() throws IOException {
        return delegate();
    }

    @Override
    public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
        return delegate().decodeArray(type);
    }

    @Override
    public @NonNull Decoder decodeArray() throws IOException {
        return delegate().decodeArray();
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return delegate().hasNextArrayValue();
    }

    @Override
    public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
        return delegate().decodeObject(type);
    }

    @Override
    public @NonNull Decoder decodeObject() throws IOException {
        return delegate().decodeObject();
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        return delegate().decodeKey();
    }

    @Override
    public @NonNull String decodeString() throws IOException {
        return delegate().decodeString();
    }

    @Override
    public @Nullable String decodeStringNullable() throws IOException {
        return delegate().decodeStringNullable();
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return delegate().decodeBoolean();
    }

    @Override
    public @Nullable Boolean decodeBooleanNullable() throws IOException {
        return delegate().decodeBooleanNullable();
    }

    @Override
    public byte decodeByte() throws IOException {
        return delegate().decodeByte();
    }

    @Override
    public @Nullable Byte decodeByteNullable() throws IOException {
        return delegate().decodeByteNullable();
    }

    @Override
    public short decodeShort() throws IOException {
        return delegate().decodeShort();
    }

    @Override
    public @Nullable Short decodeShortNullable() throws IOException {
        return delegate().decodeShortNullable();
    }

    @Override
    public char decodeChar() throws IOException {
        return delegate().decodeChar();
    }

    @Override
    public @Nullable Character decodeCharNullable() throws IOException {
        return delegate().decodeCharNullable();
    }

    @Override
    public int decodeInt() throws IOException {
        return delegate().decodeInt();
    }

    @Override
    public @Nullable Integer decodeIntNullable() throws IOException {
        return delegate().decodeIntNullable();
    }

    @Override
    public long decodeLong() throws IOException {
        return delegate().decodeLong();
    }

    @Override
    public @Nullable Long decodeLongNullable() throws IOException {
        return delegate().decodeLongNullable();
    }

    @Override
    public float decodeFloat() throws IOException {
        return delegate().decodeFloat();
    }

    @Override
    public @Nullable Float decodeFloatNullable() throws IOException {
        return delegate().decodeFloatNullable();
    }

    @Override
    public double decodeDouble() throws IOException {
        return delegate().decodeDouble();
    }

    @Override
    public @Nullable Double decodeDoubleNullable() throws IOException {
        return delegate().decodeDoubleNullable();
    }

    @Override
    public @NonNull BigInteger decodeBigInteger() throws IOException {
        return delegate().decodeBigInteger();
    }

    @Override
    public @Nullable BigInteger decodeBigIntegerNullable() throws IOException {
        return delegate().decodeBigIntegerNullable();
    }

    @Override
    public @NonNull BigDecimal decodeBigDecimal() throws IOException {
        return delegate().decodeBigDecimal();
    }

    @Override
    public @Nullable BigDecimal decodeBigDecimalNullable() throws IOException {
        return delegate().decodeBigDecimalNullable();
    }

    @Override
    public byte @NonNull [] decodeBinary() throws IOException {
        return delegate().decodeBinary();
    }

    @Override
    public byte @Nullable [] decodeBinaryNullable() throws IOException {
        return delegate().decodeBinaryNullable();
    }

    @Override
    public boolean decodeNull() throws IOException {
        return delegate().decodeNull();
    }

    @Override
    public @Nullable Object decodeArbitrary() throws IOException {
        return delegate().decodeArbitrary();
    }

    @Override
    public @NonNull JsonNode decodeNode() throws IOException {
        return delegate().decodeNode();
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        return delegate().decodeBuffer();
    }

    @Override
    public void skipValue() throws IOException {
        delegate().skipValue();
    }

    @Override
    public void finishStructure() throws IOException {
        delegate().finishStructure();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        delegate().finishStructure(consumeLeftElements);
    }

    @Override
    public void close() throws IOException {
        delegate().close();
    }

    /**
     * This method remains abstract because it doesn't throw IOException and thus can't call decoder().
     * <br>
     * {@inheritDoc}
     */
    @Override
    public abstract @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue);
}
