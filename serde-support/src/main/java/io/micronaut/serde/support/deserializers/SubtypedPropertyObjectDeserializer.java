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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LookaheadDecoder;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.io.IOException;
import java.util.Map;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @since 1.0.0
 */
final class SubtypedPropertyObjectDeserializer implements Deserializer<Object> {

    private final SubtypedDeserBean<? super Object> deserBean;
    private final Map<String, Deserializer<Object>> deserializers;
    private final Deserializer<Object> supertypeDeserializer;

    public SubtypedPropertyObjectDeserializer(SubtypedDeserBean<? super Object> deserBean,
                                              Map<String, Deserializer<Object>> deserializers, Deserializer<Object> supertypeDeserializer) {
        this.deserBean = deserBean;
        this.deserializers = deserializers;
        this.supertypeDeserializer = supertypeDeserializer;
        if (deserBean.discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
            throw new IllegalStateException("Unsupported discriminator type: " + deserBean.discriminatorType);
        }
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {
        LookaheadDecoder objectDecoder = decoder.decodeObjectLookahead(type);
        Deserializer<Object> deserializer = findDeserializer(objectDecoder);
        return deserializer.deserialize(
            objectDecoder.replay(),
            decoderContext,
            type
        );
    }

    @NonNull
    private Deserializer<Object> findDeserializer(Decoder objectDecoder) throws IOException {
        final String defaultImpl = deserBean.defaultImpl;
        final String discriminatorName = deserBean.discriminatorName;
        while (true) {
            final String key = objectDecoder.decodeKey();
            if (key == null) {
                break;
            }

            if (key.equals(discriminatorName)) {
                if (!objectDecoder.decodeNull()) {
                    final String subtypeName = objectDecoder.decodeString();
                    final Deserializer<Object> deserializer = deserializers.get(subtypeName);
                    if (deserializer != null) {
                        return deserializer;
                    }
                }
                break;
            } else {
                objectDecoder.skipValue();
            }
        }
        if (defaultImpl != null) {
            return deserializers.get(defaultImpl);
        }
        return supertypeDeserializer;
    }

}
