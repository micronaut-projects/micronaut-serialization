package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Duration;

/**
 * The custom serde for {@link Duration} for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Secondary
public class OracleJsonDurationSerde extends OracleJsonTypeToStringSerializer<Duration> implements NullableSerde<Duration> {

    @Override
    public Duration deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Duration> type) throws IOException {
        String duration = decoder.decodeString();
        return Duration.parse(duration);
    }
}
