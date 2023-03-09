package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * The custom serde for {@link LocalDateTime} for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Secondary
public class OracleJsonLocaleDateTimeSerde extends OracleJsonTypeToStringSerializer<LocalDateTime> implements NullableSerde<LocalDateTime> {

    @Override
    public LocalDateTime deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super LocalDateTime> type) throws IOException {
        String dateStr = decoder.decodeString();
        return LocalDateTime.parse(dateStr);
    }
}
