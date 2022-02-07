/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.serde.jackson;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.json.UTF8JsonGenerator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Identical to {@link JacksonEncoder}, but specialized for {@link UTF8JsonGenerator} for better inlining.
 */
abstract class SpecializedJacksonEncoder implements Encoder {
    protected final UTF8JsonGenerator generator;
    @Nullable
    private final SpecializedJacksonEncoder parent;

    private SpecializedJacksonEncoder child = null;

    private SpecializedJacksonEncoder(@NonNull SpecializedJacksonEncoder parent) {
        this.generator = parent.generator;
        this.parent = parent;
    }

    private SpecializedJacksonEncoder(@NonNull UTF8JsonGenerator generator) {
        this.generator = generator;
        this.parent = null;
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
        SpecializedJacksonEncoder arrayEncoder = new ArrayEncoder(this);
        child = arrayEncoder;
        return arrayEncoder;
    }

    @Override
    public final Encoder encodeObject(Argument<?> type) throws IOException {
        checkChild();

        generator.writeStartObject();
        SpecializedJacksonEncoder objectEncoder = new ObjectEncoder(this);
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

    @Override
    public final void close() throws IOException {
        checkChild();
        finishStructureToken();
        if (parent != null) {
            parent.child = null;
        }
    }

    protected abstract void finishStructureToken() throws IOException;

    @Override
    public String currentPath() {
        final JsonStreamContext outputContext = generator.getOutputContext();
        return outputContext.pathAsPointer().toString();
    }

    @Override
    public final void encodeKey(@NonNull String key) throws IOException {
        Objects.requireNonNull(key, "key");
        generator.writeFieldName(key);
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
    public final void encodeNull() throws IOException {
        generator.writeNull();
    }

    private static final class ArrayEncoder extends SpecializedJacksonEncoder {
        ArrayEncoder(SpecializedJacksonEncoder parent) {
            super(parent);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            generator.writeEndArray();
        }
    }

    private static final class ObjectEncoder extends SpecializedJacksonEncoder {
        ObjectEncoder(SpecializedJacksonEncoder parent) {
            super(parent);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            generator.writeEndObject();
        }
    }

    static final class OuterEncoder extends SpecializedJacksonEncoder {
        OuterEncoder(@NonNull UTF8JsonGenerator generator) {
            super(generator);
        }

        @Override
        protected void finishStructureToken() {
            throw new IllegalStateException("Not in structure");
        }
    }
}
