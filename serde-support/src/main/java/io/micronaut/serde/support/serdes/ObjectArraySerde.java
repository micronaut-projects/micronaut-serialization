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
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.SpecificOnlyDeserializer;
import io.micronaut.serde.util.SpecificOnlySerializer;

/**
 * Deserializer for object arrays.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ObjectArraySerde implements Serde<Object[]>, SpecificOnlySerializer<Object[]>, SpecificOnlyDeserializer<Object[]> {
    @Override
    public Deserializer<Object[]> createSpecific(DecoderContext context, Argument<? super Object[]> type)
            throws SerdeException {

        final Argument<Object> componentType =  Argument.of((Class<Object>) type.getType().getComponentType());
        final Deserializer<?> deserializer = context.findDeserializer(componentType).createSpecific(context, componentType);
        return new SpecificObjectDeserializerArraySerde(componentType, deserializer);
    }

    @Override
    public Serializer<Object[]> createSpecific(EncoderContext context, Argument<? extends Object[]> type) throws SerdeException {
        final Argument<Object> componentType =  Argument.of((Class<Object>) type.getType().getComponentType());
        final Serializer<? super Object> serializer = context.findSerializer(componentType).createSpecific(context, componentType);
        return new SpecificObjectSerializerArraySerde(componentType, serializer);
    }
}
