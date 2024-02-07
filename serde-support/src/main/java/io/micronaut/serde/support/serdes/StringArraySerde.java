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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.Arrays;

/**
 * Deserializer for String arrays.
 * @author Denis Stepanov
 */
@Internal
final class StringArraySerde implements SerdeRegistrar<String[]> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends String[]> type, String[] strings)
        throws IOException {
        final Encoder arrayEncoder = encoder.encodeArray(type);
        for (String string : strings) {
            arrayEncoder.encodeString(string);
        }
        arrayEncoder.finishStructure();
    }

    @Override
    public String[] deserialize(Decoder decoder, DecoderContext context, Argument<? super String[]> type) throws IOException {
        final Decoder arrayDecoder = decoder.decodeArray();
        String[] buffer = new String[50];
        int index = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            final int l = buffer.length;
            if (l == index) {
                buffer = Arrays.copyOf(buffer, l * 2);
            }
            buffer[index++] = arrayDecoder.decodeStringNullable();
        }
        arrayDecoder.finishStructure();
        return Arrays.copyOf(buffer, index);
    }

    @Override
    public boolean isEmpty(EncoderContext context, String[] strings) {
        return strings == null || strings.length == 0;
    }

    @Override
    public Argument<String[]> getType() {
        return Argument.of(String[].class);
    }

}
