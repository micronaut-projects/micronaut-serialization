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
 * A simple bean (no-args constructor and only properties) implementation for deserialization of objects that uses introspection metadata.
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
final class SimpleObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private final boolean ignoreUnknown;
    private final DeserBean<? super Object> deserBean;

    public SimpleObjectDeserializer(boolean ignoreUnknown, DeserBean<? super Object> deserBean) {
        this.ignoreUnknown = ignoreUnknown && deserBean.ignoreUnknown;
        this.deserBean = deserBean;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType)
            throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        deserBean.initialize(decoderContext);

        Object obj;
        try {
            obj = deserBean.introspection.instantiate();
        } catch (InstantiationException e) {
            throw new SerdeException("Unable to deserialize type [" + beanType + "]: " + e.getMessage(), e);
        }

        readProperties(decoder, decoderContext, beanType, obj);

        return obj;
    }

    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object value)
            throws IOException {
        deserBean.initialize(decoderContext);
        readProperties(decoder, decoderContext, beanType, value);
    }

    private void readProperties(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object obj) throws IOException {
        Decoder objectDecoder = decoder.decodeObject(beanType);

        if (deserBean.readProperties == null) {
            skipUnknownProperties(objectDecoder, beanType);
        } else {
            PropertiesBag<? super Object>.Consumer readProperties = deserBean.readProperties.newConsumer();

            while (true) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                @SuppressWarnings("unchecked") final DeserBean.DerProperty<Object, Object> consumedProperty =
                        (DeserBean.DerProperty<Object, Object>) readProperties.consume(prop);
                if (consumedProperty != null) {
                    boolean isNull = objectDecoder.decodeNull();
                    if (isNull) {
                        if (consumedProperty.nullable) {
                            consumedProperty.set(obj, null);
                        } else {
                            consumedProperty.setDefault(decoderContext, obj);
                        }
                    } else {
                        Object val;
                        Argument<Object> argument = consumedProperty.argument;
                        try {
                            val = consumedProperty
                                .deserializer
                                .createSpecific(decoderContext, argument)
                                .deserialize(objectDecoder, decoderContext, argument);
                        } catch (InvalidFormatException e) {
                            throw new InvalidPropertyFormatException(e, argument);
                        } catch (Exception e) {
                            throw new SerdeException("Error decoding property [" + argument + "] of type [" + deserBean.introspection.getBeanType() + "]: " + e.getMessage(), e);
                        }

                        consumedProperty.set(obj, val);
                        if (readProperties.isAllConsumed()) {
                            skipUnknownProperties(objectDecoder, beanType);
                            break;
                        }
                    }
                } else {
                    skipUnknown(objectDecoder, beanType, prop);
                }
            }

            if (!readProperties.isAllConsumed()) {
                for (DeserBean.DerProperty<? super Object, ?> dp : readProperties.getNotConsumed()) {
                    dp.setDefault(decoderContext, obj);
                }
            }
        }
        objectDecoder.finishStructure();
    }

    private void skipUnknownProperties(Decoder decoder, Argument<? super Object> beanType) throws IOException {
        while (true) {
            String unknownProp = decoder.decodeKey();
            if (unknownProp == null) {
                break;
            } else {
                skipUnknown(decoder, beanType, unknownProp);
            }
        }
    }

    private void skipUnknown(Decoder decoder, Argument<? super Object> beanType, String prop) throws IOException {
        if (ignoreUnknown) {
            decoder.skipValue();
        } else {
            throw new SerdeException("Unknown property [" + prop + "] encountered during deserialization of type: " + beanType);
        }
    }

    @Override
    public boolean allowNull() {
        return true;
    }

}
