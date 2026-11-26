package example.retain

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serializer
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

/**
 * A user serializer whose specific serializers keep the context of their document.
 */
@Singleton
class RetainingSerializer implements Serializer<Retained> {

    static final AtomicInteger SPECIFICS = new AtomicInteger()

    @Override
    Serializer<Retained> createSpecific(Serializer.EncoderContext context, Argument<? extends Retained> type) {
        SPECIFICS.incrementAndGet()
        return new Specific(context)
    }

    @Override
    void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends Retained> type, Retained value) {
        encoder.encodeString(value.name)
    }

    static class Specific implements Serializer<Retained> {
        final Serializer.EncoderContext context

        Specific(Serializer.EncoderContext context) {
            this.context = context
        }

        @Override
        void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends Retained> type, Retained value) {
            assert context.is(this.context)
            encoder.encodeString(value.name)
        }
    }
}
