/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.SerdeRegistrar;

import java.util.List;
import java.util.function.Consumer;

/**
 * Internal Serdes registrar.
 *
 * @author Denis Stepanov
 * @since 2.9.0
 */
@Internal
public final class Serdes {

    public static final IntegerSerde INTEGER_SERDE = new IntegerSerde();
    public static final LongSerde LONG_SERDE = new LongSerde();
    public static final ShortSerde SHORT_SERDE = new ShortSerde();
    public static final FloatSerde FLOAT_SERDE = new FloatSerde();
    public static final ByteSerde BYTE_SERDE = new ByteSerde();
    public static final DoubleSerde DOUBLE_SERDE = new DoubleSerde();
    public static final OptionalIntSerde OPTIONAL_INT_SERDE = new OptionalIntSerde();
    public static final OptionalDoubleSerde OPTIONAL_DOUBLE_SERDE = new OptionalDoubleSerde();
    public static final OptionalLongSerde OPTIONAL_LONG_SERDE = new OptionalLongSerde();
    public static final BigDecimalSerde BIG_DECIMAL_SERDE = new BigDecimalSerde();
    public static final BigIntegerSerde BIG_INTEGER_SERDE = new BigIntegerSerde();
    public static final UUIDSerde UUID_SERDE = new UUIDSerde();
    public static final URLSerde URL_SERDE = new URLSerde();
    public static final URISerde URI_SERDE = new URISerde();
    public static final CharsetSerde CHARSET_SERDE = new CharsetSerde();
    public static final TimeZoneSerde TIME_ZONE_SERDE = new TimeZoneSerde();
    public static final LocaleSerde LOCALE_SERDE = new LocaleSerde();
    public static final IntArraySerde INT_ARRAY_SERDE = new IntArraySerde();
    public static final LongArraySerde LONG_ARRAY_SERDE = new LongArraySerde();
    public static final FloatArraySerde FLOAT_ARRAY_SERDE = new FloatArraySerde();
    public static final ShortArraySerde SHORT_ARRAY_SERDE = new ShortArraySerde();
    public static final DoubleArraySerde DOUBLE_ARRAY_SERDE = new DoubleArraySerde();
    public static final BooleanArraySerde BOOLEAN_ARRAY_SERDE = new BooleanArraySerde();
    public static final ByteArraySerde BYTE_ARRAY_SERDE = new ByteArraySerde(true);
    public static final CharArraySerde CHAR_ARRAY_SERDE = new CharArraySerde();

    public static final StringSerde STRING_SERDE = new StringSerde();

    public static final BooleanSerde BOOLEAN_SERDE = new BooleanSerde();
    public static final CharSerde CHAR_SERDE = new CharSerde();
    public static final List<SerdeRegistrar<?>> LEGACY_DEFAULT_SERDES = List.of(
        BOOLEAN_SERDE,
        BYTE_SERDE,
        CHAR_SERDE,
        DOUBLE_SERDE,
        FLOAT_SERDE,
        INTEGER_SERDE,
        LONG_SERDE,
        SHORT_SERDE,
        STRING_SERDE,
        OPTIONAL_INT_SERDE,
        OPTIONAL_DOUBLE_SERDE,
        OPTIONAL_LONG_SERDE,
        BIG_DECIMAL_SERDE,
        BIG_INTEGER_SERDE,
        UUID_SERDE,
        URL_SERDE,
        URI_SERDE,
        CHARSET_SERDE,
        TIME_ZONE_SERDE,
        LOCALE_SERDE,
        INT_ARRAY_SERDE,
        LONG_ARRAY_SERDE,
        FLOAT_ARRAY_SERDE,
        SHORT_ARRAY_SERDE,
        DOUBLE_ARRAY_SERDE,
        BOOLEAN_ARRAY_SERDE,
        CHAR_ARRAY_SERDE
    );

    private static final List<SerdeRegistrar<?>> SERDES = List.of(
        new DurationSerde(),
        new JsonNodeSerde(),
        new PeriodSerde(),
        new ByteBufferSerde(),
        new StringArraySerde(),
        new OptionalSerde<>()
    );

    public static void register(SerdeConfiguration serdeConfiguration,
                                SerdeIntrospections introspections,
                                Consumer<SerdeRegistrar<?>> consumer) {
        LEGACY_DEFAULT_SERDES.forEach(consumer);
        consumer.accept(new ByteArraySerde(serdeConfiguration));
        SERDES.forEach(consumer);
        InstantSerde instantSerde = new InstantSerde(serdeConfiguration);
        consumer.accept(instantSerde);
        consumer.accept(new DateSerde(instantSerde));
        LocalDateSerde localDateSerde = new LocalDateSerde(serdeConfiguration);
        consumer.accept(localDateSerde);
        consumer.accept(new LocalTimeSerde(serdeConfiguration));
        consumer.accept(new LocalDateTimeSerde(serdeConfiguration));
        consumer.accept(new OffsetDateTimeSerde(serdeConfiguration));
        consumer.accept(new SqlDateSerde(localDateSerde));
        consumer.accept(new SqlTimestampSerde(instantSerde));
        consumer.accept(new YearSerde());
        consumer.accept(new ZonedDateTimeSerde(serdeConfiguration));
        consumer.accept(new EnumSerde<>(introspections));
        consumer.accept(new InetAddressSerde(serdeConfiguration));
    }

}
