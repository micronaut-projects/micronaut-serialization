/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.concurrent.TimeUnit;

abstract class NumericSupportTemporalSerde<T extends TemporalAccessor> extends DefaultFormattedTemporalSerde<T> {
    private static final BigInteger NS_FACTOR = BigInteger.valueOf(1_000_000_000);

    private final SerdeConfiguration.TimeShape writeShape;
    private final SerdeConfiguration.NumericTimeUnit numericUnit;

    NumericSupportTemporalSerde(
        @NonNull SerdeConfiguration configuration,
        @NonNull DateTimeFormatter defaultStringFormatter,
        @NonNull SerdeConfiguration.NumericTimeUnit legacyUnit
    ) {
        super(configuration, defaultStringFormatter);
        writeShape = configuration.getTimeWriteShape();
        numericUnit = configuration.getNumericTimeUnit() == SerdeConfiguration.NumericTimeUnit.LEGACY ? legacyUnit : configuration.getNumericTimeUnit();
    }

    protected abstract T fromNanos(long seconds, int nanos);

    protected abstract long getSecondPart(T value);

    protected abstract int getNanoPart(T value);

    @Override
    final T deserializeFallback(DateTimeException exc, String str) {
        BigDecimal raw;
        try {
            raw = new BigDecimal(str);
        } catch (NumberFormatException e) {
            exc.addSuppressed(e);
            throw exc;
        }
        BigDecimal s = switch (numericUnit) {
            case LEGACY -> throw new AssertionError("Should be replaced in constructor");
            case SECONDS -> raw;
            case MILLISECONDS -> raw.scaleByPowerOfTen(-3);
            case NANOSECONDS -> raw.scaleByPowerOfTen(-9);
        };
        s = s.setScale(9, RoundingMode.DOWN);
        return fromNanos(s.longValue(), s.remainder(BigDecimal.ONE).unscaledValue().intValueExact());
    }

    @Override
    final void serialize0(Encoder encoder, T value) throws IOException {
        switch (writeShape) {
            case STRING -> super.serialize0(encoder, value);
            case INTEGER -> {
                switch (numericUnit) {
                    case LEGACY -> throw new AssertionError("Should be replaced in constructor");
                    case SECONDS -> encoder.encodeLong(getSecondPart(value));
                    case MILLISECONDS -> encoder.encodeLong(getSecondPart(value) * 1000L + TimeUnit.NANOSECONDS.toMillis(getNanoPart(value)));
                    // this can go out of bounds of long
                    case NANOSECONDS -> encoder.encodeBigInteger(BigInteger.valueOf(getSecondPart(value)).multiply(NS_FACTOR).add(BigInteger.valueOf(getNanoPart(value))));
                }
            }
            case DECIMAL -> {
                BigDecimal s = BigDecimal.valueOf(getSecondPart(value)).add(BigDecimal.valueOf(getNanoPart(value), 9));
                switch (numericUnit) {
                    case LEGACY -> throw new AssertionError("Should be replaced in constructor");
                    case SECONDS -> encoder.encodeBigDecimal(s);
                    case MILLISECONDS -> encoder.encodeBigDecimal(s.scaleByPowerOfTen(3));
                    case NANOSECONDS -> encoder.encodeBigInteger(s.unscaledValue());
                }
            }
        }
    }
}
