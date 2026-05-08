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
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Subtyped external property deserializer.
 *
 * @author Denis Stepanov
 * @since 2.5.0
 */
@Internal
final class SubtypedExternalPropertyObjectDeserializer implements Deserializer<Object> {

    private final DeserializerSubtypeInfo<? super Object> subtypeInfo;

    SubtypedExternalPropertyObjectDeserializer(DeserializerSubtypeInfo<? super Object> subtypeInfo) {
        this.subtypeInfo = subtypeInfo;
    }

    static PropertyReference<Object, String> createExternalPropertyReference(DecoderContext decoderContext, String discriminator, @Nullable String value) throws SerdeException {
        String referenceName = "externalProperty@" + discriminator;
        // TODO: We need a better API for this case when there is no introspection
        PropertyReference<Object, String> reference = decoderContext.resolveReference(
            new PropertyReference<>(
                referenceName,
                null,
                Argument.of(String.class, referenceName),
                value)
        );
        if (reference == null) {
            throw unresolvedExternalPropertyReference(discriminator);
        }
        return reference;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
        String discriminator = subtypeInfo.parent().info().discriminatorName();
        PropertyReference<Object, String> externalPropertyReference = createExternalPropertyReference(decoderContext, discriminator, null);
        PropertyReference<Object, String> ref = decoderContext.resolveReference(externalPropertyReference);
        if (ref == null) {
            throw unresolvedExternalPropertyReference(discriminator);
        }
        String discriminatorValue = (String) ref.getReference();
        Deserializer<? super Object> deserializer = subtypeInfo.findDeserializer(discriminatorValue);
        return deserializer.deserialize(decoder, decoderContext, type);
    }

    private static SerdeException unresolvedExternalPropertyReference(String discriminator) {
        return new SerdeException("Cannot resolve external property reference for discriminator: " + discriminator);
    }

}
