/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Deserializer for object arrays.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class CustomizedObjectArrayDeserializer implements Deserializer<Object[]> {

    private final Argument<Object> componentType;
    private final Deserializer<?> componentDeserializer;

    public CustomizedObjectArrayDeserializer(Argument<Object> componentType, Deserializer<?> deserializer) {
        this.componentType = componentType;
        this.componentDeserializer = deserializer;
    }

    @Override
    public Object[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object[]> type)
            throws IOException {
        boolean decoderNotAllowsNull = !componentDeserializer.allowNull();
        final Decoder arrayDecoder = decoder.decodeArray();
        // safe to assume only object[] handled
        Object[] buffer = (Object[]) Array.newInstance(componentType.getType(), 50);
        int index = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            final int l = buffer.length;
            if (l == index) {
                buffer = Arrays.copyOf(buffer, l * 2);
            }
            if (decoderNotAllowsNull && arrayDecoder.decodeNull()) {
                index++;
                continue;
            }
            buffer[index++] = componentDeserializer.deserialize(
                    arrayDecoder,
                    decoderContext,
                    componentType
            );
        }
        arrayDecoder.finishStructure();
        return Arrays.copyOf(buffer, index);
    }

}
