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
package io.micronaut.serde.jsonb;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

/**
 * JSON-B default mapping for arrays of {@link Optional}.
 */
@SuppressWarnings("rawtypes")
@Internal
@Singleton
final class JsonbOptionalArraySerde implements Serializer<Optional[]>, Deserializer<Optional[]>, SerdeRegistrar<Optional[]> {
    private static final Argument<Optional[]> OPTIONAL_ARRAY = Argument.of(Optional[].class);

    @Override
    public Serializer<Optional[]> createSpecific(EncoderContext context, Argument<? extends Optional[]> type) throws SerdeException {
        Argument<Optional> componentType = componentType(type);
        Serializer<? super Optional> componentSerializer = context.findSerializer(componentType).createSpecific(context, componentType);
        return (encoder, encoderContext, arrayType, value) -> {
            Encoder array = encoder.encodeArray(arrayType);
            for (Optional item : value) {
                if (item == null) {
                    array.encodeNull();
                } else {
                    componentSerializer.serialize(array, encoderContext, componentType, item);
                }
            }
            array.finishStructure();
        };
    }

    @Override
    public Deserializer<Optional[]> createSpecific(DecoderContext context, Argument<? super Optional[]> type) throws SerdeException {
        Argument<Optional> componentType = componentType(type);
        Deserializer<? extends Optional> componentDeserializer = context.findDeserializer(componentType).createSpecific(context, componentType);
        return new Deserializer<>() {
            @Override
            public Optional[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Optional[]> arrayType) throws IOException {
                Decoder array = decoder.decodeArray();
                Optional[] values = new Optional[10];
                int index = 0;
                while (array.hasNextArrayValue()) {
                    if (index == values.length) {
                        Optional[] expanded = new Optional[values.length * 2];
                        System.arraycopy(values, 0, expanded, 0, values.length);
                        values = expanded;
                    }
                    values[index++] = componentDeserializer.deserializeNullable(array, decoderContext, componentType);
                }
                array.finishStructure();
                Optional[] result = new Optional[index];
                System.arraycopy(values, 0, result, 0, index);
                return result;
            }

            @Override
            public Optional @Nullable [] deserializeNullable(Decoder decoder, DecoderContext decoderContext, Argument<? super Optional[]> arrayType) throws IOException {
                if (decoder.decodeNull()) {
                    return null;
                }
                return deserialize(decoder, decoderContext, arrayType);
            }
        };
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Optional[]> type, Optional[] value) throws IOException {
        createSpecific(context, type).serialize(encoder, context, type, value);
    }

    @Override
    public Optional[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Optional[]> type) throws IOException {
        return createSpecific(decoderContext, type).deserialize(decoder, decoderContext, type);
    }

    @Override
    public Argument<Optional[]> getType() {
        return OPTIONAL_ARRAY;
    }

    private static Argument<Optional> componentType(Argument<?> type) {
        Argument<?>[] typeParameters = type.getTypeParameters();
        if (typeParameters.length > 0) {
            return Argument.of(Optional.class, typeParameters);
        }
        return Argument.of(Optional.class, Argument.OBJECT_ARGUMENT);
    }
}
