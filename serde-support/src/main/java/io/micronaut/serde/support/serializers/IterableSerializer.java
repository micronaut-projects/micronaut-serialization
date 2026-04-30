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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.support.serdes.SingleElementArraySerde;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;
import io.micronaut.serde.util.CustomizableSerializer;

import static io.micronaut.serde.support.util.SerdeArgumentConf.reconstructGenericWithParentMetadata;

/**
 * A serializer for any iterable.
 * @param <T> The generic type
 */
@Internal
final class IterableSerializer<T> implements CustomizableSerializer<Iterable<T>>, FormattedSerializer<Iterable<T>>, SerializerRegistrar<Iterable<T>> {
    @Override
    public Serializer<Iterable<T>> createSpecific(EncoderContext context, Argument<? extends Iterable<T>> type)
            throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length > 0) {
            // if there are annotations on the collection property we need to combine the annotation metadata with the generic.
            @SuppressWarnings("unchecked") final Argument<T> generic = reconstructGenericWithParentMetadata(type, (Argument<T>) generics[0]);
            if (generic.getType() == String.class) {
                return (Serializer) SingleElementArraySerde.writeSingleElementArraysUnwrapped(StringIterableSerializer.INSTANCE, context);
            }
            Serializer<? super T> componentSerializer = context.findSerializer(generic)
                    .createSpecific(context, generic);
            return SingleElementArraySerde.writeSingleElementArraysUnwrapped(new CustomizedIterableSerializer<>(generic, componentSerializer), context);
        }
        return SingleElementArraySerde.writeSingleElementArraysUnwrapped(new RuntimeValueIterableSerializer<>(), context);
    }

    @Override
    public Serializer<Iterable<T>> createSpecific(EncoderContext context,
                                                           Argument<? extends Iterable<T>> type,
                                                           FormatConfiguration format) throws SerdeException {
        if (format.shape().isPojoShape()) {
            return ObjectShapeSerdeHelper.objectSerializer(context, type);
        }
        Serializer<Iterable<T>> specific = createSpecific(context, type);
        if (specific instanceof FormattedSerializer<Iterable<T>> formattedSerializer) {
            return formattedSerializer.createSpecific(context, type, format);
        }
        return SingleElementArraySerde.writeSingleElementArraysUnwrapped(specific, context);
    }

    @Override
    public Argument<Iterable<T>> getType() {
        return (Argument) Argument.of(Iterable.class, Argument.ofTypeVariable(Object.class, "T"));
    }
}
