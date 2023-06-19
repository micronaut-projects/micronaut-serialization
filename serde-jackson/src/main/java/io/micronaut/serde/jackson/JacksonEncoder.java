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
import com.fasterxml.jackson.core.JsonStreamContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Implementation of the {@link io.micronaut.serde.Encoder} interface for Jackson.
 */
public abstract class JacksonEncoder extends LimitingStream implements Encoder {
    protected final JsonGenerator generator;
    @Nullable
    private final JacksonEncoder parent;

    private JacksonEncoder child = null;

    private JacksonEncoder(@NonNull JacksonEncoder parent, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = parent.generator;
        this.parent = parent;
    }

    private JacksonEncoder(@NonNull JsonGenerator generator, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = generator;
        this.parent = null;
    }

    @NonNull
    public static Encoder create(@NonNull JsonGenerator generator) {
        return create(generator, DEFAULT_LIMITS);
    }

    /**
     * Create a new encoder.
     *
     * @param generator       The jackson-core generator to write to
     * @param remainingLimits The maximum nesting depth
     * @return The encoder
     */
    @NonNull
    @Internal
    public static Encoder create(@NonNull JsonGenerator generator, @NonNull RemainingLimits remainingLimits) {
        Objects.requireNonNull(generator, "generator");
        return new ReuseChildEncoder(generator, remainingLimits);
    }

    void checkChild() {
        if (child != null) {
            throw new IllegalStateException("There is still an unfinished child generator");
        }
        if (parent != null && parent.child != this) {
            throw new IllegalStateException("This child generator has already completed");
        }
    }

    JacksonEncoder makeArrayChildEncoder() throws SerdeException {
        return new ArrayEncoder(this, childLimits());
    }

    @Override
    public final Encoder encodeArray(Argument<?> type) throws IOException {
        checkChild();

        generator.writeStartArray();
        JacksonEncoder arrayEncoder = makeArrayChildEncoder();
        child = arrayEncoder;
        return arrayEncoder;
    }

    JacksonEncoder makeObjectChildEncoder() throws SerdeException {
        return new ObjectEncoder(this, childLimits());
    }

    @Override
    public final Encoder encodeObject(Argument<?> type) throws IOException {
        checkChild();

        generator.writeStartObject();
        JacksonEncoder objectEncoder = makeObjectChildEncoder();
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
        finishStructure();
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

    private static final class ArrayEncoder extends JacksonEncoder {
        ArrayEncoder(JacksonEncoder parent, RemainingLimits remainingLimits) {
            super(parent, remainingLimits);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            generator.writeEndArray();
        }
    }

    private static final class ObjectEncoder extends JacksonEncoder {
        ObjectEncoder(JacksonEncoder parent, RemainingLimits remainingLimits) {
            super(parent, remainingLimits);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            generator.writeEndObject();
        }
    }

    private static final class OuterEncoder extends JacksonEncoder {
        OuterEncoder(@NonNull JsonGenerator generator, RemainingLimits remainingLimits) {
            super(generator, remainingLimits);
        }

        @Override
        protected void finishStructureToken() {
            throw new IllegalStateException("Not in structure");
        }
    }

    private static final class ReuseChildEncoder extends JacksonEncoder {
        private long type = 0;
        private int depth = 0;

        ReuseChildEncoder(@NonNull JsonGenerator generator, RemainingLimits remainingLimits) {
            super(generator, remainingLimits);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            if (depth == 0) {
                throw new IllegalStateException("Not in structure");
            }
            decreaseDepth();
            depth--;
            if ((type & 1) == 0) {
                generator.writeEndObject();
            } else {
                generator.writeEndArray();
            }
            type >>>= 1;
        }

        @Override
        JacksonEncoder makeArrayChildEncoder() throws SerdeException {
            if (depth == 64) {
                return super.makeArrayChildEncoder();
            } else {
                increaseDepth();
                depth++;
                type = (type << 1) | 1;
                return this;
            }
        }

        @Override
        JacksonEncoder makeObjectChildEncoder() throws SerdeException {
            if (depth == 64) {
                return super.makeObjectChildEncoder();
            } else {
                increaseDepth();
                depth++;
                type = type << 1;
                return this;
            }
        }

        @Override
        void checkChild() {
        }
    }
}
