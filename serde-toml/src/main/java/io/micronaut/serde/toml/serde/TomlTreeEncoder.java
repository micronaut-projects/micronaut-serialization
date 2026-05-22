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
package io.micronaut.serde.toml.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.toml.support.SerdeTomlConfiguration;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * TOML tree encoder that applies TOML write feature behavior to JsonNode trees.
 */
@Internal
public final class TomlTreeEncoder implements Encoder {
    private final Encoder delegate;
    private final JsonNodeEncoder root;
    private final boolean failOnNullWrite;

    private TomlTreeEncoder(Encoder delegate, JsonNodeEncoder root, boolean failOnNullWrite) {
        this.delegate = delegate;
        this.root = root;
        this.failOnNullWrite = failOnNullWrite;
    }

    public static TomlTreeEncoder create(LimitingStream.RemainingLimits limits,
                                         SerdeTomlConfiguration tomlConfiguration) {
        JsonNodeEncoder root = JsonNodeEncoder.create(limits);
        return new TomlTreeEncoder(root, root, tomlConfiguration.isFailOnNullWrite());
    }

    public @NonNull JsonNode getCompletedValue() {
        return root.getCompletedValue();
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        return new TomlTreeEncoder(delegate.encodeArray(type), root, failOnNullWrite);
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        return new TomlTreeEncoder(delegate.encodeObject(type), root, failOnNullWrite);
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
    public void encodeKey(@NonNull String key) throws IOException {
        delegate.encodeKey(key);
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
        delegate.encodeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        delegate.encodeBoolean(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        delegate.encodeByte(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        delegate.encodeShort(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        delegate.encodeChar(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        delegate.encodeInt(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        delegate.encodeLong(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        delegate.encodeFloat(value);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        delegate.encodeDouble(value);
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        delegate.encodeBigInteger(value);
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        delegate.encodeBigDecimal(value);
    }

    @Override
    public void encodeBinary(byte @NonNull [] data) throws IOException {
        delegate.encodeBinary(data);
    }

    @Override
    public void encodeNull() throws IOException {
        if (failOnNullWrite) {
            throw new SerdeException("TOML null writing disabled (FAIL_ON_NULL_WRITE)");
        }
        delegate.encodeNull();
    }

    @Override
    public @NonNull String currentPath() {
        return delegate.currentPath();
    }
}
