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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

/**
 * Interface for serializing and deserializing temporals.
 *
 * @param <T> The generic type
 */
public interface TemporalSerde<T extends TemporalAccessor> extends Serde<T> {
    ZoneId UTC = ZoneId.of(ZoneOffset.UTC.getId());

    @Override
    default Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) {
        final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        final String pattern = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            return new FormattedTemporalSerde<>(pattern, annotationMetadata, query(), this);
        }
        return this;

    }

    @Override
    default Deserializer<T> createSpecific(DecoderContext decoderContext, Argument<? super T> context) throws SerdeException {
        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        final String pattern = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            return new FormattedTemporalSerde<>(pattern, annotationMetadata, query(), this);
        }
        return this;
    }

    /**
     * @return The temporal query for the type.
     */
    @NonNull
    TemporalQuery<T> query();
}
