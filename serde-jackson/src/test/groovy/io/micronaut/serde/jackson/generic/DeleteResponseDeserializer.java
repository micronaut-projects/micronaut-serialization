/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.serde.jackson.generic;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.deserializers.DemuxingObjectDecoder;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;

@Internal
//@Singleton
final class DeleteResponseDeserializer implements Deserializer<DeleteResponse<?>> {

    @Override
    public @Nullable DeleteResponse<?> deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super DeleteResponse<?>> type) throws IOException {
        DeleteResponse deleteResponse;
        try (DemuxingObjectDecoder.PrimedDecoder primed = DemuxingObjectDecoder.prime(decoder)) {
            Decoder kindFinder = primed.decodeObjectNonConsuming(type);
            String kindPropertyName = kindFinder.decodeKey();
            if (!"kind".equalsIgnoreCase(kindPropertyName)) {
                throw new SerdeException("Unknown property [" + kindPropertyName + "] encountered during deserialization of type: " + type);
            }
            String kindPropertyValue = kindFinder.decodeString();
            kindFinder.finishStructure(true);

            if ("Status".equalsIgnoreCase(kindPropertyValue)) {
                Argument<V1Status> statusArgument = Argument.of(V1Status.class);
                Deserializer<? extends V1Status> statusDeserializer = context.findDeserializer(statusArgument).createSpecific(context, statusArgument);
                V1Status objectPropertyValue = statusDeserializer.deserializeNullable(primed, context, statusArgument);
                deleteResponse = new DeleteResponse(null, objectPropertyValue);
            } else {
                Optional<Argument<?>> typeParamOpt = type.getFirstTypeVariable();
                if (typeParamOpt.isEmpty()) {
                    throw new SerdeException("Not found type parameter in type: " + type);
                }
                Argument typeVariable = typeParamOpt.get();
                Deserializer<?> objectDeserializer = context.findDeserializer(typeVariable).createSpecific(context, typeVariable);
                Object objectPropertyValue = objectDeserializer.deserializeNullable(primed, context, typeVariable);
                deleteResponse = new DeleteResponse(objectPropertyValue, null);
            }
        }
        return deleteResponse;
    }
}
