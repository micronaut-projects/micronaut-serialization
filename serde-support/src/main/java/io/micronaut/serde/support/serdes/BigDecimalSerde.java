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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.math.BigDecimal;

@Internal
final class BigDecimalSerde implements SerdeRegistrar<BigDecimal>, NumberSerde<BigDecimal> {

    @Override
    public Argument<BigDecimal> getType() {
        return Argument.of(BigDecimal.class);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends BigDecimal> type, BigDecimal value)
        throws IOException {
        encoder.encodeBigDecimal(value);
    }

    @Override
    public BigDecimal deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super BigDecimal> type)
        throws IOException {
        return decoder.decodeBigDecimal();
    }

    @Override
    public BigDecimal deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super BigDecimal> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }
}
