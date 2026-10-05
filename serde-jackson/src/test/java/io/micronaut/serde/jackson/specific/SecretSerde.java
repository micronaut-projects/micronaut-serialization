package io.micronaut.serde.jackson.specific;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;

/**
 * User-written serdes created with the context and the type, which require the context of the call they serve
 * to be the context they were created with.
 */
public final class SecretSerde {

    private SecretSerde() {
    }

    @Prototype
    public static final class SecretDeserializer implements Deserializer<Secret> {
        private final Deserializer.DecoderContext constructorContext;

        public SecretDeserializer(@Parameter Deserializer.DecoderContext context, @Parameter Argument<?> type) {
            this.constructorContext = context;
        }

        @Override
        public Secret deserialize(Decoder decoder, DecoderContext context, Argument<? super Secret> type) throws IOException {
            if (constructorContext != context) {
                throw new IOException("DecoderContext was not passed to the deserializer constructor");
            }
            return new Secret(decoder.decodeString());
        }
    }

    @Prototype
    public static final class SecretSerializer implements Serializer<Secret> {
        private final Serializer.EncoderContext constructorContext;

        public SecretSerializer(@Parameter Serializer.EncoderContext context, @Parameter Argument<?> type) {
            this.constructorContext = context;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Secret> type, Secret value) throws IOException {
            if (constructorContext != context) {
                throw new IOException("EncoderContext was not passed to the serializer constructor");
            }
            encoder.encodeString(value.value());
        }
    }
}
