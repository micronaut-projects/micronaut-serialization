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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.oracle.jdbc.json.annotation.OracleType;
import io.micronaut.serde.support.serdes.LocalDateSerde;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Serde for {@link LocalDate} from Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonLocaleDateSerde extends OracleJsonTemporalSerde<LocalDate> {

    private final LocalDateSerde localDateSerde;

    public OracleJsonLocaleDateSerde(LocalDateSerde localDateSerde) {
        this.localDateSerde = localDateSerde;
    }

    @Override
    @NonNull
    protected LocalDate doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder,
                                           @NonNull DecoderContext decoderContext,
                                           @NonNull Argument<? super LocalDate> type) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.TEMPORAL) {
            LocalDateTime localDateTime = (LocalDateTime) decoder.decodeTemporal();
            return localDateTime.toLocalDate();
        } else {
            return getDefault().deserializeNonNull(
                decoder,
                decoderContext,
                type
            );
        }
    }

    @Override
    protected NullableSerde<LocalDate> getDefault() {
        return localDateSerde;
    }
}
