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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.PriorityQueue;

/**
 * Serde for {@link PriorityQueue}.
 */
@Internal
@Singleton
final class PriorityQueueSerde implements SerdeRegistrar<PriorityQueue<Object>> {
    @SuppressWarnings("unchecked")
    private static final Argument<PriorityQueue<Object>> PRIORITY_QUEUE =
        (Argument<PriorityQueue<Object>>) (Argument<?>) Argument.of(PriorityQueue.class, Argument.ofTypeVariable(Object.class, "E"));

    @Override
    public Serializer<PriorityQueue<Object>> createSpecific(EncoderContext context, Argument<? extends PriorityQueue<Object>> type) throws SerdeException {
        Argument<Object> elementType = elementType(type);
        Serializer<? super Object> elementSerializer = context.findSerializer(elementType).createSpecific(context, elementType);
        return (encoder, encoderContext, queueType, value) -> {
            try (Encoder array = encoder.encodeArray(queueType)) {
                for (Object item : value) {
                    elementSerializer.serialize(array, encoderContext, elementType, item);
                }
            }
        };
    }

    @Override
    public Deserializer<PriorityQueue<Object>> createSpecific(DecoderContext context, Argument<? super PriorityQueue<Object>> type) throws SerdeException {
        Argument<Object> elementType = elementType(type);
        Deserializer<? extends Object> elementDeserializer = context.findDeserializer(elementType).createSpecific(context, elementType);
        return new Deserializer<>() {
            @Override
            @SuppressWarnings("java:S2638")
            public @Nullable PriorityQueue<Object> deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super PriorityQueue<Object>> type) throws IOException {
                if (decoder.decodeNull()) {
                    return null;
                }
                return deserialize(decoder, context, type);
            }

            @Override
            public PriorityQueue<Object> deserialize(Decoder decoder, DecoderContext context, Argument<? super PriorityQueue<Object>> type) throws IOException {
                PriorityQueue<Object> queue = new PriorityQueue<>();
                Decoder array = decoder.decodeArray();
                while (array.hasNextArrayValue()) {
                    Object item = elementDeserializer.deserializeNullable(array, context, elementType);
                    if (item == null) {
                        throw decoder.createDeserializationException("PriorityQueue does not support null values", null);
                    }
                    queue.add(item);
                }
                array.finishStructure();
                return queue;
            }
        };
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends PriorityQueue<Object>> type, PriorityQueue<Object> value) throws IOException {
        createSpecific(context, type).serialize(encoder, context, type, value);
    }

    @Override
    public PriorityQueue<Object> deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super PriorityQueue<Object>> type) throws IOException {
        return createSpecific(decoderContext, type).deserialize(decoder, decoderContext, type);
    }

    @Override
    public Argument<PriorityQueue<Object>> getType() {
        return PRIORITY_QUEUE;
    }

    @SuppressWarnings("unchecked")
    private static Argument<Object> elementType(Argument<?> type) {
        return (Argument<Object>) (type.getTypeParameters().length == 0 ? Argument.OBJECT_ARGUMENT : type.getTypeParameters()[0]);
    }
}
