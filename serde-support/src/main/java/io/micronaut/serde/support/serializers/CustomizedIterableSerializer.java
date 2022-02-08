package io.micronaut.serde.support.serializers;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;
import java.util.Collection;

/**
 * Customezed iterable serializer.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 1.0
 */
final class CustomizedIterableSerializer<T> implements Serializer<Iterable<T>> {

    private final Argument<T> generic;
    private final Serializer<? super T> componentSerializer;

    CustomizedIterableSerializer(Argument<T> generic, Serializer<? super T> componentSerializer) {
        this.generic = generic;
        this.componentSerializer = componentSerializer;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Iterable<T>> type, Iterable<T> value)
            throws IOException {
        try (Encoder array = encoder.encodeArray(type)) {
            for (T t : value) {
                if (t == null) {
                    encoder.encodeNull();
                } else {
                    componentSerializer.serialize(array, context, generic, t);
                }
            }
        }
    }

    @Override
    public boolean isEmpty(EncoderContext context, Iterable<T> value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection) {
            return ((Collection<T>) value).isEmpty();
        } else {
            return !value.iterator().hasNext();
        }
    }

    @Override
    public boolean isAbsent(EncoderContext context, Iterable<T> value) {
        return value == null;
    }
}
