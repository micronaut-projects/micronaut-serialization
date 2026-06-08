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
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * A wrapped object deserializer.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Internal
final class WrappedObjectSubtypedDeserializer implements Deserializer<Object> {

    private final DeserializerSubtypeInfo<? super Object> subtypeInfo;
    private final List<String> subtypeNames;
    private final Keys subtypeKeys;
    private final boolean ignoreUnknown;

    WrappedObjectSubtypedDeserializer(DeserializerSubtypeInfo<? super Object> subtypeInfo, boolean ignoreUnknown) {
        this.subtypeInfo = subtypeInfo;
        this.subtypeNames = List.copyOf(subtypeInfo.parent().subtypes().keySet());
        this.subtypeKeys = Keys.create(subtypeNames);
        this.ignoreUnknown = ignoreUnknown;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return Objects.requireNonNull(deserialize(decoder, context, type, false));
    }

    @Override
    public @Nullable Object deserializeNullable(Decoder decoder,
                                                DecoderContext context,
                                                Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }

        return deserialize(decoder, context, type, true);
    }

    private @Nullable Object deserialize(Decoder decoder,
                                         DecoderContext context,
                                         Argument<? super Object> type,
                                         boolean isNullable) throws IOException {

        KeysAwareDecoder unwrappedDecoder = KeysAwareDecoder.of(decoder.decodeObject());
        int keyIndex = unwrappedDecoder.decodeKey(subtypeKeys);
        String discriminatorValue;
        if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
            if (isNullable) {
                return null;
            }
            throw new SerdeException("Wrapper property is null encountered during deserialization of type: " + type);
        } else if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
            discriminatorValue = unwrappedDecoder.decodeKey();
        } else {
            discriminatorValue = subtypeNames.get(keyIndex);
        }
        Deserializer<? super Object> deserializer = subtypeInfo.findDeserializer(discriminatorValue);

        Object result;
        if (isNullable) {
            result = deserializer.deserializeNullable(unwrappedDecoder, context, type);
        } else {
            result = deserializer.deserialize(unwrappedDecoder, context, type);
        }

        if (ignoreUnknown) {
            unwrappedDecoder.finishStructure(true);
        } else {
            keyIndex = unwrappedDecoder.decodeKey(subtypeKeys);
            String unknownProp;
            if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                unknownProp = null;
            } else if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                unknownProp = unwrappedDecoder.decodeKey();
            } else {
                unknownProp = subtypeNames.get(keyIndex);
            }
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
