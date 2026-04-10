package io.micronaut.serde.yaml;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class YamlEncoder extends LimitingStream implements Encoder {

    private Yaml yaml;

    public YamlEncoder(@NonNull RemainingLimits remainingLimits) {
        super(remainingLimits);
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        return null;
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        return null;
    }

    @Override
    public void finishStructure() throws IOException {

    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {

    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {

    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {

    }

    @Override
    public void encodeByte(byte value) throws IOException {

    }

    @Override
    public void encodeShort(short value) throws IOException {

    }

    @Override
    public void encodeChar(char value) throws IOException {

    }

    @Override
    public void encodeInt(int value) throws IOException {

    }

    @Override
    public void encodeLong(long value) throws IOException {

    }

    @Override
    public void encodeFloat(float value) throws IOException {

    }

    @Override
    public void encodeDouble(double value) throws IOException {

    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {

    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {

    }

    @Override
    public void encodeNull() throws IOException {

    }
}
