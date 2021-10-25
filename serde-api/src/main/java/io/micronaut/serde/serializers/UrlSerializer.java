package io.micronaut.serde.serializers;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URL;

@Singleton
final class UrlSerializer implements Serializer<URL>, Deserializer<URL> {
    @Override
    public URL deserialize(Decoder decoder,
                           DecoderContext decoderContext,
                           Argument<? super URL> type,
                           Argument<?>... generics)
            throws IOException {
        return new URL(decoder.decodeString());
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context, URL value,
                          Argument<? extends URL> type) throws IOException {
        encoder.encodeString(value.toString());
    }
}
