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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.io.IOException;

/**
 * Subtyped property deserializer.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Internal
final class SubtypedPropertyObjectDeserializer implements Deserializer<Object> {

    private final DeserializerSubtypeInfo<? super Object> subtypeInfo;

    public SubtypedPropertyObjectDeserializer(DeserializerSubtypeInfo<? super Object> subtypeInfo) {
        this.subtypeInfo = subtypeInfo;
        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = subtypeInfo.parent().info().discriminatorType();
        if (discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY
            && discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.EXISTING_PROPERTY) {
            throw new IllegalStateException("Unsupported discriminator type: " + discriminatorType);
        }
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {
        try (DemuxingObjectDecoder.PrimedDecoder primed = DemuxingObjectDecoder.prime(decoder)) {
            Decoder typeFinder;
            if (subtypeInfo.parent().info().discriminatorVisible()) {
                typeFinder = primed.decodeObjectNonConsuming(type);
            } else {
                typeFinder = primed.decodeObject(type);
            }
            Deserializer<Object> deserializer = findDeserializer(typeFinder);
            typeFinder.finishStructure(true);

            return deserializer.deserialize(
                primed,
                decoderContext,
                type
            );
        }
    }

    @NonNull
    private Deserializer<? super Object> findDeserializer(Decoder objectDecoder) throws IOException {
        final DeserBeanSubtypeInfo<?> deserBeanSubtypeInfo = subtypeInfo.parent();
        final String discriminatorName = deserBeanSubtypeInfo.info().discriminatorName();

        while (true) {
            final String key = objectDecoder.decodeKey();
            if (key == null) {
                break;
            }

            if (key.equals(discriminatorName)) {
                if (objectDecoder.decodeNull()) {
                    return subtypeInfo.findDeserializer(null);
                }
                String discriminatorValue = objectDecoder.decodeString();
                return subtypeInfo.findDeserializer(discriminatorValue);
            } else {
                objectDecoder.skipValue();
            }
        }
        return subtypeInfo.findDeserializer(null);
    }

}
