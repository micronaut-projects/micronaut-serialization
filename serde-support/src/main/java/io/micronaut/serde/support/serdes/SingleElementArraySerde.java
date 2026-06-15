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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Helpers for single-element array serialization and deserialization behavior.
 *
 * @since 3.0
 */
@Internal
public final class SingleElementArraySerde {

    private SingleElementArraySerde() {
    }

    /**
     * Apply {@link SerializationConfiguration.Feature#WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED} if enabled.
     *
     * @param serializer The serializer
     * @param context The encoder context
     * @param <T> The serialized type
     * @return The serializer, wrapped when needed
     */
    public static <T> Serializer<T> writeSingleElementArraysUnwrapped(Serializer<T> serializer,
                                                                      Serializer.EncoderContext context) throws SerdeException {
        if (context.getFeatures().contains(SerializationConfiguration.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
            return new SingleElementArrayUnwrappingSerializer<>(serializer, context);
        }
        return serializer;
    }

    /**
     * Apply {@link DeserializationConfiguration.Feature#ACCEPT_SINGLE_VALUE_AS_ARRAY} if enabled.
     *
     * @param deserializer The deserializer
     * @param context The decoder context
     * @param <T> The deserialized type
     * @return The deserializer, wrapped when needed
     */
    @SuppressWarnings("unchecked")
    public static <T> Deserializer<T> acceptSingleValueAsArray(Deserializer<T> deserializer,
                                                               Deserializer.DecoderContext context) {
        if (context.getFeatures().contains(DeserializationConfiguration.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)) {
            if (deserializer instanceof UpdatingDeserializer<?> updatingDeserializer) {
                return new SingleValueAsArrayUpdatingDeserializer<>((UpdatingDeserializer<T>) updatingDeserializer, deserializer, context);
            }
            return new SingleValueAsArrayDeserializer<>(deserializer, context);
        }
        return deserializer;
    }

    private static LimitingStream.RemainingLimits encoderLimits(Serializer.EncoderContext context) {
        return context.getSerdeConfiguration()
            .map(LimitingStream::limitsFromConfiguration)
            .orElse(LimitingStream.DEFAULT_LIMITS);
    }

    private static LimitingStream.RemainingLimits decoderLimits(Deserializer.DecoderContext context) {
        return context.getSerdeConfiguration()
            .map(LimitingStream::limitsFromConfiguration)
            .orElse(LimitingStream.DEFAULT_LIMITS);
    }

    private static final class SingleElementArrayUnwrappingSerializer<T> implements Serializer<T> {

        private static final Argument<JsonNode> JSON_NODE_ARGUMENT = Argument.of(JsonNode.class);

        private final Serializer<T> delegate;
        private final LimitingStream.RemainingLimits remainingLimits;
        private final Serializer<? super JsonNode> jsonNodeSerializer;

        private SingleElementArrayUnwrappingSerializer(Serializer<T> delegate,
                                                       Serializer.EncoderContext context) throws SerdeException {
            this.delegate = delegate;
            this.remainingLimits = encoderLimits(context);
            this.jsonNodeSerializer = context.findSerializer(JsonNode.class);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends T> type,
                              T value) throws IOException {
            JsonNodeEncoder nodeEncoder = JsonNodeEncoder.create(remainingLimits);
            delegate.serialize(nodeEncoder, context, type, value);
            JsonNode node = nodeEncoder.getCompletedValue();
            if (node.isArray() && node.size() == 1) {
                jsonNodeSerializer.serialize(encoder, context, JSON_NODE_ARGUMENT, node.values().iterator().next());
            } else {
                jsonNodeSerializer.serialize(encoder, context, JSON_NODE_ARGUMENT, node);
            }
        }

        @Override
        public boolean isEmpty(EncoderContext context, @Nullable T value) {
            return delegate.isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(EncoderContext context, @Nullable T value) {
            return delegate.isAbsent(context, value);
        }

        @Override
        public boolean isDefault(EncoderContext context, T value) {
            return delegate.isDefault(context, value);
        }
    }

    private static final class SingleValueAsArrayDeserializer<T> implements Deserializer<T> {
        private final Deserializer<T> delegate;
        private final LimitingStream.RemainingLimits remainingLimits;

        private SingleValueAsArrayDeserializer(Deserializer<T> delegate,
                                               Deserializer.DecoderContext context) {
            this.delegate = delegate;
            this.remainingLimits = decoderLimits(context);
        }

        @Override
        public T deserialize(Decoder decoder,
                             DecoderContext context,
                             Argument<? super T> type) throws IOException {
            JsonNode node = decoder.decodeNode();
            if (!node.isArray()) {
                node = JsonNode.createArrayNode(List.of(node));
            }
            return delegate.deserialize(JsonNodeDecoder.create(node, remainingLimits), context, type);
        }

        @Override
        public @Nullable T getDefaultValue(DecoderContext context,
                                           Argument<? super T> type) {
            return delegate.getDefaultValue(context, type);
        }
    }

    private static final class SingleValueAsArrayUpdatingDeserializer<T> implements Deserializer<T>, UpdatingDeserializer<T> {
        private final UpdatingDeserializer<T> updatingDelegate;
        private final Deserializer<T> delegate;
        private final LimitingStream.RemainingLimits remainingLimits;

        private SingleValueAsArrayUpdatingDeserializer(UpdatingDeserializer<T> updatingDelegate,
                                                       Deserializer<T> delegate,
                                                       Deserializer.DecoderContext context) {
            this.updatingDelegate = updatingDelegate;
            this.delegate = delegate;
            this.remainingLimits = decoderLimits(context);
        }

        @Override
        public T deserialize(Decoder decoder,
                             DecoderContext context,
                             Argument<? super T> type) throws IOException {
            JsonNode node = arrayNode(decoder);
            return delegate.deserialize(JsonNodeDecoder.create(node, remainingLimits), context, type);
        }

        @Override
        public void deserializeInto(Decoder decoder,
                                    DecoderContext context,
                                    Argument<? super T> type,
                                    T value) throws IOException {
            JsonNode node = arrayNode(decoder);
            updatingDelegate.deserializeInto(JsonNodeDecoder.create(node, remainingLimits), context, type, value);
        }

        @Override
        public @Nullable T getDefaultValue(DecoderContext context,
                                           Argument<? super T> type) {
            return delegate.getDefaultValue(context, type);
        }

        private JsonNode arrayNode(Decoder decoder) throws IOException {
            JsonNode node = decoder.decodeNode();
            if (!node.isArray()) {
                node = JsonNode.createArrayNode(List.of(node));
            }
            return node;
        }
    }
}
