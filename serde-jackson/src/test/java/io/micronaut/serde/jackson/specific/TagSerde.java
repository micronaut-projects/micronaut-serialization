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
 * Serdes created with the context and the type, like the build-time generated serdes. A tag is written as its name
 * prefixed with "#", or with the format pattern.
 */
public final class TagSerde {

    public static final AtomicInteger DESERIALIZERS = new AtomicInteger();
    public static final AtomicInteger SERIALIZERS = new AtomicInteger();

    private TagSerde() {
    }

    @Prototype
    @CacheableSpecificSerde
    public static final class TagDeserializer implements CustomizableDeserializer<Tag>, FormattedDeserializer<Tag> {

        public TagDeserializer(@Parameter Deserializer.DecoderContext context, @Parameter Argument<?> type) {
            DESERIALIZERS.incrementAndGet();
        }

        @Override
        public Deserializer<Tag> createSpecific(DecoderContext context, Argument<? super Tag> type) {
            return deserializer("#");
        }

        @Override
        public Deserializer<Tag> createSpecific(DecoderContext context, Argument<? super Tag> type, FormatConfiguration format) {
            return deserializer(format.pattern() == null ? "#" : format.pattern());
        }

        private static Deserializer<Tag> deserializer(String prefix) {
            return (decoder, context, type) -> new Tag(decoder.decodeString().substring(prefix.length()));
        }
    }

    @Prototype
    @CacheableSpecificSerde
    public static final class TagSerializer implements CustomizableSerializer<Tag>, FormattedSerializer<Tag> {

        public TagSerializer(@Parameter Serializer.EncoderContext context, @Parameter Argument<?> type) {
            SERIALIZERS.incrementAndGet();
        }

        @Override
        public Serializer<Tag> createSpecific(EncoderContext context, Argument<? extends Tag> type) {
            return serializer("#");
        }

        @Override
        public Serializer<Tag> createSpecific(EncoderContext context, Argument<? extends Tag> type, FormatConfiguration format) {
            return serializer(format.pattern() == null ? "#" : format.pattern());
        }

        private static Serializer<Tag> serializer(String prefix) {
            return (Encoder encoder, Serializer.EncoderContext context, Argument<? extends Tag> type, Tag value) ->
                encoder.encodeString(prefix + value.name());
        }
    }
}
