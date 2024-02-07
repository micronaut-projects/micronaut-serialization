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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.util.CustomizableSerializer;

import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

@Internal
final class StreamSerializer<T> implements CustomizableSerializer<Stream<T>>, SerializerRegistrar<Stream<T>> {

    @Override
    public Serializer<Stream<T>> createSpecific(EncoderContext context, Argument<? extends Stream<T>> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot serialize raw stream");
        }
        final Argument generic = generics[0];
        final Serializer componentSerializer = context.findSerializer(generic).createSpecific(context, type);
        return new Serializer<Stream<T>>() {
            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Stream<T>> type, Stream<T> value) throws IOException {
                if (value == null) {
                    throw new SerdeException("Stream is required");
                }
                Encoder arrayEncoder = encoder.encodeArray(type);
                Iterator<T> itr = value.iterator();
                while (itr.hasNext()) {
                    componentSerializer
                        .serialize(
                            arrayEncoder,
                            context, generic, itr.next()
                        );
                }
                arrayEncoder.finishStructure();
            }
        };
    }

    @Override
    public Argument<Stream<T>> getType() {
        return (Argument) Argument.of(Stream.class, Argument.ofTypeVariable(Object.class, "T"));
    }
}
