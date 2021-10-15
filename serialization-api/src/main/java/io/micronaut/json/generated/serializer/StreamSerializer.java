package io.micronaut.json.generated.serializer;

import io.micronaut.core.type.Argument;
import io.micronaut.json.ArgumentResolver;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerdeRegistry;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

final class StreamSerializer<T> implements Serializer<Stream<T>> {
    private final Serializer<T> valueSerializer;

    private StreamSerializer(Serializer<T> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Override
    public void serialize(Encoder encoder, Stream<T> value) throws IOException {
        Encoder arrayEncoder = encoder.encodeArray();
        Iterator<T> itr = value.iterator();
        while (itr.hasNext()) {
            valueSerializer.serialize(arrayEncoder, itr.next());
        }
        arrayEncoder.finishStructure();
    }

    @Singleton
    static final class Factory implements Serializer.Factory {
        @Override
        public Argument<Stream> getGenericType() {
            return Argument.of(Stream.class, Argument.ofTypeVariable(Object.class, "T"));
        }

        @Override
        public Serializer<?> newInstance(SerdeRegistry locator, ArgumentResolver getTypeParameter) {
            Serializer<?> valueSerializer = locator.findSerializer(getTypeParameter.apply("T"));
            return new StreamSerializer<>(valueSerializer);
        }
    }

    @Singleton
    static final class RawFactory implements Serializer.Factory {
        @SuppressWarnings("rawtypes")
        @Override
        public Argument<Stream> getGenericType() {
            return Argument.of(Stream.class);
        }

        @Override
        public Serializer<?> newInstance(SerdeRegistry locator, ArgumentResolver getTypeParameter) {
            Serializer<?> valueSerializer = locator.findSerializer(Object.class);
            return new StreamSerializer<>(valueSerializer);
        }
    }
}
