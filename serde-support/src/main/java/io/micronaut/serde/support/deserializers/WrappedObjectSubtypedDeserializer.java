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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.util.Map;

/**
 * A wrapped object deserializer.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Internal
final class WrappedObjectSubtypedDeserializer implements Deserializer<Object> {

    private final Map<String, Deserializer<Object>> subtypes;
    private final boolean ignoreUnknown;

    WrappedObjectSubtypedDeserializer(Map<String, Deserializer<Object>> subtypes,
                                      boolean ignoreUnknown) {
        this.subtypes = subtypes;
        this.ignoreUnknown = ignoreUnknown;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return deserialize(decoder, context, type, false);
    }

    @Override
    public Object deserializeNullable(@NonNull Decoder decoder,
                                      @NonNull DecoderContext context,
                                      @NonNull Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }

        return deserialize(decoder, context, type, true);
    }

    private Object deserialize(Decoder decoder,
                               DecoderContext context,
                               Argument<? super Object> type,
                               boolean isNullable) throws IOException {

        Decoder unwrappedDecoder = decoder.decodeObject();
        String key = unwrappedDecoder.decodeKey();
        if (key == null) {
            if (isNullable) {
                return null;
            }
            throw new SerdeException("Wrapper property is null encountered during deserialization of type: " + type);
        }
        Deserializer<Object> deserializer = subtypes.get(key);
        if (deserializer == null) {
            throw new SerdeException("Unknown wrapper property: [" + key + "] encountered during deserialization of type: " + type);
        }

        Object result;
        if (isNullable) {
            result = deserializer.deserializeNullable(unwrappedDecoder, context, type);
        } else {
            result = deserializer.deserialize(unwrappedDecoder, context, type);
        }

        if (ignoreUnknown) {
            unwrappedDecoder.finishStructure(true);
        } else {
            String unknownProp = unwrappedDecoder.decodeKey();
            if (unknownProp != null) {
                throw unknownProperty(type, unknownProp);
            }
            unwrappedDecoder.finishStructure();
        }

        return result;
    }

    private SerdeException unknownProperty(Argument<? super Object> beanType, String prop) {
        return new SerdeException("Unknown property [" + prop + "] encountered during deserialization of type: " + beanType);
    }

}
