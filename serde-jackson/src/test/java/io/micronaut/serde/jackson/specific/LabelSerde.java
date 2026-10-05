package io.micronaut.serde.jackson.specific;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton serdes that create their specific serdes without allowing them to be cached.
 */
public final class LabelSerde {

    public static final AtomicInteger DESERIALIZERS = new AtomicInteger();
    public static final AtomicInteger SERIALIZERS = new AtomicInteger();

    private LabelSerde() {
    }

    @Singleton
    public static final class LabelDeserializer implements Deserializer<Label> {

        @Override
        public Deserializer<Label> createSpecific(DecoderContext context, Argument<? super Label> type) {
            DESERIALIZERS.incrementAndGet();
            return (decoder, ctx, t) -> new Label(decoder.decodeString());
        }

        @Override
        public Label deserialize(Decoder decoder, DecoderContext context, Argument<? super Label> type) throws IOException {
            return createSpecific(context, type).deserialize(decoder, context, type);
        }
    }

    @Singleton
    public static final class LabelSerializer implements Serializer<Label> {

        @Override
        public Serializer<Label> createSpecific(EncoderContext context, Argument<? extends Label> type) {
            SERIALIZERS.incrementAndGet();
            return (Encoder encoder, Serializer.EncoderContext ctx, Argument<? extends Label> t, Label value) ->
                encoder.encodeString(value.text());
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Label> type, Label value) throws IOException {
            createSpecific(context, type).serialize(encoder, context, type, value);
        }
    }
}
