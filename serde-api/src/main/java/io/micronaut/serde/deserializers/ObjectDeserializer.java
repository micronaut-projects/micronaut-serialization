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
package io.micronaut.serde.deserializers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.beans.DeserIntrospection;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

@Singleton
@Primary
public class ObjectDeserializer implements Deserializer<Object> {
    @Override
    public Object deserialize(Decoder decoder,
                              DecoderContext decoderContext,
                              Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }

        final DeserIntrospection<? super Object> introspection;
        try {
            introspection = decoderContext.getDeserializableIntrospection(type);
        } catch (IntrospectionException e) {
            throw new SerdeException("Unable to deserialize object of type: " + type, e);
        }
        final Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> readProperties =
                introspection.readProperties != null ? new HashMap<>(introspection.readProperties) : null;
        final boolean hasProperties = readProperties != null;

        if (introspection.creatorParams != null) {

            final Decoder objectDecoder = decoder.decodeObject();
            final Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> creatorParameters =
                    new HashMap<>(introspection.creatorParams);
            Object[] params = new Object[creatorParameters.size()];
            PropertyBuffer buffer = null;
            while (true) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                final DeserIntrospection.SerdeProperty<? super Object, ?> sp =
                        creatorParameters.remove(prop);
                if (sp != null) {
                    @SuppressWarnings("unchecked") final Argument<Object> propertyType = (Argument<Object>) sp.argument;
                    final Object val = sp.deserializer.deserialize(
                            objectDecoder,
                            decoderContext,
                            propertyType
                    );
                    params[sp.index] = val;
                    if (creatorParameters.isEmpty()) {
                        break;
                    }
                } else if (hasProperties) {
                    final DeserIntrospection.SerdeProperty<? super Object, ?> rp = readProperties.get(prop);
                    if (rp != null) {
                        @SuppressWarnings("unchecked")
                        final Argument<Object> argument = (Argument<Object>) rp.argument;
                        final Object val = rp.deserializer.deserialize(
                                objectDecoder,
                                decoderContext,
                                argument
                        );
                        if (buffer == null) {
                            buffer = new PropertyBuffer(rp.writer, prop, val, null);
                        } else {
                            buffer = buffer.next(rp.writer, prop, val);
                        }
                    }
                } else {
                    objectDecoder.skipValue();
                }
            }

            if (!creatorParameters.isEmpty()) {
                for (DeserIntrospection.SerdeProperty<? super Object, ?> sp : creatorParameters.values()) {
                    if (sp.defaultValue != null) {
                        params[sp.index] = sp.defaultValue;
                    } else if(sp.argument.isNullable()) {
                        params[sp.index] = null;
                    } else {
                        throw new SerdeException("Unable to deserialize type [" + type + "]. Required constructor parameter [" + sp.argument + "] at index [" + sp.index + "] is not present in supplied data");
                    }
                }
            }

            final Object obj = introspection.introspection.instantiate(params);
            if (hasProperties) {

                if (buffer != null) {
                    for (PropertyBuffer propertyBuffer : buffer) {
                        propertyBuffer.set(obj);
                        readProperties.remove(propertyBuffer.property);
                    }
                }
                if (!readProperties.isEmpty()) {
                    // more properties still to be read
                    decodeProperties(
                            type,
                            decoderContext,
                            obj,
                            objectDecoder,
                            readProperties
                    );
                }
            }
            objectDecoder.finishStructure();
            return obj;
        } else {
            final Object obj = introspection.introspection.instantiate();
            final Decoder objectDecoder = decoder.decodeObject();
            if (hasProperties) {
                decodeProperties(type, decoderContext, obj, objectDecoder, readProperties);
            }
            objectDecoder.finishStructure();

            return obj;
        }

    }

    @Override
    public boolean allowNull() {
        return true;
    }

    private void decodeProperties(Argument<?> type,
                                  DecoderContext decoderContext,
                                  Object obj,
                                  Decoder objectDecoder,
                                  Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> readProperties) throws IOException {
        while (true) {
            final String prop = objectDecoder.decodeKey();
            if (prop == null) {
                break;
            }
            @SuppressWarnings("unchecked") final DeserIntrospection.SerdeProperty<Object, Object> property =
                    (DeserIntrospection.SerdeProperty<Object, Object>) readProperties.remove(prop);
            if (property != null) {
                final Argument<Object> propertyType = property.argument;
                final Object val = property.deserializer.deserialize(
                        objectDecoder,
                        decoderContext,
                        propertyType
                );
                // writer is never null for properties
                //noinspection ConstantConditions
                property.writer.set(obj, val);
                if (readProperties.isEmpty()) {
                    break;
                }
            } else {
                objectDecoder.skipValue();
            }
        }

        if (!readProperties.isEmpty()) {
            for (DeserIntrospection.SerdeProperty<? super Object, ?> sp : readProperties.values()) {
                if (sp.defaultValue != null) {
                    @SuppressWarnings("unchecked") final BeanProperty<? super Object, Object> writer =
                            (BeanProperty<? super Object, Object>) sp.writer;
                    writer.set(obj, sp.defaultValue);
                } else if (sp.argument.isNonNull()) {
                    throw new SerdeException("Unable to deserialize type [" + type + "]. Required property [" + sp.argument + "] is not present in supplied data");
                }
            }
        }
    }

    private static final class PropertyBuffer implements Iterable<PropertyBuffer> {

        final BeanProperty<? super Object, Object> bp;
        final String property;
        final Object value;
        private final PropertyBuffer next;

        public PropertyBuffer(BeanProperty<? super Object, ?> bp,
                              String property,
                              Object val,
                              @Nullable PropertyBuffer next) {
            //noinspection unchecked
            this.bp = (BeanProperty<? super Object, Object>) bp;
            this.property = property;
            this.value = val;
            this.next = next;
        }

        PropertyBuffer next(BeanProperty<? super Object, ?> rp, String property, Object val) {
            return new PropertyBuffer(rp, property, val, this);
        }

        @Override
        public Iterator<PropertyBuffer> iterator() {
            return new Iterator<PropertyBuffer>() {
                PropertyBuffer thisBuffer = null;

                @Override
                public boolean hasNext() {
                    return thisBuffer == null || thisBuffer.next != null;
                }

                @Override
                public PropertyBuffer next() {
                    if (thisBuffer == null) {
                        thisBuffer = PropertyBuffer.this;
                    } else {
                        thisBuffer = thisBuffer.next;
                    }
                    return thisBuffer;
                }
            };
        }

        public void set(Object obj) {
            bp.set(obj, value);
        }
    }

}
