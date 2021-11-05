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
package io.micronaut.serde.serializers;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;

/**
 * Number serializer that handles formatting.
 * @param <N> The number type
 */
public interface NumberSerde<N extends Number> extends Serde<N>, NullableSerde<N> {

    @Override
    default Deserializer<N> createSpecific(Argument<? super N> context, DecoderContext decoderContext) throws SerdeException {
        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        final String pattern = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            return new FormattedNumberSerde<>(pattern, annotationMetadata);
        }
        return this;
    }

    @Override
    default Serializer<N> createSpecific(Argument<? extends N> type, EncoderContext encoderContext) {
        final AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        final String pattern = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            return new FormattedNumberSerde<>(pattern, annotationMetadata);
        }
        return this;
    }
}
