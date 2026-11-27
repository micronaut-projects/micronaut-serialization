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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.OptionalDouble;

@Internal
final class OptionalDoubleSerde implements SerdeRegistrar<OptionalDouble> {

    private static final OptionalDoubleSerde WHOLE_AS_INTEGERS = new OptionalDoubleSerde(true);

    private final boolean wholeAsIntegers;

    OptionalDoubleSerde() {
        this(false);
    }

    private OptionalDoubleSerde(boolean wholeAsIntegers) {
        this.wholeAsIntegers = wholeAsIntegers;
    }

    @Override
    public Serializer<OptionalDouble> createSpecific(EncoderContext context, Argument<? extends OptionalDouble> type) {
        return WholeFloats.enabled(context, type) ? WHOLE_AS_INTEGERS : this;
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends OptionalDouble> type, OptionalDouble value) throws IOException {
        if (value.isPresent()) {
            if (wholeAsIntegers) {
                WholeFloats.encode(encoder, value.getAsDouble());
            } else {
                encoder.encodeDouble(value.getAsDouble());
            }
        } else {
            encoder.encodeNull();
        }
    }

    @Override
    public OptionalDouble deserialize(Decoder decoder,
                                      DecoderContext context,
                                      Argument<? super OptionalDouble> type) throws IOException {
        if (decoder.decodeNull()) {
            return OptionalDouble.empty();
        } else {
            return OptionalDouble.of(decoder.decodeDouble());
        }
    }

    @Override
    public OptionalDouble deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super OptionalDouble> type) throws IOException {
        return deserialize(decoder, context, type);
    }

    @Override
    public boolean isEmpty(EncoderContext context, @Nullable OptionalDouble value) {
        return value == null || value.isEmpty();
    }

    @Override
    public boolean isAbsent(EncoderContext context, @Nullable OptionalDouble value) {
        return value == null || value.isEmpty();
    }

    @Override
    public OptionalDouble getDefaultValue(DecoderContext context, Argument<? super OptionalDouble> type) {
        return OptionalDouble.empty();
    }

    @Override
    public Argument<OptionalDouble> getType() {
        return Argument.of(OptionalDouble.class);
    }
}
