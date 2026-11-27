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
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.util.Arrays;

@Internal
final class DoubleArraySerde extends AbstractArraySerde<double[]> {

    private static final DoubleArraySerde WHOLE_AS_INTEGERS = new DoubleArraySerde(true);

    private final boolean wholeAsIntegers;

    DoubleArraySerde() {
        this(false);
    }

    private DoubleArraySerde(boolean wholeAsIntegers) {
        this.wholeAsIntegers = wholeAsIntegers;
    }

    @Override
    public Serializer<double[]> createSpecific(EncoderContext context, Argument<? extends double[]> type) throws SerdeException {
        // the serializer is chosen once, so that no element is checked while the feature is off
        return SingleElementArraySerde.writeSingleElementArraysUnwrapped(
            WholeFloats.enabled(context, type) ? WHOLE_AS_INTEGERS : this, context);
    }

    @Override
    public double[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super double[]> type)
        throws IOException {
        final Decoder arrayDecoder = decoder.decodeArray();
        double[] buffer = new double[50];
        int index = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            if (buffer.length == index) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            if (!arrayDecoder.decodeNull()) {
                buffer[index] = arrayDecoder.decodeDouble();
            }
            index++;
        }
        arrayDecoder.finishStructure();
        return Arrays.copyOf(buffer, index);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends double[]> type, double[] value) throws IOException {
        final Encoder arrayEncoder = encoder.encodeArray(type);
        if (wholeAsIntegers) {
            for (double i : value) {
                WholeFloats.encode(arrayEncoder, i);
            }
        } else {
            for (double i : value) {
                arrayEncoder.encodeDouble(i);
            }
        }
        arrayEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(EncoderContext context, double @Nullable [] value) {
        return value == null || value.length == 0;
    }

    @Override
    public Argument<double[]> getType() {
        return Argument.of(double[].class);
    }
}
