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

import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * A simple all-args constructor and no setter properties implementation for deserialization of objects that uses introspection metadata.
 *
 * @author Denis Stepanov
 */
final class SimpleRecordLikeObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private final DeserBean<? super Object> deserBean;

    SimpleRecordLikeObjectDeserializer(DeserBean<? super Object> deserBean) {
        this.deserBean = deserBean;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType)
            throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        deserBean.initialize(decoderContext);

        Decoder objectDecoder = decoder.decodeObject(beanType);
        final PropertiesBag<Object>.Consumer creatorParameters = deserBean.creatorParams.newConsumer();
        Object[] params = new Object[deserBean.creatorSize];
        boolean allConsumed = deserBean.creatorSize == 0;
        while (!allConsumed) {
            final String propertyName = objectDecoder.decodeKey();
            if (propertyName == null) {
                break;
            }
            final DeserBean.DerProperty<Object, Object> derProperty = creatorParameters.consume(propertyName);
            if (derProperty != null) {
                try {
                    Object value = derProperty.deserializer.deserialize(objectDecoder, decoderContext, derProperty.argument);
                    if (value == null) {
                        derProperty.setDefault(decoderContext, params);
                    } else {
                        params[derProperty.index] = value;
                    }
                } catch (InvalidFormatException e) {
                    throw new InvalidPropertyFormatException(e, derProperty.argument);
                }
                allConsumed = creatorParameters.isAllConsumed();
            }
        }
        if (!allConsumed) {
            for (DeserBean.DerProperty<Object, Object> sp : creatorParameters.getNotConsumed()) {
                sp.setDefault(decoderContext, params);
            }
        }

        Object obj;
        try {
            obj = deserBean.introspection.instantiate(params);
        } catch (InstantiationException e) {
            throw new SerdeException("Unable to deserialize type [" + beanType + "]: " + e.getMessage(), e);
        }

        objectDecoder.finishStructure(true);

        return obj;
    }

    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object value)
            throws IOException {
        throw new SerdeException("Unsupported deserialize into for [" + beanType + "]");
    }

    @Override
    public boolean allowNull() {
        return true;
    }

}
