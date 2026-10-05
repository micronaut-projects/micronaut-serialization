package io.micronaut.serde.jackson.specific;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.CacheableSpecificSerde;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.util.CustomizableDeserializer;
import io.micronaut.serde.util.CustomizableSerializer;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serdes created with the context and the type, like the build-time generated serdes. A point is written as "x,y",
 * or with the format pattern as separator.
 */
public final class PointSerde {

    public static final AtomicInteger DESERIALIZERS = new AtomicInteger();
    public static final AtomicInteger SERIALIZERS = new AtomicInteger();

    private PointSerde() {
    }

    @Prototype
    @CacheableSpecificSerde
    public static final class PointDeserializer implements CustomizableDeserializer<Point>, FormattedDeserializer<Point> {

        public PointDeserializer(@Parameter Deserializer.DecoderContext context, @Parameter Argument<?> type) {
            DESERIALIZERS.incrementAndGet();
        }

        @Override
        public Deserializer<Point> createSpecific(DecoderContext context, Argument<? super Point> type) {
            return deserializer(",");
        }

        @Override
        public Deserializer<Point> createSpecific(DecoderContext context, Argument<? super Point> type, FormatConfiguration format) {
            return deserializer(format.pattern() == null ? "," : format.pattern());
        }

        private static Deserializer<Point> deserializer(String separator) {
            return (decoder, context, type) -> {
                String[] parts = decoder.decodeString().split(separator);
                return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            };
        }
    }

    @Prototype
    @CacheableSpecificSerde
    public static final class PointSerializer implements CustomizableSerializer<Point>, FormattedSerializer<Point> {

        public PointSerializer(@Parameter Serializer.EncoderContext context, @Parameter Argument<?> type) {
            SERIALIZERS.incrementAndGet();
        }

        @Override
        public Serializer<Point> createSpecific(EncoderContext context, Argument<? extends Point> type) {
            return serializer(",");
        }

        @Override
        public Serializer<Point> createSpecific(EncoderContext context, Argument<? extends Point> type, FormatConfiguration format) {
            return serializer(format.pattern() == null ? "," : format.pattern());
        }

        private static Serializer<Point> serializer(String separator) {
            return (Encoder encoder, Serializer.EncoderContext context, Argument<? extends Point> type, Point value) ->
                encoder.encodeString(value.x() + separator + value.y());
        }
    }
}
