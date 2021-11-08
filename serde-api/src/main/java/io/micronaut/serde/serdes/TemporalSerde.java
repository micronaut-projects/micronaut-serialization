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
package io.micronaut.serde.serdes;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;

/**
 * Interface for serializing and deserializing temporals.
 *
 * @param <T> The generic type
 */
public interface TemporalSerde<T extends TemporalAccessor> extends NullableSerde<T> {
    ZoneId UTC = ZoneId.of(ZoneOffset.UTC.getId());

    @Override
    default Serializer<T> createSpecific(Argument<? extends T> type, EncoderContext encoderContext) {
        final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        final String pattern = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            return new FormattedTemporalSerde<>(pattern, annotationMetadata, query());
        }
        return this;

    }

    @Override
    default Deserializer<T> createSpecific(Argument<? super T> context, DecoderContext decoderContext) throws SerdeException {
        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        final String pattern = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            return new FormattedTemporalSerde<>(pattern, annotationMetadata, query());
        }
        return this;
    }

    /**
     * @return The temporal query for the type.
     */
    @NonNull
    TemporalQuery<T> query();
}
