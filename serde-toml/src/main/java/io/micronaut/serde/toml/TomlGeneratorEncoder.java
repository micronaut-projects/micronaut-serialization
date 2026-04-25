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
package io.micronaut.serde.toml;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * TOML encoder backed by Jackson's TOML generator.
 */
@Internal
abstract class TomlGeneratorEncoder extends LimitingStream implements Encoder {

    protected final JsonGenerator generator;
    @Nullable
    private final TomlGeneratorEncoder parent;
    @Nullable
    private TomlGeneratorEncoder child;

    private TomlGeneratorEncoder(@NonNull JsonGenerator generator, @NonNull RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = generator;
        this.parent = null;
    }

    private TomlGeneratorEncoder(@NonNull TomlGeneratorEncoder parent, @NonNull RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = parent.generator;
        this.parent = parent;
    }

    static @NonNull Encoder create(@NonNull JsonGenerator generator, @NonNull RemainingLimits remainingLimits) {
        return new RootEncoder(generator, remainingLimits);
    }

    private void checkChild() {
        if (child != null) {
            throw new IllegalStateException("There is still an unfinished child generator");
        }
        if (parent != null && parent.child != this) {
            throw new IllegalStateException("This child generator has already completed");
        }
    }

    @Override
    public final Encoder encodeArray(Argument<?> type) throws IOException {
        checkChild();
        generator.writeStartArray();
        TomlGeneratorEncoder arrayEncoder = new ArrayEncoder(this, childLimits());
        child = arrayEncoder;
        return arrayEncoder;
    }

    @Override
    public final Encoder encodeObject(Argument<?> type) throws IOException {
        checkChild();
        generator.writeStartObject();
        TomlGeneratorEncoder objectEncoder = new ObjectEncoder(this, childLimits());
        child = objectEncoder;
        return objectEncoder;
    }

    @Override
    public final void finishStructure() throws IOException {
        checkChild();
        finishStructureToken();
        if (parent != null) {
            parent.child = null;
        }
    }

    protected void finishStructureToken() throws IOException {
        throw new IllegalStateException("Not in structure");
    }

    @Override
    public final void close() throws IOException {
        finishStructure();
    }

    @Override
    public final void encodeKey(@NonNull String key) throws IOException {
        Objects.requireNonNull(key, "key");
        generator.writeName(key);
    }

    @Override
    public final void encodeString(@NonNull String value) throws IOException {
        Objects.requireNonNull(value, "value");
        generator.writeString(value);
    }

    @Override
    public final void encodeBoolean(boolean value) throws IOException {
        generator.writeBoolean(value);
    }

    @Override
    public final void encodeByte(byte value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeShort(short value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeChar(char value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeInt(int value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeLong(long value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeFloat(float value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeDouble(double value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        Objects.requireNonNull(value, "value");
        generator.writeNumber(value);
    }

    @Override
    public final void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        Objects.requireNonNull(value, "value");
        generator.writeNumber(value);
    }

    @Override
    public final void encodeBinary(byte @NonNull [] data) throws IOException {
        generator.writeBinary(data);
    }

    @Override
    public final void encodeNull() throws IOException {
        generator.writeNull();
    }

    @Override
    public @NonNull String currentPath() {
        TokenStreamContext outputContext = generator.streamWriteContext();
        return outputContext.pathAsPointer().toString();
    }

    private static final class ArrayEncoder extends TomlGeneratorEncoder {
        private ArrayEncoder(TomlGeneratorEncoder parent, RemainingLimits remainingLimits) {
            super(parent, remainingLimits);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            this.generator.writeEndArray();
        }
    }

    private static final class RootEncoder extends TomlGeneratorEncoder {
        private RootEncoder(JsonGenerator generator, RemainingLimits remainingLimits) {
            super(generator, remainingLimits);
        }
    }

    private static final class ObjectEncoder extends TomlGeneratorEncoder {
        private ObjectEncoder(TomlGeneratorEncoder parent, RemainingLimits remainingLimits) {
            super(parent, remainingLimits);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            this.generator.writeEndObject();
        }
    }
}
