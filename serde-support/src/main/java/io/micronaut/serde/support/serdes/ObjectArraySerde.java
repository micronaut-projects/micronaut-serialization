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

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;

/**
 * Deserializer for object arrays.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ObjectArraySerde implements NullableSerde<Object[]>, Serde<Object[]> {
    @Override
    public Deserializer<Object[]> createSpecific(Argument<? super Object[]> context, DecoderContext decoderContext)
            throws SerdeException {
        final Argument<Object> componentType = getComponentType(context);
        final Deserializer<?> deserializer = findDeserializer(decoderContext, componentType);
        return new ObjectArraySerde() {
            @Override
            protected Argument<Object> getComponentType(Argument<? super Object[]> type) {
                return componentType;
            }

            @Override
            protected Deserializer<?> findDeserializer(DecoderContext decoderContext, Argument<?> componentType) {
                return deserializer;
            }
        };
    }

    @Override
    public Object[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Object[]> type)
            throws IOException {
        final Argument<Object> componentType = getComponentType(type);
        final Decoder arrayDecoder = decoder.decodeArray();
        // safe to assume only object[] handled
        Object[] buffer = (Object[]) Array.newInstance(componentType.getType(), 50);
        Deserializer<?> deserializer = findDeserializer(decoderContext, componentType);
        int index = 0;
        while (arrayDecoder.hasNextArrayValue()) {
            final int l = buffer.length;
            if (l == index) {
                buffer = Arrays.copyOf(buffer, l * 2);
            }
            buffer[index++] = deserializer.deserialize(
                    arrayDecoder,
                    decoderContext,
                    componentType
            );
        }
        arrayDecoder.finishStructure();
        return Arrays.copyOf(buffer, index);
    }

    /**
     * resolves the component type.
     * @param type The component type
     * @return The component type
     */
    protected Argument<Object> getComponentType(Argument<? super Object[]> type) {
        return Argument.of((Class<Object>) type.getType().getComponentType());
    }

    /**
     * Resolves the deserializer.
     * @param decoderContext The decoder context
     * @param componentType The component type
     * @return The deserializer
     * @throws SerdeException if no deserializer is available.
     */
    protected Deserializer<?> findDeserializer(DecoderContext decoderContext, Argument<?> componentType) throws SerdeException {
        return decoderContext.findDeserializer(componentType);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Object[] value, Argument<? extends Object[]> type)
            throws IOException {
        final Encoder arrayEncoder = encoder.encodeArray(type);
        // TODO: need better generics handling in core for arrays
        final Argument<?> componentType = Argument.of(type.getType().getComponentType());
        final Serializer<Object> componentSerializer =
                (Serializer<Object>) context.findSerializer(componentType);
        for (Object v : value) {
            componentSerializer.serialize(
                    arrayEncoder,
                    context,
                    v,
                    componentType
            );
        }
        arrayEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(Object[] value) {
        return ArrayUtils.isEmpty(value);
    }
}
