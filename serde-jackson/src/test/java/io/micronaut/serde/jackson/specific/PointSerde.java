package io.micronaut.serde.jackson.specific;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.CacheableSpecificSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton serdes that create their specific serdes, like the generated factories, and allow caching them.
 * A point is written as "x,y", or with the format pattern as separator.
 */
public final class PointSerde {

    public static final AtomicInteger DESERIALIZERS = new AtomicInteger();
    public static final AtomicInteger SERIALIZERS = new AtomicInteger();

    private PointSerde() {
    }

    @Singleton
    @CacheableSpecificSerde
    public static final class PointDeserializer implements FormattedDeserializer<Point> {

        @Override
        public Deserializer<Point> createSpecific(DecoderContext context, Argument<? super Point> type) {
            return deserializer(",");
        }

        @Override
        public Deserializer<Point> createSpecific(DecoderContext context, Argument<? super Point> type, FormatConfiguration format) {
            return deserializer(format.pattern() == null ? "," : format.pattern());
        }

        @Override
        public Point deserialize(Decoder decoder, DecoderContext context, Argument<? super Point> type) throws IOException {
            return createSpecific(context, type).deserialize(decoder, context, type);
        }

        private static Deserializer<Point> deserializer(String separator) {
            DESERIALIZERS.incrementAndGet();
            return (decoder, context, type) -> {
                String[] parts = decoder.decodeString().split(separator);
                return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            };
        }
    }

    @Singleton
    @CacheableSpecificSerde
    public static final class PointSerializer implements FormattedSerializer<Point> {

        @Override
        public Serializer<Point> createSpecific(EncoderContext context, Argument<? extends Point> type) {
            return serializer(",");
        }

        @Override
        public Serializer<Point> createSpecific(EncoderContext context, Argument<? extends Point> type, FormatConfiguration format) {
            return serializer(format.pattern() == null ? "," : format.pattern());
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Point> type, Point value) throws IOException {
            createSpecific(context, type).serialize(encoder, context, type, value);
        }

        private static Serializer<Point> serializer(String separator) {
            SERIALIZERS.incrementAndGet();
            return (Encoder encoder, Serializer.EncoderContext context, Argument<? extends Point> type, Point value) ->
                encoder.encodeString(value.x() + separator + value.y());
        }
    }
}
