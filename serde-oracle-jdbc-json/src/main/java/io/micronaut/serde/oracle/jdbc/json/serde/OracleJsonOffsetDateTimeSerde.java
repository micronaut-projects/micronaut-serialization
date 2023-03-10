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
import io.micronaut.core.order.Ordered;
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
@Order(Ordered.LOWEST_PRECEDENCE)
public class OracleJsonOffsetDateTimeSerde extends OracleJsonTypeToStringSerializer<OffsetDateTime> implements NullableSerde<OffsetDateTime> {

    @Override
    public OffsetDateTime deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super OffsetDateTime> type) throws IOException {
        String dateStr = decoder.decodeString();
        return OffsetDateTime.parse(dateStr);
    }
}
