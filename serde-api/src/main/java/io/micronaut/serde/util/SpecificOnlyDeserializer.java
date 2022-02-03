package io.micronaut.serde.util;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;

/**
 * The type of deserializer that requires a specific implementation by calling {@link #createSpecific(DecoderContext, Argument)}.
 *
 * @param <T> The deserializer type
 * @author Denis Stepanov
 */
public interface SpecificOnlyDeserializer<T> extends Deserializer<T> {

    @Override
    default T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
        throw new IllegalStateException("Specific deserializer required!");
    }

    @Override
    default boolean allowNull() {
        throw new IllegalStateException("Specific deserializer required!");
    }

    @Override
    default T getDefaultValue(DecoderContext context, Argument<? super T> type) {
        throw new IllegalStateException("Specific deserializer required!");
    }
}
