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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;

import java.io.IOException;

/**
 * A simple bean (no-args constructor and only properties) implementation for deserialization of objects that uses introspection metadata.
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
final class SimpleObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private final boolean ignoreUnknown;
    private final boolean strictNullable;
    private final BeanIntrospection<Object> introspection;
    @Nullable
    private final PropertiesBag<Object> properties;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    SimpleObjectDeserializer(boolean strictNullable,
                             DeserBean<? super Object> deserBean,
                             @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.ignoreUnknown = deserBean.ignoreUnknown;
        this.strictNullable = strictNullable;
        this.introspection = deserBean.introspection;
        this.properties = deserBean.injectProperties;
        this.preInstantiateCallback = preInstantiateCallback;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType)
            throws IOException {
        Object obj;
        try {
            if (preInstantiateCallback != null) {
                preInstantiateCallback.preInstantiate(introspection, ArrayUtils.EMPTY_OBJECT_ARRAY);
            }
            obj = introspection.instantiate(strictNullable, ArrayUtils.EMPTY_OBJECT_ARRAY);
        } catch (InstantiationException e) {
            throw new SerdeException("Unable to deserialize type [" + beanType + "]: " + e.getMessage(), e);
        }

        deserializeInto(decoder, decoderContext, beanType, obj);

        return obj;
    }

    @Override
    public @Nullable Object deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object beanInstance)
            throws IOException {
        Decoder objectDecoder = decoder.decodeObject(beanType);
        boolean completed = false;
        PropertiesBag<Object>.Consumer propertiesConsumer = null;

        if (properties != null) {
            propertiesConsumer = properties.newConsumer();

            while (true) {
                final String propertyName = objectDecoder.decodeKey();
                if (propertyName == null) {
                    completed = true;
                    break;
                }
                final DeserBean.DerProperty<Object, Object> consumedProperty = propertiesConsumer.consume(propertyName);
                if (consumedProperty != null) {
                    consumedProperty.deserializeAndSetPropertyValue(objectDecoder, decoderContext, beanInstance);

                } else if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw unexpectedProperty(beanType, propertiesConsumer, propertyName);
                }
            }

            if (!propertiesConsumer.isAllConsumed()) {
                for (DeserBean.DerProperty<Object, Object> dp : propertiesConsumer.getNotConsumed()) {
                    dp.setDefaultPropertyValue(decoderContext, beanInstance);
                }
            }
        }

        if (completed) {
            objectDecoder.finishStructure();
        } else if (ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
            String propertyName = objectDecoder.decodeKey();
            if (propertyName != null) {
                throw unexpectedProperty(beanType, propertiesConsumer, propertyName);
            }
            objectDecoder.finishStructure();
        }
    }

    private SerdeException unexpectedProperty(Argument<? super Object> beanType, PropertiesBag<Object>.@Nullable Consumer propertiesConsumer, String propertyName) {
        if (propertiesConsumer != null && propertiesConsumer.contains(propertyName)) {
            return duplicateProperty(beanType, propertyName);
        }
        return unknownProperty(beanType, propertyName);
    }

    private static SerdeException unknownProperty(Argument<? super Object> beanType, String propertyName) {
        SerdeException serdeException = new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    private static SerdeException duplicateProperty(Argument<? super Object> beanType, String propertyName) {
        SerdeException serdeException = new SerdeException("Duplicate property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }
}
