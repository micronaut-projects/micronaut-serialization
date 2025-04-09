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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Subtyped deduction deserializer.
 *
 * @author Denis Stepanov
 * @since 2.15
 */
@Internal
final class SubtypedDeductionDeserializer implements Deserializer<Object> {

    private final DeserBean<? super Object> deserBean;
    private final Map<String, Deserializer<Object>> deserializers;

    SubtypedDeductionDeserializer(DeserBean<? super Object> deserBean,
                                  Map<String, Deserializer<Object>> deserializers) {
        this.deserBean = deserBean;
        this.deserializers = deserializers;
        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = deserBean.subtypeInfo.info().discriminatorType();
        if (discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY
            && discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.EXISTING_PROPERTY) {
            throw new IllegalStateException("Unsupported discriminator type: " + discriminatorType);
        }
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {
        try (DemuxingObjectDecoder.PrimedDecoder primed = DemuxingObjectDecoder.prime(decoder)) {
            Decoder typeFinder = primed.decodeObjectNonConsuming(type);
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
    private Deserializer<Object> findDeserializer(Decoder objectDecoder) throws IOException {
        Map<String, DeserBean<?>> subtypes = new LinkedHashMap<>(deserBean.subtypeInfo.subtypes());

        while (true) {
            if (subtypes.size() == 1) {
                return deserializers.get(subtypes.keySet().iterator().next());
            }
            if (subtypes.isEmpty()) {
                break;
            }
            final String key = objectDecoder.decodeKey();
            if (key == null) {
                break;
            }
            Iterator<Map.Entry<String, DeserBean<?>>> iterator = subtypes.entrySet().iterator();
            while (iterator.hasNext()) {
                DeserBean<?> subtype = iterator.next().getValue();
                if (subtype.injectProperties != null && subtype.injectProperties.propertyIndexOf(key) != -1) {
                    // Found property
                    continue;
                }
                if (subtype.creatorParams != null && subtype.creatorParams.propertyIndexOf(key) != -1) {
                    // Found property
                    continue;
                }
                // Not found
                iterator.remove();
            }

            objectDecoder.skipValue();
        }
        throw new SerdeException("Cannot deduct the subtype for bean " + deserBean.introspection.getBeanType().getName());
    }

}
