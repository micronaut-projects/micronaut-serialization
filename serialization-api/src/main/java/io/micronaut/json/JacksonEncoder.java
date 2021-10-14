package io.micronaut.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public abstract class JacksonEncoder implements Encoder {
    protected final JsonGenerator generator;
    @Nullable
    private final JacksonEncoder parent;

    private JacksonEncoder child = null;

    @NonNull
    private final Class<?> view;

    private JacksonEncoder(@NonNull JacksonEncoder parent) {
        this.generator = parent.generator;
        this.parent = parent;
        this.view = parent.view;
    }

    private JacksonEncoder(@NonNull JsonGenerator generator, @NonNull Class<?> view) {
        this.generator = generator;
        this.view = view;
        this.parent = null;
    }

    public static JacksonEncoder create(@NonNull JsonGenerator generator) {
        return create(generator, Object.class);
    }

    public static JacksonEncoder create(@NonNull JsonGenerator generator, @NonNull Class<?> view) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(view, "view");
        return new OuterEncoder(generator, view);
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

    @Override
    public void encodeArbitrary(Object object) throws IOException {
        generator.writeObject(object);
    }

    @Override
    public boolean hasView(Class<?>... views) {
        for (Class<?> candidate : views) {
            if (candidate.isAssignableFrom(view)) {
                return true;
            }
        }
        return false;
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
        OuterEncoder(@NonNull JsonGenerator generator, @NonNull Class<?> view) {
            super(generator, view);
        }

        @Override
        protected void finishStructureToken() throws IOException {
            throw new IllegalStateException("Not in structure");
        }
    }
}
