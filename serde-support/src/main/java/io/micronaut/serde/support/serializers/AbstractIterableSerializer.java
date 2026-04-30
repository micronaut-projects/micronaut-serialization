/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.serdes.SingleElementArraySerde;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;
import org.jspecify.annotations.NonNull;

/**
 * Base serializer for iterable types that can react to array-related format features.
 *
 * @param <T> The item type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
abstract sealed class AbstractIterableSerializer<T> implements FormattedSerializer<Iterable<T>>
    permits CustomizedIterableSerializer, RuntimeValueIterableSerializer, StringIterableSerializer {

    @Override
    public @NonNull Serializer<Iterable<T>> createSpecific(@NonNull EncoderContext context,
                                                           @NonNull Argument<? extends Iterable<T>> type,
                                                           @NonNull FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return ObjectShapeSerdeHelper.objectSerializer(context, type);
        }
        return SingleElementArraySerde.writeSingleElementArraysUnwrapped(createSpecific(context, type), context);
    }
}
