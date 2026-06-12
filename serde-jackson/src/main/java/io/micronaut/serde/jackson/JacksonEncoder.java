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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.KeysSupport;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.TokenStreamContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementation of the {@link io.micronaut.serde.Encoder} interface for Jackson.
 */
public abstract class JacksonEncoder extends LimitingStream implements KeysAwareEncoder {
    private static final int JACKSON_KEYS_INDEX = KeysSupport.indexOf(new JacksonKeysProvider());

    protected final JsonGenerator generator;
    @Nullable
    private final JacksonEncoder parent;

    @Nullable
    private JacksonEncoder child;
    @Nullable
    private Keys currentKeys;
    private SerializableString @Nullable [] currentSerializableKeys;

    private JacksonEncoder(JacksonEncoder parent, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = parent.generator;
        this.parent = parent;
    }

    private JacksonEncoder(JsonGenerator generator, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = generator;
        this.parent = null;
    }

    public static Encoder create(JsonGenerator generator) {
        return create(generator, DEFAULT_LIMITS);
    }

    /**
     * Create a new encoder.
     *
     * @param generator       The jackson-core generator to write to
     * @param remainingLimits The maximum nesting depth
     * @return The encoder
     */
    @Internal
    public static Encoder create(JsonGenerator generator, RemainingLimits remainingLimits) {
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
        final TokenStreamContext outputContext = generator.streamWriteContext();
        return outputContext.pathAsPointer().toString();
    }

    @Override
    public final void encodeKey(String key) throws IOException {
        generator.writeName(key);
    }

    @Override
    public final void encodeKey(Keys keys, int index) throws IOException {
        generator.writeName(serializableKey(keys, index));
    }

    private SerializableString serializableKey(Keys keys, int index) {
        SerializableString[] serializableKeys = currentSerializableKeys;
        if (keys != currentKeys || serializableKeys == null) {
            serializableKeys = (SerializableString[]) KeysSupport.get(keys, JACKSON_KEYS_INDEX)[JacksonKeysProvider.SERIALIZABLE_KEYS_INDEX];
            currentKeys = keys;
            currentSerializableKeys = serializableKeys;
        }
        return serializableKeys[index];
    }

    @Override
    public final void encodeString(String value) throws IOException {
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
    public final void encodeBigInteger(BigInteger value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public final void encodeBigDecimal(BigDecimal value) throws IOException {
        generator.writeNumber(value);
    }

    @Override
    public void encodeBinary(byte[] data) throws IOException {
        generator.writeBinary(data);
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
        OuterEncoder(JsonGenerator generator, RemainingLimits remainingLimits) {
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

        ReuseChildEncoder(JsonGenerator generator, RemainingLimits remainingLimits) {
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
