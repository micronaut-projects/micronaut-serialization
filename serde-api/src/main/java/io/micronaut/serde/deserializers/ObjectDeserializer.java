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
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
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

        final Class<? super Object> objectType = type.getType();
        final DeserIntrospection<? super Object> introspection;
        try {
            introspection = decoderContext.getDeserializableIntrospection(type);
        } catch (IntrospectionException e) {
            throw new SerdeException("Unable to deserialize object of type: " + type, e);
        }
        final Map<String, ? extends DeserIntrospection.DerProperty<? super Object, ?>> readProperties =
                introspection.readProperties != null ? new HashMap<>(introspection.readProperties) : null;
        final boolean hasProperties = readProperties != null;

        if (introspection.creatorParams != null) {

            final Decoder objectDecoder = decoder.decodeObject();
            final Map<String, ? extends DeserIntrospection.DerProperty<? super Object, ?>> creatorParameters =
                    new HashMap<>(introspection.creatorParams);
            int creatorSize = introspection.creatorSize;
            Object[] params = new Object[creatorSize];
            PropertyBuffer buffer = null;

            while (true) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                final DeserIntrospection.DerProperty<? super Object, ?> sp =
                        creatorParameters.remove(prop);
                if (sp != null) {
                    @SuppressWarnings("unchecked") final Argument<Object> propertyType = (Argument<Object>) sp.argument;
                    final Object val = sp.deserializer.deserialize(
                            objectDecoder,
                            decoderContext,
                            propertyType
                    );
                    if (sp.declaringType == objectType) {
                        params[sp.index] = val;
                    } else {
                        buffer = initBuffer(buffer, sp, prop, val);
                    }
                    if (creatorParameters.isEmpty()) {
                        break;
                    }
                } else if (hasProperties) {
                    final DeserIntrospection.DerProperty<? super Object, ?> rp = readProperties.get(prop);
                    if (rp != null) {
                        @SuppressWarnings("unchecked") final Argument<Object> argument = (Argument<Object>) rp.argument;
                        final Object val = rp.deserializer.deserialize(
                                objectDecoder,
                                decoderContext,
                                argument
                        );
                        buffer = initBuffer(buffer, rp, prop, val);
                    }
                } else {
                    objectDecoder.skipValue();
                }
            }

            if (!creatorParameters.isEmpty()) {
                // set unsatisfied parameters to defaults or ail
                for (DeserIntrospection.DerProperty<? super Object, ?> sp : creatorParameters.values()) {
                    if (sp.unwrapped != null && buffer != null) {
                        final Object o = materializeFromBuffer(sp, buffer);
                        if (o == null) {
                            sp.setDefault(params);
                        } else {
                            params[sp.index] = o;
                        }
                    } else {
                        sp.setDefault(params);
                    }
                }
            }

            final Object obj;
            try {
                obj = introspection.introspection.instantiate(params);
            } catch (InstantiationException e) {
                throw new SerdeException("Unable to deserialize type [" + type + "]:" + e.getMessage(), e);
            }
            if (hasProperties) {

                if (buffer != null) {
                    for (PropertyBuffer propertyBuffer : buffer) {
                        final DeserIntrospection.DerProperty<? super Object, ?> derProperty =
                                readProperties.remove(propertyBuffer.name);
                        if (derProperty != null) {
                            if (derProperty.declaringType == objectType) {
                                propertyBuffer.set(obj);
                            }
                        }
                    }
                }
                if (!readProperties.isEmpty()) {
                    // more properties still to be read
                    buffer = decodeProperties(
                            introspection,
                            decoderContext,
                            obj,
                            objectDecoder,
                            readProperties,
                            introspection.unwrappedProperties,
                            buffer
                    );
                }

                applyDefaultValuesOrFail(
                        obj,
                        readProperties,
                        introspection.unwrappedProperties,
                        buffer
                );
            }

            // finish up
            while (objectDecoder.decodeKey() != null) {
                objectDecoder.skipValue();
            }
            objectDecoder.finishStructure();
            return obj;
        } else {
            final Object obj = introspection.introspection.instantiate();
            final Decoder objectDecoder = decoder.decodeObject();
            if (hasProperties) {
                final PropertyBuffer propertyBuffer = decodeProperties(introspection,
                                                                       decoderContext,
                                                                       obj,
                                                                       objectDecoder,
                                                                       readProperties,
                                                                       introspection.unwrappedProperties, null);
                // the property buffer will be non-null if there were any unwrapped
                // properties in which case we need to go through and materialize unwrapped
                // from the buffer
                applyDefaultValuesOrFail(obj, readProperties, introspection.unwrappedProperties, propertyBuffer);
            }
            // finish up
            while (objectDecoder.decodeKey() != null) {
                objectDecoder.skipValue();
            }
            objectDecoder.finishStructure();

            return obj;
        }

    }

    private PropertyBuffer initBuffer(PropertyBuffer buffer,
                                      DeserIntrospection.DerProperty<? super Object, ?> rp,
                                      String prop,
                                      Object val) {
        if (buffer == null) {
            buffer = new PropertyBuffer(rp, prop, val, null);
        } else {
            buffer = buffer.next(rp, prop, val);
        }
        return buffer;
    }

    @Override
    public boolean allowNull() {
        return true;
    }

    private PropertyBuffer decodeProperties(
            DeserIntrospection<? super Object> introspection,
            DecoderContext decoderContext,
            Object obj,
            Decoder objectDecoder,
            Map<String, ? extends DeserIntrospection.DerProperty<? super Object, ?>> readProperties,
            DeserIntrospection.DerProperty<? super Object, Object>[] unwrappedProperties,
            PropertyBuffer propertyBuffer) throws IOException {
        while (true) {
            final String prop = objectDecoder.decodeKey();
            if (prop == null) {
                break;
            }
            @SuppressWarnings("unchecked") final DeserIntrospection.DerProperty<Object, Object> property =
                    (DeserIntrospection.DerProperty<Object, Object>) readProperties.remove(prop);
            if (property != null && property.writer != null) {
                final Argument<Object> propertyType = property.argument;
                final Object val = property.deserializer.deserialize(
                        objectDecoder,
                        decoderContext,
                        propertyType
                );
                // writer is never null for properties
                final BeanProperty<Object, Object> writer = property.writer;
                if (introspection.introspection == writer.getDeclaringBean()) {
                    writer.set(obj, val);
                } else {
                    propertyBuffer = initBuffer(propertyBuffer, property, prop, val);
                }
                if (readProperties.isEmpty() && unwrappedProperties == null) {
                    break;
                }
            } else {
                objectDecoder.skipValue();
            }
        }
        return propertyBuffer;
    }

    private void applyDefaultValuesOrFail(
            Object obj,
            Map<String, ? extends DeserIntrospection.DerProperty<? super Object, ?>> readProperties,
            @Nullable DeserIntrospection.DerProperty<? super Object, Object>[] unwrappedProperties,
            @Nullable PropertyBuffer buffer)
            throws SerdeException {
        if (ArrayUtils.isNotEmpty(unwrappedProperties)) {
            for (DeserIntrospection.DerProperty<? super Object, Object> dp : unwrappedProperties) {
                if (buffer == null) {
                    dp.set(obj, null);
                } else {
                     Object v = materializeFromBuffer(dp, buffer);
                     dp.set(obj, v);
                }
            }
        }
        if (!readProperties.isEmpty()) {
            for (DeserIntrospection.DerProperty<? super Object, ?> dp : readProperties.values()) {
                dp.setDefault(obj);
            }
        }
    }

    private @Nullable Object materializeFromBuffer(
                                         DeserIntrospection.DerProperty<? super Object, ?> property,
                                         PropertyBuffer buffer) throws SerdeException {
        @SuppressWarnings("unchecked")
        final DeserIntrospection<Object> unwrapped = (DeserIntrospection<Object>) property.unwrapped;
        if (unwrapped != null) {
            final Map<String, ? extends DeserIntrospection.DerProperty<?, ?>> creatorParams =
                    unwrapped.creatorParams;
            final Map<String, ? extends DeserIntrospection.DerProperty<Object, Object>> readProperties = unwrapped.readProperties;

            Object object;
            if (creatorParams != null) {
                Object[] params = new Object[unwrapped.creatorSize];
                // handle construction
                for (DeserIntrospection.DerProperty<?, ?> der : creatorParams.values()) {
                    boolean satisfied = false;
                    for (PropertyBuffer pb : buffer) {
                        if (pb.property == der) {
                            params[der.index] = pb.value;
                            satisfied = true;
                            break;
                        }
                    }
                    if (!satisfied) {
                        if (der.defaultValue != null) {
                            params[der.index] = der.defaultValue;
                        } else if (der.isNonNull()) {
                            throw new SerdeException("Unable to deserialize type [" + unwrapped.introspection.getBeanType() + "]. Required constructor parameter [" + der.argument + "] at index [" + der.index + "] is not present in supplied data");

                        }
                    }
                }
                object = unwrapped.introspection.instantiate(params);
            } else {
                object = unwrapped.introspection.instantiate();
            }

            if (readProperties != null) {
                for (DeserIntrospection.DerProperty<Object, Object> der : readProperties.values()) {
                    boolean satisfied = false;
                    for (PropertyBuffer pb : buffer) {
                        if (pb.property == der) {
                            der.set(object, pb.value);
                            satisfied = true;
                            break;
                        }
                    }
                    if (!satisfied) {
                        der.setDefault(object);
                    }
                }
            }
            return object;
        }
        return null;
    }

    private static final class PropertyBuffer implements Iterable<PropertyBuffer> {

        final DeserIntrospection.DerProperty<? super Object, Object> property;
        final String name;
        final Object value;
        private final PropertyBuffer next;

        public PropertyBuffer(DeserIntrospection.DerProperty<? super Object, ?> derProperty,
                              String name,
                              Object val,
                              @Nullable PropertyBuffer next) {
            //noinspection unchecked
            this.property = (DeserIntrospection.DerProperty<? super Object, Object>) derProperty;
            this.name = name;
            this.value = val;
            this.next = next;
        }

        PropertyBuffer next(DeserIntrospection.DerProperty<? super Object, ?> rp, String property, Object val) {
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
            if (property.writer != null) {
                property.writer.set(obj, value);
            }
        }
    }

}
