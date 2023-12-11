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
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * Json value deserializer.
 *
 * @author Denis Stepanov
 * @since 2.5.0
 */
final class JsonValueDeserializer implements Deserializer<Object> {
    private final DeserBean<? super Object> deserBean;

    JsonValueDeserializer(DeserBean<? super Object> deserBean) {
        this.deserBean = deserBean;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType)
        throws IOException {
        if (deserBean.creatorSize == 0) {
            throw new SerdeException("Expected at least one constructor parameter");
        }
        final Object[] params = new Object[1];
        final DeserBean.DerProperty<Object, Object> constructorJsonValue = deserBean.creatorParams.getDerProperties().get(0);
        if (constructorJsonValue != null) {
            constructorJsonValue.deserializeAndSetConstructorValue(decoder, decoderContext, params);
        }
        try {
            return deserBean.introspection.instantiate(params);
        } catch (InstantiationException e) {
            throw new SerdeException("Unable to deserialize type [" + beanType + "]: " + e.getMessage(), e);
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
