/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.support.serdes;

import io.micronaut.serde.Serde;

import java.time.Duration;
import java.time.Period;

/**
 * Factory class for core serdes.
 */
public class CoreSerdes {

    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Duration> DURATION_SERDE = new DurationSerde();
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Period> PERIOD_SERDE = new PeriodSerde();
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<CharSequence> CHAR_SEQUENCE_SERDE = new CharSequenceSerde();

}
