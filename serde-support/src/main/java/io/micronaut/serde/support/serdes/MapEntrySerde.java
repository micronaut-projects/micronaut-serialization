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
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

/**
 * Serde for {@link Map.Entry}.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
final class MapEntrySerde implements FormattedSerde<Map.Entry<?, ?>>, SerdeRegistrar<Map.Entry<?, ?>> {
    private static final String MAP_ENTRY_KEY = "key";
    private static final String MAP_ENTRY_VALUE = "value";

    @Override
    public @NonNull Serializer<Map.Entry<?, ?>> createSpecific(@NonNull EncoderContext context,
                                                               @NonNull Argument<? extends Map.Entry<?, ?>> type) throws SerdeException {
        return new MapEntryNaturalSerializer<>(this, context, type);
    }

    @Override
    public @NonNull Serializer<Map.Entry<?, ?>> createSpecific(@NonNull EncoderContext context,
                                                               @NonNull Argument<? extends Map.Entry<?, ?>> type,
                                                               @NonNull FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return new MapEntryPojoSerializer<>(this, context, type);
        }
        return createSpecific(context, type);
    }

    @Override
    public @NonNull Deserializer<Map.Entry<?, ?>> createSpecific(@NonNull DecoderContext context,
                                                                 @NonNull Argument<? super Map.Entry<?, ?>> type) throws SerdeException {
        return new MapEntryNaturalDeserializer<>(context, type);
    }

    @Override
    public @NonNull Deserializer<Map.Entry<?, ?>> createSpecific(@NonNull DecoderContext context,
                                                                 @NonNull Argument<? super Map.Entry<?, ?>> type,
                                                                 @NonNull FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return new MapEntryPojoDeserializer<>(context, type);
        }
        return createSpecific(context, type);
    }

    @Override
    public void serialize(@NonNull Encoder encoder,
                          @NonNull EncoderContext context,
                          @NonNull Argument<? extends Map.Entry<?, ?>> type,
                          Map.Entry<?, ?> value) throws IOException {
        createSpecific(context, type).serialize(encoder, context, type, value);
    }

    @Override
    public Map.Entry<?, ?> deserialize(@NonNull Decoder decoder,
                                       @NonNull DecoderContext context,
                                       @NonNull Argument<? super Map.Entry<?, ?>> type) throws IOException {
        return createSpecific(context, type).deserialize(decoder, context, type);
    }

    @Override
    public boolean isEmpty(@NonNull EncoderContext context, Map.Entry<?, ?> value) {
        return value == null;
    }

    @Override
    public boolean isAbsent(@NonNull EncoderContext context, Map.Entry<?, ?> value) {
        return value == null;
    }

    @Override
    public Argument<Map.Entry<?, ?>> getType() {
        return (Argument) Argument.of(Map.Entry.class, Argument.ofTypeVariable(Object.class, "K"), Argument.ofTypeVariable(Object.class, "V"));
    }

    @SuppressWarnings("unchecked")
    private static Argument<Object> typeVariable(Argument<?> type, String name, int index) {
        return (Argument<Object>) type.getTypeVariable(name).orElseGet(() -> {
            Argument<?>[] typeParameters = type.getTypeParameters();
            if (typeParameters.length > index) {
                return typeParameters[index];
            }
            return Argument.OBJECT_ARGUMENT;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void encodeNullable(Encoder encoder,
                                           Serializer.EncoderContext context,
                                           Argument<T> argument,
                                           Serializer<T> serializer,
                                           @Nullable Object value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            serializer.serialize(encoder, context, argument, (T) value);
        }
    }

    @Nullable
    private static <T> T decodeNullable(Decoder decoder,
                                        Deserializer.DecoderContext context,
                                        Argument<T> argument,
                                        Deserializer<T> deserializer) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserializer.deserialize(decoder, context, argument);
    }

    private abstract static class AbstractMapEntrySerializer<T extends Map.Entry<?, ?>> implements Serializer<T> {
        final Serializer<T> delegate;
        final Argument<Object> keyArgument;
        final Argument<Object> valueArgument;
        final Serializer<Object> keySerializer;
        final Serializer<Object> valueSerializer;

        @SuppressWarnings({"unchecked", "rawtypes"})
        AbstractMapEntrySerializer(Serializer<T> delegate,
                                   Serializer.EncoderContext context,
                                   Argument<? extends T> type) throws SerdeException {
            this.delegate = delegate;
            this.keyArgument = typeVariable(type, "K", 0);
            this.valueArgument = typeVariable(type, "V", 1);
            this.keySerializer = (Serializer<Object>) context.findSerializer(keyArgument).createSpecific(context, (Argument) keyArgument);
            this.valueSerializer = (Serializer<Object>) context.findSerializer(valueArgument).createSpecific(context, (Argument) valueArgument);
        }

        @Override
        public boolean isEmpty(@NonNull EncoderContext context, @Nullable T value) {
            return delegate.isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(@NonNull EncoderContext context, @Nullable T value) {
            return delegate.isAbsent(context, value);
        }

        @Override
        public boolean isDefault(@NonNull EncoderContext context, @NonNull T value) {
            return delegate.isDefault(context, value);
        }
    }

    private static final class MapEntryPojoSerializer<T extends Map.Entry<?, ?>> extends AbstractMapEntrySerializer<T> {

        private MapEntryPojoSerializer(Serializer<T> delegate,
                                       Serializer.EncoderContext context,
                                       Argument<? extends T> type) throws SerdeException {
            super(delegate, context, type);
        }

        @Override
        public void serialize(@NonNull Encoder encoder,
                              @NonNull EncoderContext context,
                              @NonNull Argument<? extends T> type,
                              @NonNull T value) throws IOException {
            try (Encoder objectEncoder = encoder.encodeObject(type)) {
                objectEncoder.encodeKey(MAP_ENTRY_KEY);
                encodeNullable(objectEncoder, context, keyArgument, keySerializer, value.getKey());
                objectEncoder.encodeKey(MAP_ENTRY_VALUE);
                encodeNullable(objectEncoder, context, valueArgument, valueSerializer, value.getValue());
            }
        }
    }

    private static final class MapEntryNaturalSerializer<T extends Map.Entry<?, ?>> extends AbstractMapEntrySerializer<T> {

        private MapEntryNaturalSerializer(Serializer<T> delegate,
                                          Serializer.EncoderContext context,
                                          Argument<? extends T> type) throws SerdeException {
            super(delegate, context, type);
        }

        @Override
        public void serialize(@NonNull Encoder encoder,
                              @NonNull EncoderContext context,
                              @NonNull Argument<? extends T> type,
                              @NonNull T value) throws IOException {
            Object key = value.getKey();
            try (Encoder objectEncoder = encoder.encodeObject(type)) {
                objectEncoder.encodeKey(key == null ? "null" : key.toString());
                encodeNullable(objectEncoder, context, valueArgument, valueSerializer, value.getValue());
            }
        }
    }

    private abstract static sealed class AbstractMapEntryDeserializer<T extends Map.Entry<?, ?>> implements Deserializer<T> {
        final Argument<Object> keyArgument;
        final Argument<Object> valueArgument;
        final Deserializer<Object> keyDeserializer;
        final Deserializer<Object> valueDeserializer;

        @SuppressWarnings({"unchecked", "rawtypes"})
        AbstractMapEntryDeserializer(Deserializer.DecoderContext context,
                                     Argument<?> type) throws SerdeException {
            this.keyArgument = typeVariable(type, "K", 0);
            this.valueArgument = typeVariable(type, "V", 1);
            this.keyDeserializer = (Deserializer<Object>) context.findDeserializer(keyArgument).createSpecific(context, (Argument) keyArgument);
            this.valueDeserializer = (Deserializer<Object>) context.findDeserializer(valueArgument).createSpecific(context, (Argument) valueArgument);
        }
    }

    private static final class MapEntryPojoDeserializer<T extends Map.Entry<?, ?>> extends AbstractMapEntryDeserializer<T> {

        private MapEntryPojoDeserializer(Deserializer.DecoderContext context,
                                         Argument<?> type) throws SerdeException {
            super(context, type);
        }

        @Override
        public T deserialize(@NonNull Decoder decoder,
                             @NonNull DecoderContext context,
                             @NonNull Argument<? super T> type) throws IOException {
            Object key = null;
            Object value = null;
            try (Decoder objectDecoder = decoder.decodeObject(type)) {
                String property;
                while ((property = objectDecoder.decodeKey()) != null) {
                    switch (property) {
                        case MAP_ENTRY_KEY ->
                            key = decodeNullable(objectDecoder, context, keyArgument, keyDeserializer);
                        case MAP_ENTRY_VALUE ->
                            value = decodeNullable(objectDecoder, context, valueArgument, valueDeserializer);
                        default -> objectDecoder.skipValue();
                    }
                }
            }
            return (T) new AbstractMap.SimpleEntry<>(key, value);
        }
    }

    private static final class MapEntryNaturalDeserializer<T extends Map.Entry<?, ?>> extends AbstractMapEntryDeserializer<T> {

        private MapEntryNaturalDeserializer(Deserializer.DecoderContext context,
                                            Argument<?> type) throws SerdeException {
            super(context, type);
        }

        @Override
        public T deserialize(@NonNull Decoder decoder,
                             @NonNull DecoderContext context,
                             @NonNull Argument<? super T> type) throws IOException {
            Decoder objectDecoder = decoder.decodeObject(type);
            String key = objectDecoder.decodeKey();
            if (key == null) {
                objectDecoder.finishStructure();
                return null;
            }
            Object convertedKey;
            try {
                convertedKey = context.getConversionService().convertRequired(key, keyArgument);
            } catch (ConversionErrorException e) {
                throw new SerdeException("Error decoding map entry key of type " + keyArgument + ": " + e.getMessage(), e);
            }
            Object value = decodeNullable(objectDecoder, context, valueArgument, valueDeserializer);
            objectDecoder.finishStructure(true);
            return (T) new AbstractMap.SimpleEntry<>(convertedKey, value);
        }
    }
}
