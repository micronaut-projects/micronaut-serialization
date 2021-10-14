package io.micronaut.json.generated.serializer;

import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URL;

@Singleton
class UrlSerializer implements Serializer<URL>, Deserializer<URL> {
    @Override
    public URL deserialize(Decoder decoder) throws IOException {
        return new URL(decoder.decodeString());
    }

    @Override
    public void serialize(Encoder encoder, URL value) throws IOException {
        encoder.encodeString(value.toString());
    }
}
