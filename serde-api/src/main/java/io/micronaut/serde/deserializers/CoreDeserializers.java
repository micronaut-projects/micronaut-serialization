package io.micronaut.serde.deserializers;

import io.micronaut.context.annotation.Factory;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Singleton;

@Factory
public class CoreDeserializers {

    @Singleton
    Deserializer<String> stringDeserializer() {
        return (decoder, decoderContext, type, generics) -> decoder.decodeString();
    }
}
