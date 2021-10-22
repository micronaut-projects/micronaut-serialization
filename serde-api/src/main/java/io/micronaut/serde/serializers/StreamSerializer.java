package io.micronaut.serde.serializers;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

@Singleton
final class StreamSerializer<T> implements Serializer<Stream<T>> {
    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context, Stream<T> value, Argument<? extends Stream<T>> type,
                          Argument<?>... generics) throws IOException {
        if (value == null) {
            throw new SerdeException("Stream is required");
        }
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot serialize raw stream");
        }
        final Argument generic = generics[0];
        final Serializer componentSerializer = context.findSerializer(generic);

        Encoder arrayEncoder = encoder.encodeArray();
        Iterator<T> itr = value.iterator();
        while (itr.hasNext()) {
            componentSerializer
                    .serialize(
                            encoder,
                            context, itr.next(), generic,
                            generic.getTypeParameters()
                    );
        }
        arrayEncoder.finishStructure();
    }


}
