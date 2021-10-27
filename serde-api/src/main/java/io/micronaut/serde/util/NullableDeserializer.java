package io.micronaut.serde.util;

import java.io.IOException;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

@FunctionalInterface
public interface NullableDeserializer<T> extends Deserializer<T> {
    @Override
    default T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        } else {
            return deserializeNonNull(decoder, decoderContext, type);
        }
    }

    /**
     * A method that is invoked when the value is known not to be null.
     * @param decoder The decoder
     * @param decoderContext The decoder context
     * @param type The type
     * @return The value
     * @throws IOException if something goes wrong during deserialization
     */
    @NonNull
    T deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException;

    @Override
    default boolean allowNull() {
        return true;
    }
}
