/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

@Internal
final class NumberTypeSerde implements SerdeRegistrar<Number>, NumberSerde<Number> {

    @Override
    public Argument<Number> getType() {
        return Argument.of(Number.class);
    }

    @Override
    public Number deserialize(Decoder decoder,
                              DecoderContext decoderContext,
                              Argument<? super Number> type) throws IOException {
        return decoder.decodeNumber();
    }

    @Override
    public Number deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Number> type) throws IOException {
        return decoder.decodeNumberNullable();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Number> type,
                          Number value) throws IOException {
        if (value instanceof Integer integer) {
            encoder.encodeInt(integer);
        } else if (value instanceof Long aLong) {
            encoder.encodeLong(aLong);
        } else if (value instanceof Double aDouble) {
            encoder.encodeDouble(aDouble);
        } else if (value instanceof Float aFloat) {
            encoder.encodeFloat(aFloat);
        } else if (value instanceof Byte aByte) {
            encoder.encodeByte(aByte);
        } else if (value instanceof Short aShort) {
            encoder.encodeShort(aShort);
        } else if (value instanceof BigDecimal bigDecimal) {
            encoder.encodeBigDecimal(bigDecimal);
        } else if (value instanceof BigInteger bigInteger) {
            encoder.encodeBigInteger(bigInteger);
        } else {
            throw new SerdeException("Unrecognized Number type: " + value.getClass().getName() + " " + value);
        }
    }

    @Nullable
    @Override
    public Integer getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Number> type) {
        return type.isPrimitive() ? 0 : null;
    }
}
