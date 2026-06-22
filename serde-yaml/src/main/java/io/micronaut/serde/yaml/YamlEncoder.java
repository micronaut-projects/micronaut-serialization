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
package io.micronaut.serde.yaml;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * YAML implementation of the {@link Encoder} interface.
 *
 * @since 3.1.0
 */
@SuppressWarnings("NullAway")
public class YamlEncoder extends LimitingStream implements Encoder {

    private Yaml yaml;

    /**
     * Creates a YAML encoder with the supplied stream limits.
     *
     * @param remainingLimits The remaining stream limits
     */
    public YamlEncoder(@NonNull RemainingLimits remainingLimits) {
        super(remainingLimits);
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        return this;
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        return this;
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
