package io.micronaut.serde.json.stream;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import io.micronaut.serde.Encoder;
import jakarta.json.stream.JsonGenerator;

final class JsonStreamEncoder implements Encoder {
    private final JsonGenerator jsonGenerator;

    public JsonStreamEncoder(JsonGenerator jsonGenerator) {
        this.jsonGenerator = jsonGenerator;
    }

    @Override
    public Encoder encodeArray() throws IOException {
        jsonGenerator.writeStartArray();
        return this;
    }

    @Override
    public Encoder encodeObject() throws IOException {
        jsonGenerator.writeStartObject();
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        jsonGenerator.writeEnd();
    }

    @Override
    public void encodeKey(String key) throws IOException {
        jsonGenerator.writeKey(key);
    }

    @Override
    public void encodeString(String value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeNull() throws IOException {
        jsonGenerator.writeNull();
    }

    @Override
    public boolean hasView(Class<?>... views) {
        // TODO
        return false;
    }
}
