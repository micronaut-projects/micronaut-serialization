package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * The custom deserializer for {@link OffsetDateTime} for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Secondary
public class OracleJsonOffsetDateTimeSerde extends OracleJsonTypeToStringSerializer<OffsetDateTime> implements NullableSerde<OffsetDateTime> {

    @Override
    public OffsetDateTime deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super OffsetDateTime> type) throws IOException {
        String dateStr = decoder.decodeString();
        return OffsetDateTime.parse(dateStr);
    }
}
