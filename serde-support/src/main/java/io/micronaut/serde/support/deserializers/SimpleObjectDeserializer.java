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

    SimpleObjectDeserializer(boolean ignoreUnknown, DeserBean<? super Object> deserBean) {
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

        if (deserBean.readProperties != null) {
            PropertiesBag<Object>.Consumer readProperties = deserBean.readProperties.newConsumer();

            boolean allConsumed = false;
            while (!allConsumed) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                final DeserBean.DerProperty<Object, Object> consumedProperty = readProperties.consume(prop);
                if (consumedProperty != null) {
                    boolean isNull = objectDecoder.decodeNull();
                    if (isNull) {
                        if (consumedProperty.nullable) {
                            consumedProperty.set(obj, null);
                        } else {
                            consumedProperty.setDefault(decoderContext, obj);
                        }
                    } else {
                        Argument<Object> argument = consumedProperty.argument;
                        try {
                            Object val = consumedProperty.deserializer.deserialize(objectDecoder, decoderContext, argument);
                            consumedProperty.beanProperty.setUnsafe(obj, val);
                        } catch (InvalidFormatException e) {
                            throw new InvalidPropertyFormatException(e, argument);
                        } catch (Exception e) {
                            throw new SerdeException("Error decoding property [" + argument + "] of type [" + deserBean.introspection.getBeanType() + "]: " + e.getMessage(), e);
                        }
                    }

                    allConsumed = readProperties.isAllConsumed();

                } else {
                    skipUnknown(objectDecoder, beanType, prop);
                }
            }

            if (!allConsumed) {
                for (DeserBean.DerProperty<Object, Object> dp : readProperties.getNotConsumed()) {
                    dp.setDefault(decoderContext, obj);
                }
            }
        }

        if (ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
            while (true) {
                String unknownProp = objectDecoder.decodeKey();
                if (unknownProp == null) {
                    break;
                } else {
                    skipUnknown(objectDecoder, beanType, unknownProp);
                }
            }
            objectDecoder.finishStructure();
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
