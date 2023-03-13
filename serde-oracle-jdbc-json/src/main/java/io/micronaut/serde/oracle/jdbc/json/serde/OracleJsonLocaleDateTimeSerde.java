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
import io.micronaut.serde.support.serdes.LocalDateTimeSerde;
import io.micronaut.serde.util.NullableSerde;
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
public class OracleJsonLocaleDateTimeSerde extends OracleJsonTemporalSerde<LocalDateTime> {
    private final LocalDateTimeSerde dateTimeSerde;

    public OracleJsonLocaleDateTimeSerde(LocalDateTimeSerde dateTimeSerde) {
        this.dateTimeSerde = dateTimeSerde;
    }

    @Override
    protected NullableSerde<LocalDateTime> getDefault() {
        return dateTimeSerde;
    }
}
