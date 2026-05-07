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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.LocalTime;

/**
 * Shared support for temporal {@link FormatConfiguration.Shape#ARRAY} serdes.
 *
 * @since 3.0.0
 */
final class TemporalArrayShapeSupport {

    private TemporalArrayShapeSupport() {
    }

    static boolean isNumericOrArrayShape(FormatConfiguration format) {
        return format.shape().isNumeric() || format.shape() == FormatConfiguration.Shape.ARRAY;
    }

    static boolean writeDateTimestampsAsNanos(Serializer.EncoderContext context) {
        return context.getFeatures().contains(SerializationConfiguration.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }

    static boolean readDateTimestampsAsNanos(Deserializer.DecoderContext context) {
        return context.getFeatures().contains(DeserializationConfiguration.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }

    static void serializeLocalTime(Encoder encoder,
                                   LocalTime localTime,
                                   boolean writeNanos) throws IOException {
        encoder.encodeInt(localTime.getHour());
        encoder.encodeInt(localTime.getMinute());
        int second = localTime.getSecond();
        int nano = localTime.getNano();
        if (second != 0 || nano != 0) {
            encoder.encodeInt(second);
        }
        if (nano != 0) {
            encoder.encodeInt(writeNanos ? nano : nano / 1_000_000);
        }
    }

    static LocalTime deserializeLocalTime(Decoder arrayDecoder, boolean readNanos) throws IOException {
        int hour = arrayDecoder.decodeInt();
        int minute = arrayDecoder.decodeInt();
        int second = arrayDecoder.hasNextArrayValue() ? arrayDecoder.decodeInt() : 0;
        int nano = arrayDecoder.hasNextArrayValue() ? arrayDecoder.decodeInt() : 0;
        if (!readNanos) {
            nano *= 1_000_000;
        }
        return LocalTime.of(hour, minute, second, nano);
    }

    abstract static class AbstractSerializer<T> implements Serializer<T> {
        private final Serializer<T> delegate;

        AbstractSerializer(Serializer<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public final void serialize(Encoder encoder,
                                    EncoderContext context,
                                    Argument<? extends T> type,
                                    T value) throws IOException {
            try (Encoder arrayEncoder = encoder.encodeArray(type)) {
                serializeArray(arrayEncoder, value);
            }
        }

        abstract void serializeArray(Encoder arrayEncoder, T value) throws IOException;

        @Override
        public final boolean isEmpty(EncoderContext context, @Nullable T value) {
            return delegate.isEmpty(context, value);
        }

        @Override
        public final boolean isAbsent(EncoderContext context, @Nullable T value) {
            return delegate.isAbsent(context, value);
        }

        @Override
        public final boolean isDefault(EncoderContext context, T value) {
            return delegate.isDefault(context, value);
        }
    }

    abstract static class AbstractDeserializer<T> implements Deserializer<T> {
        private final Deserializer<T> delegate;

        AbstractDeserializer(Deserializer<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public final T deserialize(Decoder decoder,
                                   DecoderContext context,
                                   Argument<? super T> type) throws IOException {
            try (Decoder arrayDecoder = decoder.decodeArray(Argument.INT)) {
                return deserializeArray(arrayDecoder);
            }
        }

        abstract T deserializeArray(Decoder arrayDecoder) throws IOException;

        @Override
        public @Nullable final T deserializeNullable(Decoder decoder,
                                           DecoderContext context,
                                           Argument<? super T> type) throws IOException {
            if (decoder.decodeNull()) {
                return null;
            }
            return deserialize(decoder, context, type);
        }

        @Nullable
        @Override
        public final T getDefaultValue(DecoderContext context, Argument<? super T> type) {
            return delegate.getDefaultValue(context, type);
        }
    }
}
