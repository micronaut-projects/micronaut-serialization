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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;

import java.io.IOException;

/**
 * A simple delegating deserializer.
 *
 * @author Denis Stepanov
 */
@Internal
final class DelegatingObjectDeserializer implements Deserializer<Object> {
    private final boolean strictNullable;
    private final DeserBean<? super Object> deserBean;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    public DelegatingObjectDeserializer(boolean strictNullable,
                                        DeserBean<? super Object> deserBean,
                                        @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.strictNullable = strictNullable;
        this.deserBean = deserBean;
        this.preInstantiateCallback = preInstantiateCallback;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {

        if (deserBean.creatorParams != null) {
            final PropertiesBag<Object>.Consumer creatorParams = deserBean.creatorParams.newConsumer();
            final DeserBean.DerProperty<Object, Object> creator = creatorParams.getNotConsumed().iterator().next();
            Object result;
            try {
                Deserializer<Object> deserializer = creator.deserializer;
                result = deserializer.deserializeNullable(
                    decoder,
                    decoderContext,
                    creator.argument
                );
            } catch (InvalidFormatException e) {
                throw new InvalidPropertyFormatException(e, creator.argument);
            }
            Object[] args = new Object[]{result};
            if (preInstantiateCallback != null) {
                preInstantiateCallback.preInstantiate(deserBean.introspection, args);
            }
            return deserBean.introspection.instantiate(strictNullable, args);
        } else {
            throw new IllegalStateException("At least one creator parameter expected");
        }
    }

    @Override
    public Object deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

}
