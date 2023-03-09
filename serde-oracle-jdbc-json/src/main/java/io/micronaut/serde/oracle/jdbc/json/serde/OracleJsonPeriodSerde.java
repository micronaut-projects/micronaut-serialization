package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Period;

/**
 * The custom serde for {@link Period} for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Secondary
public class OracleJsonPeriodSerde extends OracleJsonTypeToStringSerializer<Period> implements NullableSerde<Period> {

    @Override
    public Period deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Period> type) throws IOException {
        String period = decoder.decodeString();
        return Period.parse(period);
    }
}
