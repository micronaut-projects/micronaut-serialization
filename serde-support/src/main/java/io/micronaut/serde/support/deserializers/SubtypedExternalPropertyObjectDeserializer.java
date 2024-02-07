/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;

import java.io.IOException;
import java.util.Map;

/**
 * Subtyped external property deserializer.
 *
 * @author Denis Stepanov
 * @since 2.5.0
 */
@Internal
final class SubtypedExternalPropertyObjectDeserializer implements Deserializer<Object> {

    private final DeserializeSubtypeInfo<?> subtypeInfo;
    private final Map<String, Deserializer<Object>> deserializers;

    SubtypedExternalPropertyObjectDeserializer(DeserializeSubtypeInfo<?> subtypeInfo,
                                               Map<String, Deserializer<Object>> deserializers) {
        this.subtypeInfo = subtypeInfo;
        this.deserializers = deserializers;
    }

    static PropertyReference<Object, String> createExternalPropertyReference(DecoderContext decoderContext, String discriminator, String value) {
        String referenceName = "externalProperty@" + discriminator;
        // TODO: We need a better API for this case when there is no introspection
        return decoderContext.resolveReference(
            new PropertyReference<>(
                referenceName,
                null,
                Argument.of(String.class, referenceName),
                value)
        );
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
        PropertyReference<Object, String> externalPropertyReference = createExternalPropertyReference(decoderContext, subtypeInfo.info().discriminatorName(), null);
        PropertyReference<Object, String> ref = decoderContext.resolveReference(externalPropertyReference);
        String subtypeName = (String) ref.getReference();
        Deserializer<?> deserializer = deserializers.get(subtypeName);
        if (deserializer == null && subtypeInfo.defaultDiscriminator() != null) {
            deserializer = deserializers.get(subtypeInfo.defaultDiscriminator());
        }
        if (deserializer == null) {
            throw new SerdeException("Cannot find subtype deserializer for discriminator: " + subtypeName + "  and argument: [" + type + "]");
        }
        return deserializer.deserialize(decoder, decoderContext, type);
    }

}
