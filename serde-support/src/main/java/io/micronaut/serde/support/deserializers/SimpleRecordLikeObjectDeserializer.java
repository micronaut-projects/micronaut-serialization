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

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * A simple all-args constructor and no setter properties implementation for deserialization of objects that uses introspection metadata.
 *
 * @author Denis Stepanov
 */
final class SimpleRecordLikeObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private final BeanIntrospection<Object> introspection;
    private final PropertiesBag<Object> constructorParameters;
    private final int valuesSize;
    private final boolean ignoreUnknown;

    SimpleRecordLikeObjectDeserializer(boolean ignoreUnknown, DeserBean<? super Object> deserBean) {
        this.introspection = deserBean.introspection;
        this.constructorParameters = deserBean.creatorParams;
        this.valuesSize = deserBean.creatorSize;
        this.ignoreUnknown = ignoreUnknown && deserBean.ignoreUnknown;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType) throws IOException {
        final Decoder objectDecoder = decoder.decodeObject(beanType);
        final PropertiesBag<Object>.Consumer creatorParameters = constructorParameters.newConsumer();
        final Object[] params = new Object[valuesSize];
        boolean allConsumed = valuesSize == 0;
        while (!allConsumed) {
            final String propertyName = objectDecoder.decodeKey();
            if (propertyName == null) {
                break;
            }
            final DeserBean.DerProperty<Object, Object> derProperty = creatorParameters.consume(propertyName);
            if (derProperty != null) {
                derProperty.deserializeAndSetConstructorValue(objectDecoder, decoderContext, params);
                allConsumed = creatorParameters.isAllConsumed();
            } else if (ignoreUnknown) {
                objectDecoder.skipValue();
            } else {
                return new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + beanType);
            }
        }
        if (!allConsumed) {
            for (DeserBean.DerProperty<Object, Object> sp : creatorParameters.getNotConsumed()) {
                sp.setDefaultConstructorValue(decoderContext, params);
            }
        }

        Object obj;
        try {
            obj = introspection.instantiate(params);
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

}
