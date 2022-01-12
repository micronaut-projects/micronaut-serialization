package io.micronaut.serde.awslambdaevents;

import java.io.IOException;

import com.amazonaws.services.lambda.runtime.serialization.util.SerializeUtil;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;
import org.joda.time.DateTime;

@Singleton
@Requires(classes = DateTime.class)
@TypeHint(typeNames = {"org.joda.time.format.DateTimeFormatter", "org.joda.time.format.ISODateTimeFormat", "org.joda.time.ReadableInstant"})
public class JodaDateTimeSerde implements NullableSerde<DateTime> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, DateTime value, Argument<? extends DateTime> type)
            throws IOException {
        encoder.encodeString(SerializeUtil.serializeDateTime(value, getClass().getClassLoader()));
    }

    @Override
    public DateTime deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super DateTime> type)
            throws IOException {
        return SerializeUtil.deserializeDateTime(DateTime.class, decoder.decodeString());
    }
}
