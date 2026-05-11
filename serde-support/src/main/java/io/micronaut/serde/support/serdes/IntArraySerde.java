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

import java.io.IOException;
import java.util.Arrays;

@Internal
final class IntArraySerde extends AbstractArraySerde<int[]> {

    @Override
    public int[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super int[]> type)
        throws IOException {
        final Decoder arrayDecoder = decoder.decodeArray();
        int[] buffer = new int[50];
        int index = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            if (buffer.length == index) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            if (!arrayDecoder.decodeNull()) {
                buffer[index] = arrayDecoder.decodeInt();
            }
            index++;
        }
        arrayDecoder.finishStructure();
        return Arrays.copyOf(buffer, index);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends int[]> type, int[] value) throws IOException {
        final Encoder arrayEncoder = encoder.encodeArray(type);
        for (int i : value) {
            arrayEncoder.encodeInt(i);
        }
        arrayEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(EncoderContext context, int @Nullable [] value) {
        return value == null || value.length == 0;
    }

    @Override
    public Argument<int[]> getType() {
        return Argument.of(int[].class);
    }
}
