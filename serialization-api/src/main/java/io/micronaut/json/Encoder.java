package io.micronaut.json;

import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public interface Encoder {
    Encoder encodeArray() throws IOException;

    Encoder encodeObject() throws IOException;

    void finishStructure() throws IOException;

    void encodeKey(@NonNull String key) throws IOException;

    void encodeString(@NonNull String value) throws IOException;

    void encodeBoolean(boolean value) throws IOException;

    void encodeByte(byte value) throws IOException;

    void encodeShort(short value) throws IOException;

    void encodeChar(char value) throws IOException;

    void encodeInt(int value) throws IOException;

    void encodeLong(long value) throws IOException;

    void encodeFloat(float value) throws IOException;

    void encodeDouble(double value) throws IOException;

    void encodeBigInteger(@NonNull BigInteger value) throws IOException;

    void encodeBigDecimal(@NonNull BigDecimal value) throws IOException;

    void encodeNull() throws IOException;

    void encodeArbitrary(Object object) throws IOException;

    /**
     * @param views Views to check.
     * @return {@code true} iff any of the given views is enabled.
     */
    boolean hasView(Class<?>... views);
}
