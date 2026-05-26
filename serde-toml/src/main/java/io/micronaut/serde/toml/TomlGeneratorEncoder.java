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
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.toml.encodestyle.InlineRootEncoder;
import io.micronaut.serde.toml.encodestyle.TableRootEncoder;
import io.micronaut.serde.toml.entities.ArrayValue;
import io.micronaut.serde.toml.entities.BooleanValue;
import io.micronaut.serde.toml.entities.NullValue;
import io.micronaut.serde.toml.entities.NumberValue;
import io.micronaut.serde.toml.entities.ObjectValue;
import io.micronaut.serde.toml.entities.StringValue;
import io.micronaut.serde.toml.entities.TomlValue;
import io.micronaut.serde.toml.support.SerdeTomlConfiguration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Native TOML text encoder backed by Micronaut Serialization encoder events.
 */
@Internal
public abstract class TomlGeneratorEncoder extends LimitingStream implements Encoder {
    private final String path;
    @Nullable
    private final TomlGeneratorEncoder parent;
    @Nullable
    private TomlGeneratorEncoder child;
    private boolean completed;

    protected TomlGeneratorEncoder(@NonNull RemainingLimits remainingLimits,
                                   @NonNull String path,
                                   @Nullable TomlGeneratorEncoder parent) {
        super(remainingLimits);
        this.path = path;
        this.parent = parent;
    }

    static @NonNull TomlGeneratorEncoder create(@NonNull OutputStream outputStream,
                                                @NonNull RemainingLimits remainingLimits,
                                                @NonNull SerdeTomlConfiguration tomlConfiguration) {
        return switch (tomlConfiguration.getWriteLayout()) {
            case TABLE -> new TableRootEncoder(outputStream, remainingLimits);
            case INLINE -> new InlineRootEncoder(outputStream, remainingLimits);
        };
    }

    /**
     * Write the completed metadata value. Only {@link io.micronaut.serde.toml.encodestyle.TomlStyleEncoder} encoders override this method.
     *
     * @throws IOException If writing the completed value fails
     */
    public void writeCompleted() throws IOException {
        throw new IllegalStateException("Can only write the completed value of the root encoder");
    }

    private void checkActive() {
        if (completed || (parent != null && parent.child != this)) {
            throw new IllegalStateException("This child encoder has already completed");
        }
    }

    protected final void checkChild() {
        checkActive();
        if (child != null) {
            throw new IllegalStateException("There is still an unfinished child encoder");
        }
    }

    private void encodeTomlValue(TomlValue value) throws IOException {
        checkChild();
        acceptValue(value);
    }

    final void completeStructure(TomlValue value) throws IOException {
        checkChild();
        completed = true;
        if (parent == null) {
            throw new IllegalStateException("Not in structure");
        }
        parent.child = null;
        parent.acceptValue(value);
    }

    /**
     * Accept a completed TOML value for this encoder context.
     *
     * @see TomlValue Abstract Representations, with entities implementations
     * @param value The value to accept
     * @throws IOException If accepting the value fails
     */
    protected abstract void acceptValue(TomlValue value) throws IOException;

    /**
     * Returns the path to use for the next child encoder.
     *
     * @return The path to use for the next child encoder
     */
    String childPath() {
        return path;
    }

    @Override
    public final @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        Objects.requireNonNull(type, "type");
        checkChild();
        TomlGeneratorEncoder arrayEncoder = new ArrayEncoder(this, childLimits(), childPath());
        child = arrayEncoder;
        return arrayEncoder;
    }

    @Override
    public final @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        Objects.requireNonNull(type, "type");
        checkChild();
        TomlGeneratorEncoder objectEncoder = new ObjectEncoder(this, childLimits(), childPath());
        child = objectEncoder;
        return objectEncoder;
    }

    @Override
    public void finishStructure() throws IOException {
        throw new IllegalStateException("Not in structure");
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        throw new IllegalStateException("Not an object");
    }

    @Override
    public final void encodeString(@NonNull String value) throws IOException {
        encodeTomlValue(new StringValue(Objects.requireNonNull(value, "value")));
    }

    @Override
    public final void encodeBoolean(boolean value) throws IOException {
        encodeTomlValue(new BooleanValue(value));
    }

    @Override
    public final void encodeByte(byte value) throws IOException {
        encodeTomlValue(new NumberValue(Byte.toString(value)));
    }

    @Override
    public final void encodeShort(short value) throws IOException {
        encodeTomlValue(new NumberValue(Short.toString(value)));
    }

    @Override
    public final void encodeChar(char value) throws IOException {
        encodeTomlValue(new NumberValue(Integer.toString(value)));
    }

    @Override
    public final void encodeInt(int value) throws IOException {
        encodeTomlValue(new NumberValue(Integer.toString(value)));
    }

    @Override
    public final void encodeLong(long value) throws IOException {
        encodeTomlValue(new NumberValue(Long.toString(value)));
    }

    @Override
    public final void encodeFloat(float value) throws IOException {
        encodeTomlValue(new NumberValue(renderFloat(value)));
    }

    @Override
    public final void encodeDouble(double value) throws IOException {
        encodeTomlValue(new NumberValue(renderDouble(value)));
    }

    @Override
    public final void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        encodeTomlValue(new NumberValue(Objects.requireNonNull(value, "value").toString()));
    }

    @Override
    public final void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        encodeTomlValue(new NumberValue(Objects.requireNonNull(value, "value").toString()));
    }

    @Override
    public final void encodeBinary(byte @NonNull [] data) throws IOException {
        encodeTomlValue(new StringValue(Base64.getEncoder().encodeToString(Objects.requireNonNull(data, "data"))));
    }

    @Override
    public final void encodeNull() throws IOException {
        encodeTomlValue(NullValue.INSTANCE);
    }

    @Override
    public @NonNull String currentPath() {
        return path;
    }

    /**
     * Renders a float value see <a href="https://toml.io/en/v1.0.0#float">TOML v1.0.0 Float specification</a>.
     */
    private static String renderFloat(float value) {
        if (Float.isNaN(value)) {
            return "nan";
        }
        if (value == Float.POSITIVE_INFINITY) {
            return "inf";
        }
        if (value == Float.NEGATIVE_INFINITY) {
            return "-inf";
        }
        return Float.toString(value);
    }

    /**
     * Renders a float value per the <a href="https://toml.io/en/v1.0.0#float">TOML v1.0.0 Float specification</a>.
     */
    private static String renderDouble(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        return Double.toString(value);
    }

    private static final class ArrayEncoder extends TomlGeneratorEncoder {
        private final List<TomlValue> values = new ArrayList<>();

        private ArrayEncoder(TomlGeneratorEncoder parent, RemainingLimits remainingLimits, String path) {
            super(remainingLimits, path, parent);
        }

        @Override
        protected void acceptValue(TomlValue value) {
            values.add(value);
        }

        @Override
        String childPath() {
            return currentPath() + "/" + values.size();
        }

        @Override
        public void finishStructure() throws IOException {
            completeStructure(new ArrayValue(values));
        }
    }

    private static final class ObjectEncoder extends TomlGeneratorEncoder {
        private final Map<String, TomlValue> values = new LinkedHashMap<>();
        @Nullable
        private String currentKey;

        private ObjectEncoder(TomlGeneratorEncoder parent, RemainingLimits remainingLimits, String path) {
            super(remainingLimits, path, parent);
        }

        @Override
        public void encodeKey(@NonNull String key) {
            Objects.requireNonNull(key, "key");
            checkChild();
            if (currentKey != null) {
                throw new IllegalStateException("Already have a key");
            }
            currentKey = key;
        }

        @Override
        protected void acceptValue(TomlValue value) throws IOException {
            if (currentKey == null) {
                throw new IllegalStateException("Need a key");
            }
            if (value instanceof NullValue) {
                currentKey = null;
                return;
            }
            if (values.containsKey(currentKey)) {
                throw new SerdeException("Duplicate TOML key: " + currentKey);
            }
            values.put(currentKey, value);
            currentKey = null;
        }

        @Override
        String childPath() {
            return currentKey == null ? currentPath() : currentPath() + "/" + currentKey;
        }

        @Override
        public @NonNull String currentPath() {
            if (currentKey == null) {
                return super.currentPath();
            }
            return super.currentPath() + "/" + currentKey;
        }

        @Override
        public void finishStructure() throws IOException {
            if (currentKey != null) {
                throw new IllegalStateException("Object key has no value");
            }
            completeStructure(new ObjectValue(values));
        }
    }
}
