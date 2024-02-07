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

import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serde;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.support.serdes.OffsetDateTimeSerde;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;

/**
 * Serde for {@link OffsetDateTime} from Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonOffsetDateTimeSerde extends AbstractOracleJsonSerde<OffsetDateTime> {
    private final OffsetDateTimeSerde offsetDateTimeSerde;

    public OracleJsonOffsetDateTimeSerde(OffsetDateTimeSerde offsetDateTimeSerde) {
        this.offsetDateTimeSerde = offsetDateTimeSerde;
    }

    @Override
    protected OffsetDateTime doDeserializeNonNull(OracleJdbcJsonParserDecoder decoder, DecoderContext decoderContext, Argument<? super OffsetDateTime> type) {
        return decoder.decodeOffsetDateTime();
    }

    @Override
    protected void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends OffsetDateTime> type, OffsetDateTime value) {
        encoder.encodeOffsetDateTime(value);
    }

    @Override
    protected Serde<OffsetDateTime> getDefault() {
        return offsetDateTimeSerde;
    }
}
