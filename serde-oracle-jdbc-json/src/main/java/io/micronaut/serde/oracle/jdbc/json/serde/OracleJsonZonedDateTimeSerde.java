/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serde;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import jakarta.inject.Singleton;

import java.time.ZonedDateTime;

/**
 * Serde for {@link ZonedDateTime} from Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonZonedDateTimeSerde extends AbstractOracleJsonSerde<ZonedDateTime> {
    private final Serde<ZonedDateTime> zonedDateTimeSerde;

    public OracleJsonZonedDateTimeSerde(@Secondary Serde<ZonedDateTime> zonedDateTimeSerde) {
        this.zonedDateTimeSerde = zonedDateTimeSerde;
    }

    @Override
    protected ZonedDateTime doDeserializeNonNull(OracleJdbcJsonParserDecoder decoder, DecoderContext decoderContext, Argument<? super ZonedDateTime> type) {
        return decoder.decodeZonedDateTime();
    }

    @Override
    protected void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends ZonedDateTime> type, ZonedDateTime value) {
        encoder.encodeOffsetDateTime(value.toOffsetDateTime());
    }

    @Override
    protected Serde<ZonedDateTime> getDefault() {
        return zonedDateTimeSerde;
    }
}
