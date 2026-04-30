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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;

/**
 * Base serde for array types that can react to array-related format features.
 *
 * @param <T> The array type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
abstract sealed class AbstractArraySerde<T> implements FormattedSerde<T>, SerdeRegistrar<T>
    permits BooleanArraySerde, ByteArraySerde, CharArraySerde, DoubleArraySerde, FloatArraySerde,
    IntArraySerde, LongArraySerde, ShortArraySerde, StringArraySerde {

    @Override
    public Serializer<T> createSpecific(EncoderContext context,
                                        Argument<? extends T> type) throws SerdeException {
        return SingleElementArraySerde.writeSingleElementArraysUnwrapped(this, context);
    }

    @Override
    public Deserializer<T> createSpecific(DecoderContext context,
                                          Argument<? super T> type) throws SerdeException {
        return SingleElementArraySerde.acceptSingleValueAsArray(this, context);
    }

    @Override
    public Serializer<T> createSpecific(EncoderContext context,
                                        Argument<? extends T> type,
                                        FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return ObjectShapeSerdeHelper.objectSerializer(context, type);
        }
        return createSpecific(context, type);
    }

    @Override
    public Deserializer<T> createSpecific(DecoderContext context,
                                          Argument<? super T> type,
                                          FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return ObjectShapeSerdeHelper.objectDeserializer(context, type);
        }
        return createSpecific(context, type);
    }
}
