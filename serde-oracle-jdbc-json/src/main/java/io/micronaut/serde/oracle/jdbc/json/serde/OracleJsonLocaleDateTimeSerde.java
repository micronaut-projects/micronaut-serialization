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
import io.micronaut.serde.support.serdes.LocalDateTimeSerde;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;

/**
 * Serde for {@link LocalDateTime} from Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonLocaleDateTimeSerde extends AbstractOracleJsonSerde<LocalDateTime> {
    private final LocalDateTimeSerde dateTimeSerde;

    public OracleJsonLocaleDateTimeSerde(LocalDateTimeSerde dateTimeSerde) {
        this.dateTimeSerde = dateTimeSerde;
    }

    @Override
    protected LocalDateTime doDeserializeNonNull(OracleJdbcJsonParserDecoder decoder, DecoderContext decoderContext, Argument<? super LocalDateTime> type) {
        return decoder.decodeLocalDateTime();
    }

    @Override
    protected void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends LocalDateTime> type, LocalDateTime value) {
        encoder.encodeLocalDateTime(value);
    }

    @Override
    protected Serde<LocalDateTime> getDefault() {
        return dateTimeSerde;
    }
}
