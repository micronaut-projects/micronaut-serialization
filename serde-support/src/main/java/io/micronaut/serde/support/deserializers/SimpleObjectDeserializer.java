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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * A simple bean (no-args constructor and only properties) implementation for deserialization of objects that uses introspection metadata.
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
final class SimpleObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private final boolean ignoreUnknown;
    private final boolean strictNullable;
    private final BeanIntrospection<Object> introspection;
    private final PropertiesBag<Object> properties;
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    SimpleObjectDeserializer(boolean ignoreUnknown, boolean strictNullable,
                             DeserBean<? super Object> deserBean,
                             SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.ignoreUnknown = ignoreUnknown && deserBean.ignoreUnknown;
        this.strictNullable = strictNullable;
        this.introspection = deserBean.introspection;
        this.properties = deserBean.readProperties;
        this.preInstantiateCallback = preInstantiateCallback;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType)
            throws IOException {
        Object obj;
        try {
            Object[] arguments = {};
            if (preInstantiateCallback != null) {
                preInstantiateCallback.preInstantiate(introspection, arguments);
            }
            obj = introspection.instantiate(strictNullable, arguments);
        } catch (InstantiationException e) {
            throw new SerdeException("Unable to deserialize type [" + beanType + "]: " + e.getMessage(), e);
        }

        deserializeInto(decoder, decoderContext, beanType, obj);

        return obj;
    }

    @Override
    public Object deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object beanInstance)
            throws IOException {
        Decoder objectDecoder = decoder.decodeObject(beanType);

        if (properties != null) {
            PropertiesBag<Object>.Consumer propertiesConsumer = properties.newConsumer();

            boolean allConsumed = false;
            while (!allConsumed) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                final DeserBean.DerProperty<Object, Object> consumedProperty = propertiesConsumer.consume(prop);
                if (consumedProperty != null) {
                    consumedProperty.deserializeAndSetPropertyValue(objectDecoder, decoderContext, beanInstance);
                    allConsumed = propertiesConsumer.isAllConsumed();

                } else if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw unknownProperty(beanType, prop);
                }
            }

            if (!allConsumed) {
                for (DeserBean.DerProperty<Object, Object> dp : propertiesConsumer.getNotConsumed()) {
                    dp.setDefaultPropertyValue(decoderContext, beanInstance);
                }
            }
        }

        if (ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
            String unknownProp = objectDecoder.decodeKey();
            if (unknownProp != null) {
                throw unknownProperty(beanType, unknownProp);
            }
            objectDecoder.finishStructure();
        }
    }

    private SerdeException unknownProperty(Argument<? super Object> beanType, String prop) {
        return new SerdeException("Unknown property [" + prop + "] encountered during deserialization of type: " + beanType);
    }
}
