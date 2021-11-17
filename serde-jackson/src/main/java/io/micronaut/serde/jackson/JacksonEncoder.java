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
package io.micronaut.serde.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.Encoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Implementation of the {@link io.micronaut.serde.Encoder} interface for Jackson.
 */
public abstract class JacksonEncoder implements Encoder {
    protected final JsonGenerator generator;
    @Nullable
    private final JacksonEncoder parent;

    private JacksonEncoder child = null;

    private JacksonEncoder(@NonNull JacksonEncoder parent) {
        this.generator = parent.generator;
        this.parent = parent;
    }

    private JacksonEncoder(@NonNull JsonGenerator generator) {
        this.generator = generator;
        this.parent = null;
    }

    public static JacksonEncoder create(@NonNull JsonGenerator generator) {
        Objects.requireNonNull(generator, "generator");
        return new OuterEncoder(generator);
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
    public Encoder encodeArray() throws IOException {
        checkChild();

        generator.writeStartArray();
        JacksonEncoder arrayEncoder = new ArrayEncoder(this);
        child = arrayEncoder;
        return arrayEncoder;
    }

    @Override
    public Encoder encodeObject() throws IOException {
        checkChild();

        generator.writeStartObject();
        JacksonEncoder objectEncoder = new ObjectEncoder(this);
        child = objectEncoder;
        return objectEncoder;
    }

    @Override
    public void finishStructure() throws IOException {
        checkChild();
        finishStructureToken();
        if (parent != null) {
            parent.child = null;
        }
    }

    protected abstract void finishStructureToken() throws IOException;

    @Override
    public String toString() {
        // need a common way to obtain the current output context
        return generator.getOutputContext().pathAsPointer().toString();
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        Objects.requireNonNull(key, "key");
        generator.writeFieldName(key);
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
        Objects.requireNonNull(value, "value");
        generator.writeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        generator.writeBoolean(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        Objects.requireNonNull(value, "value");
        generator.writeNumber(value);
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        Objects.requireNonNull(value, "value");
        generator.writeNumber(value);
    }

    @Override
    public void encodeNull() throws IOException {
        generator.writeNull();
    }

    private static class ArrayEncoder extends JacksonEncoder {
        ArrayEncoder(JacksonEncoder parent) {
            super(parent);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            generator.writeEndArray();
        }
    }

    private static class ObjectEncoder extends JacksonEncoder {
        ObjectEncoder(JacksonEncoder parent) {
            super(parent);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            generator.writeEndObject();
        }
    }

    private static class OuterEncoder extends JacksonEncoder {
        OuterEncoder(@NonNull JsonGenerator generator) {
            super(generator);
        }

        @Override
        protected void finishStructureToken() {
            throw new IllegalStateException("Not in structure");
        }
    }
}
