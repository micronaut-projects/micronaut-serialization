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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.FormattedHelper;
import io.micronaut.serde.util.CustomizableDeserializer;
import io.micronaut.serde.util.CustomizableSerializer;
import org.jspecify.annotations.NonNull;

/**
 * Deserializer for object arrays.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ObjectArraySerde implements FormattedSerde<Object[]>, SerdeRegistrar<Object[]>, CustomizableSerializer<Object[]>, CustomizableDeserializer<Object[]> {

    @Override
    public Deserializer<Object[]> createSpecific(DecoderContext context, Argument<? super Object[]> type)
            throws SerdeException {
        final Argument<Object> componentType = Argument.of((Class<Object>) type.getType().getComponentType());
        final Deserializer<?> deserializer = context.findDeserializer(componentType).createSpecific(context, componentType);
        return SingleElementArraySerde.acceptSingleValueAsArray(new CustomizedObjectArrayDeserializer(componentType, deserializer), context);
    }

    @Override
    public Serializer<Object[]> createSpecific(EncoderContext context, Argument<? extends Object[]> type) throws SerdeException {
        final Argument<Object> componentType = Argument.of((Class<Object>) type.getType().getComponentType());
        final Serializer<? super Object> serializer = context.findSerializer(componentType).createSpecific(context, componentType);
        return SingleElementArraySerde.writeSingleElementArraysUnwrapped(new CustomizedObjectArraySerializer(componentType, serializer), context);
    }

    @Override
    public @NonNull Serializer<Object[]> createSpecific(@NonNull EncoderContext context,
                                                        @NonNull Argument<? extends Object[]> type,
                                                        @NonNull FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return FormattedHelper.objectSerializer(context, type);
        }
        return createSpecific(context, type);
    }

    @Override
    public @NonNull Deserializer<Object[]> createSpecific(@NonNull DecoderContext context,
                                                          @NonNull Argument<? super Object[]> type,
                                                          @NonNull FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return FormattedHelper.objectDeserializer(context, type);
        }
        return createSpecific(context, type);
    }

    @Override
    public Argument<Object[]> getType() {
        return Argument.of(Object[].class);
    }
}
