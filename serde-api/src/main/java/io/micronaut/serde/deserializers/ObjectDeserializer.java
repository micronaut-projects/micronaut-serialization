package io.micronaut.serde.deserializers;

import java.io.IOException;
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
        final DeserIntrospection<? super Object> introspection;
        try {
            introspection = decoderContext.getDeserializableIntrospection(type);
        } catch (IntrospectionException e) {
            throw new SerdeException("Unable to deserialize object of type: " + type, e);
        }
        if (introspection.creatorParams != null) {

            final Decoder objectDecoder = decoder.decodeObject();
            final Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> creatorParameters =
                    introspection.creatorParams;
            final Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> readProperties =
                    introspection.readProperties;
            int creatorSize = creatorParameters.size();
            Object[] params = new Object[creatorSize];
            PropertyBuffer buffer = null;
            final boolean hasProperties = readProperties != null;
            int propSize = hasProperties ? readProperties.size() : 0;
            while (true) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                final DeserIntrospection.SerdeProperty<? super Object, ?> sp =
                        creatorParameters.get(prop);
                if (sp != null) {
                    @SuppressWarnings("unchecked") final Argument<Object> propertyType = (Argument<Object>) sp.argument;
                    final Object val = sp.deserializer.deserialize(
                            objectDecoder,
                            decoderContext,
                            propertyType
                    );
                    params[sp.index] = val;
                    if (--creatorSize == 0) {
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
                            buffer = new PropertyBuffer(rp.writer, val, null);
                        } else {
                            buffer = buffer.next(rp.writer, val);
                        }
                    }
                } else {
                    objectDecoder.skipValue();
                }
            }

            final Object obj = introspection.introspection.instantiate(params);
            if (hasProperties) {

                if (buffer != null) {
                    for (PropertyBuffer propertyBuffer : buffer) {
                        propertyBuffer.set(obj);
                        propSize--;
                    }
                }
            }
            if (propSize != 0) {
                // more properties still to be read
                decodeProperties(
                        decoderContext,
                        obj,
                        objectDecoder,
                        readProperties,
                        propSize
                );
            }
            objectDecoder.finishStructure();
            return obj;
        } else {
            // no constructors so no need to buffer
            final Object obj = introspection.introspection.instantiate();
            if (introspection.readProperties != null) {

                final Decoder objectDecoder = decoder.decodeObject();
                final Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> readProperties =
                        introspection.readProperties;
                int total = readProperties.size();
                decodeProperties(decoderContext, obj, objectDecoder, readProperties, total);
                objectDecoder.finishStructure();
            }

            return obj;
        }

    }

    private void decodeProperties(DecoderContext decoderContext,
                           Object obj,
                           Decoder objectDecoder,
                           Map<String, ? extends DeserIntrospection.SerdeProperty<? super Object, ?>> readProperties,
                           int total) throws IOException {
        while (true) {
            final String prop = objectDecoder.decodeKey();
            if (prop == null) {
                break;
            }
            @SuppressWarnings("unchecked") final DeserIntrospection.SerdeProperty<Object, Object> property =
                    (DeserIntrospection.SerdeProperty<Object, Object>) readProperties.get(prop);
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
                if (--total == 0) {
                    break;
                }
            } else {
                objectDecoder.skipValue();
            }

        }
    }

    private static final class PropertyBuffer implements Iterable<PropertyBuffer> {

        final BeanProperty<? super Object, Object> property;
        final Object value;
        private final PropertyBuffer next;

        public PropertyBuffer(BeanProperty<? super Object, ?> rp, Object val, @Nullable PropertyBuffer next) {
            //noinspection unchecked
            this.property = (BeanProperty<? super Object, Object>) rp;
            this.value = val;
            this.next = next;
        }

        PropertyBuffer next(BeanProperty<? super Object, ?> rp, Object val) {
            return new PropertyBuffer(rp, val, this);
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
            property.set(obj, value);
        }
    }

}
