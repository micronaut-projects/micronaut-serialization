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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.NonNull;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

/**
 * Interface for serializing and deserializing temporals.
 *
 * @param <T> The generic type
 */
public interface TemporalSerde<T extends TemporalAccessor> extends Serde<T>, FormattedSerde<T> {
    ZoneId UTC = ZoneId.of(ZoneOffset.UTC.getId());

    @Override
    default Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    default Serializer<T> createSpecific(EncoderContext context,
                                         Argument<? extends T> type,
                                         @NonNull FormatConfiguration format) {
        return format.createDateTimeFormatter()
            .<Serializer<T>>map(formatter -> new FormattedTemporalSerde<>(formatter, format, query(), this, false))
            .orElse(this);

    }

    @Override
    default Deserializer<T> createSpecific(DecoderContext decoderContext, Argument<? super T> context) throws SerdeException {
        decoderContext = SerdeFeatures.withFeatures(decoderContext, context.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(context.getAnnotationMetadata());
        return format == null ? this : createSpecific(decoderContext, context, format);
    }

    @Override
    default Deserializer<T> createSpecific(DecoderContext decoderContext,
                                           Argument<? super T> context,
                                           @NonNull FormatConfiguration format) throws SerdeException {
        boolean adjustDatesToContextTimeZone = decoderContext.getFeatures()
            .contains(DeserializationConfiguration.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        return format.createDateTimeFormatter()
            .<Deserializer<T>>map(formatter -> new FormattedTemporalSerde<>(formatter, format, query(), this, adjustDatesToContextTimeZone))
            .orElse(this);
    }

    /**
     * @return The temporal query for the type.
     */
    @NonNull
    TemporalQuery<T> query();
}
