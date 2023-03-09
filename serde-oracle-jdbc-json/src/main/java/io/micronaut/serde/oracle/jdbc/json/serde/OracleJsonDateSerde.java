package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * The custom serde for {@link Date} for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Secondary
public class OracleJsonDateSerde implements NullableSerde<Date> {

    @Override
    public Date deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Date> type) throws IOException {
        String dateStr = decoder.decodeString();
        return Date.from(LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant());
    }
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Date> type, Date value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            LocalDateTime localDateTime = Instant.ofEpochMilli(value.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
            encoder.encodeString(localDateTime.toString());
        }
    }
}
