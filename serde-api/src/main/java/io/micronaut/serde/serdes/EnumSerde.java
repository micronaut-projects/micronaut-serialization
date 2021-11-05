package io.micronaut.serde.serdes;

import java.io.IOException;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

/**
 * Serde for handling enums.
 * @param <E> The enum type.
 * @since 1.0.0
 */
@Singleton
final class EnumSerde<E extends Enum<E>> implements Serde<Enum<E>> {

    @SuppressWarnings("unchecked")
    @Override
    public Enum<E> deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Enum<E>> type)
            throws IOException {
        @SuppressWarnings("rawtypes") final Class type1 = type.getType();
        return Enum.valueOf(type1, decoder.decodeString());
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Enum<E> value, Argument<? extends Enum<E>> type)
            throws IOException {
        encoder.encodeString(value.name());
    }
}
