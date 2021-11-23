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

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableDeserializer;
import io.micronaut.serde.util.TypeKey;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Singleton
@Primary
public class ObjectDeserializer implements NullableDeserializer<Object>, DeserBeanRegistry {
    private final SerdeIntrospections introspections;
    private final boolean ignoreUnknown;
    private final Map<TypeKey, DeserBean<Object>> deserBeanMap = new ConcurrentHashMap<>(50);

    public ObjectDeserializer(SerdeIntrospections introspections, DeserializationConfiguration deserializationConfiguration) {
        this.introspections = introspections;
        this.ignoreUnknown = deserializationConfiguration.isIgnoreUnknown();
    }

    @Override
    public Object deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
            throws IOException {

        Class<? super Object> objectType = type.getType();
        DeserBean<? super Object> deserBean;
        try {
            deserBean = getDeserializableBean(type, decoderContext);
        } catch (IntrospectionException e) {
            throw new SerdeException("Unable to deserialize object of type: " + type, e);
        }

        if (deserBean.delegating) {
            final Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> creatorParams = deserBean.creatorParams;
            if (CollectionUtils.isNotEmpty(creatorParams)) {
                @SuppressWarnings("unchecked") final DeserBean.DerProperty<Object, Object> creator =
                        (DeserBean.DerProperty<Object, Object>) creatorParams.values().iterator().next();
                final Object val = creator.deserializer.deserialize(
                        decoder,
                        decoderContext,
                        creator.argument
                );
                return deserBean.introspection.instantiate(val);
            } else {
                throw new IllegalStateException("At least one creator parameter expected");
            }
        } else {

            Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> readProperties =
                    deserBean.readProperties != null ? new HashMap<>(deserBean.readProperties) : null;
            boolean hasProperties = readProperties != null;
            boolean ignoreUnknown = this.ignoreUnknown && deserBean.ignoreUnknown;

            Decoder objectDecoder = decoder.decodeObject();
            TokenBuffer tokenBuffer = null;
            AnyValues<Object> anyValues = deserBean.anySetter != null ? new AnyValues<>(deserBean.anySetter) : null;
            Object obj;

            if (deserBean instanceof SubtypedDeserBean) {
                // subtyped binding required
                SubtypedDeserBean<? super Object> subtypedDeserBean = (SubtypedDeserBean) deserBean;
                final String discriminatorName = subtypedDeserBean.discriminatorName;
                final Map<String, DeserBean<?>> subtypes = subtypedDeserBean.subtypes;
                final SerdeConfig.Subtyped.DiscriminatorType discriminatorType = subtypedDeserBean.discriminatorType;
                if (discriminatorType == SerdeConfig.Subtyped.DiscriminatorType.PROPERTY) {
                    while (true) {
                        final String key = objectDecoder.decodeKey();
                        if (key == null) {
                            break;
                        }

                        if (key.equals(discriminatorName)) {
                            if (!objectDecoder.decodeNull()) {
                                final String subtypeName = objectDecoder.decodeString();
                                final DeserBean<?> subtypeDeser = subtypes.get(subtypeName);
                                if (subtypeDeser != null) {
                                    //noinspection unchecked
                                    deserBean = (DeserBean<? super Object>) subtypeDeser;
                                    //noinspection unchecked
                                    objectType = (Class<? super Object>) subtypeDeser.introspection.getBeanType();
                                    readProperties =
                                            deserBean.readProperties != null ? new HashMap<>(deserBean.readProperties) : null;
                                    hasProperties = readProperties != null;
                                    anyValues = deserBean.anySetter != null ? new AnyValues<>(deserBean.anySetter) : null;
                                }
                            }
                            break;
                        } else {
                            tokenBuffer = initTokenBuffer(tokenBuffer, objectDecoder, key);
                        }
                    }

                } else {
                    while (true) {
                        final String key = objectDecoder.decodeKey();
                        if (key == null) {
                            break;
                        }

                        final DeserBean<?> subtypeBean = subtypes.get(key);
                        if (subtypeBean != null) {
                            if (!objectDecoder.decodeNull()) {
                                objectDecoder = objectDecoder.decodeObject();
                                deserBean = (DeserBean<? super Object>) subtypeBean;
                                //noinspection unchecked
                                objectType = (Class<? super Object>) subtypeBean.introspection.getBeanType();
                                readProperties =
                                        deserBean.readProperties != null ? new HashMap<>(deserBean.readProperties) : null;
                                hasProperties = readProperties != null;
                            }

                            break;
                        } else {
                            if (anyValues != null) {
                                tokenBuffer = initTokenBuffer(tokenBuffer, objectDecoder, key);
                            } else {
                                objectDecoder.skipValue();
                            }
                        }
                    }
                }

            }

            if (deserBean.creatorParams != null) {
                final Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> creatorParameters =
                        new HashMap<>(deserBean.creatorParams);
                int creatorSize = deserBean.creatorSize;
                Object[] params = new Object[creatorSize];
                PropertyBuffer buffer = initFromTokenBuffer(
                        tokenBuffer,
                        creatorParameters,
                        readProperties,
                        anyValues,
                        decoderContext
                );

                while (true) {
                    final String prop = objectDecoder.decodeKey();
                    if (prop == null) {
                        break;
                    }
                    final DeserBean.DerProperty<? super Object, ?> sp =
                            creatorParameters.remove(prop);
                    if (sp != null) {
                        if (sp.views != null && !decoderContext.hasView(sp.views)) {
                            objectDecoder.skipValue();
                            skipAliases(creatorParameters, sp);
                            continue;
                        }
                        @SuppressWarnings("unchecked") final Argument<Object> propertyType = (Argument<Object>) sp.argument;
                        final Object val = sp.deserializer.deserialize(
                                objectDecoder,
                                decoderContext,
                                propertyType
                        );
                        skipAliases(creatorParameters, sp);
                        if (sp.instrospection.getBeanType() == objectType) {
                            params[sp.index] = val;
                            if (hasProperties && readProperties.containsKey(prop)) {
                                // will need binding to properties as well
                                buffer = initBuffer(buffer, sp, prop, val);
                            }
                        } else {
                            buffer = initBuffer(buffer, sp, prop, val);
                        }
                        if (creatorParameters.isEmpty()) {
                            break;
                        }
                    } else if (hasProperties) {
                        final DeserBean.DerProperty<? super Object, ?> rp = readProperties.get(prop);
                        if (rp != null) {
                            @SuppressWarnings("unchecked") final Argument<Object> argument = (Argument<Object>) rp.argument;
                            final Object val = rp.deserializer.deserialize(
                                    objectDecoder,
                                    decoderContext,
                                    argument
                            );
                            buffer = initBuffer(buffer, rp, prop, val);
                        } else {
                            skipOrSetAny(
                                    decoderContext,
                                    objectDecoder,
                                    prop,
                                    anyValues,
                                    ignoreUnknown,
                                    type
                            );
                        }
                    } else {
                        skipOrSetAny(
                                decoderContext,
                                objectDecoder,
                                prop,
                                anyValues,
                                ignoreUnknown,
                                type
                        );
                    }
                }

                if (buffer != null && !creatorParameters.isEmpty()) {
                    for (PropertyBuffer propertyBuffer : buffer) {
                        final DeserBean.DerProperty<? super Object, ?> derProperty =
                                creatorParameters.remove(propertyBuffer.name);
                        if (derProperty != null) {
                            propertyBuffer.set(
                                    params,
                                    decoderContext
                            );
                            skipAliases(creatorParameters, derProperty);
                        }
                    }
                }

                if (!creatorParameters.isEmpty()) {
                    // set unsatisfied parameters to defaults or fail
                    for (DeserBean.DerProperty<? super Object, ?> sp : creatorParameters.values()) {
                        if (sp.unwrapped != null && buffer != null) {
                            final Object o = materializeFromBuffer(sp, buffer, decoderContext);
                            if (o == null) {
                                sp.setDefault(params);
                            } else {
                                params[sp.index] = o;
                            }
                        } else {
                            if (sp.isAnySetter && anyValues != null) {
                                anyValues.bind(params);
                                anyValues = null;
                            } else {
                                sp.setDefault(params);
                            }
                        }
                    }
                }

                try {
                    obj = deserBean.introspection.instantiate(params);
                } catch (InstantiationException e) {
                    throw new SerdeException("Unable to deserialize type [" + type + "]:" + e.getMessage(), e);
                }
                if (hasProperties) {

                    if (buffer != null) {
                        for (PropertyBuffer propertyBuffer : buffer) {
                            final DeserBean.DerProperty<? super Object, ?> derProperty =
                                    readProperties.remove(propertyBuffer.name);
                            if (derProperty != null) {
                                if (derProperty.instrospection.getBeanType() == objectType) {
                                    propertyBuffer.set(obj, decoderContext);
                                }
                                skipAliases(readProperties, derProperty);
                            }
                        }
                    }
                    if (!readProperties.isEmpty()) {
                        // more properties still to be read
                        buffer = decodeProperties(
                                deserBean,
                                decoderContext,
                                obj,
                                objectDecoder,
                                readProperties,
                                deserBean.unwrappedProperties,
                                buffer,
                                anyValues,
                                ignoreUnknown,
                                type
                        );
                    }

                    applyDefaultValuesOrFail(
                            obj,
                            readProperties,
                            deserBean.unwrappedProperties,
                            buffer,
                            decoderContext
                    );
                }
            } else {
                obj = deserBean.introspection.instantiate();
                if (hasProperties) {
                    final PropertyBuffer existingBuffer = initFromTokenBuffer(
                            tokenBuffer,
                            null,
                            readProperties,
                            anyValues,
                            decoderContext);
                    final PropertyBuffer propertyBuffer = decodeProperties(deserBean,
                                                                           decoderContext,
                                                                           obj,
                                                                           objectDecoder,
                                                                           readProperties,
                                                                           deserBean.unwrappedProperties,
                                                                           existingBuffer,
                                                                           anyValues,
                                                                           ignoreUnknown,
                                                                           type);
                    // the property buffer will be non-null if there were any unwrapped
                    // properties in which case we need to go through and materialize unwrapped
                    // from the buffer
                    applyDefaultValuesOrFail(
                            obj,
                            readProperties,
                            deserBean.unwrappedProperties,
                            propertyBuffer,
                            decoderContext
                    );
                } else if (anyValues != null && tokenBuffer != null) {
                    for (TokenBuffer buffer : tokenBuffer) {
                        anyValues.handle(
                                buffer.name,
                                buffer.decoder,
                                decoderContext
                        );
                    }
                }
            }
            // finish up
            while (true) {
                final String key = objectDecoder.decodeKey();
                if (key == null) {
                    break;
                }
                skipOrSetAny(decoderContext, objectDecoder, key, anyValues, ignoreUnknown, type);
            }
            if (anyValues != null) {
                anyValues.bind(obj);
            }
            objectDecoder.finishStructure();

            return obj;
        }
    }

    private void skipAliases(Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> props,
                           DeserBean.DerProperty<? super Object, ?> sp) {
        if (sp.aliases != null) {
            for (String alias : sp.aliases) {
                props.remove(alias);
            }
        }
    }

    private TokenBuffer initTokenBuffer(TokenBuffer tokenBuffer, Decoder objectDecoder, String key) throws IOException {
        return tokenBuffer == null ? new TokenBuffer(
                key,
                objectDecoder.decodeBuffer(),
                null
        ) : tokenBuffer.next(key, objectDecoder.decodeBuffer());
    }

    private @Nullable PropertyBuffer initFromTokenBuffer(@Nullable TokenBuffer tokenBuffer,
                                                         @Nullable Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> creatorParameters,
                                                         @Nullable Map<String, ? extends DeserBean.DerProperty<? super Object,
                                                                 ?>> readProperties,
                                                         @Nullable AnyValues<?> anyValues,
                                                         DecoderContext decoderContext) throws IOException {
        if (tokenBuffer != null) {
            PropertyBuffer propertyBuffer = null;
            for (TokenBuffer buffer : tokenBuffer) {
                final String n = buffer.name;
                if (creatorParameters != null && creatorParameters.containsKey(n)) {
                    final DeserBean.DerProperty<? super Object, ?> derProperty = creatorParameters.get(n);
                    propertyBuffer = initBuffer(
                            propertyBuffer,
                            derProperty,
                            n,
                            buffer.decoder
                    );
                } else if (readProperties != null && readProperties.containsKey(n)) {
                    final DeserBean.DerProperty<? super Object, ?> derProperty = readProperties.get(n);
                    propertyBuffer = initBuffer(
                            propertyBuffer,
                            derProperty,
                            n,
                            buffer.decoder
                    );
                } else if (anyValues != null) {
                    anyValues.handle(
                            buffer.name,
                            buffer.decoder,
                            decoderContext
                    );
                }
            }
            return propertyBuffer;
        }
        return null;
    }

    private PropertyBuffer initBuffer(PropertyBuffer buffer,
                                      DeserBean.DerProperty<? super Object, ?> rp,
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
            DeserBean<? super Object> introspection,
            DecoderContext decoderContext,
            Object obj,
            Decoder objectDecoder,
            Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> readProperties,
            DeserBean.DerProperty<? super Object, Object>[] unwrappedProperties,
            PropertyBuffer propertyBuffer,
            @Nullable AnyValues<?> anyValues,
            boolean ignoreUnknown,
            Argument<?> beanType) throws IOException {
        while (true) {
            final String prop = objectDecoder.decodeKey();
            if (prop == null) {
                break;
            }
            @SuppressWarnings("unchecked") final DeserBean.DerProperty<Object, Object> property =
                    (DeserBean.DerProperty<Object, Object>) readProperties.remove(prop);
            if (property != null && property.writer != null) {
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    objectDecoder.skipValue();
                    skipAliases(readProperties, property);
                    continue;
                }

                skipAliases(readProperties, property);
                final Argument<Object> propertyType = property.argument;
                boolean isNull = objectDecoder.decodeNull();
                if (isNull) {
                    if (propertyType.isNullable()) {
                        property.set(obj, null);
                    } else {
                        property.setDefault(obj);
                    }
                    continue;
                }
                final Object val = property.deserializer.deserialize(
                        objectDecoder,
                        decoderContext,
                        propertyType
                );

                // writer is never null for properties
                final BiConsumer<Object, Object> writer = property.writer;
                if (introspection.introspection == property.instrospection) {
                    writer.accept(obj, val);
                } else {
                    propertyBuffer = initBuffer(propertyBuffer, property, prop, val);
                }
                if (readProperties.isEmpty() && unwrappedProperties == null && introspection.anySetter == null) {
                    break;
                }
            } else {
                skipOrSetAny(
                        decoderContext,
                        objectDecoder,
                        prop,
                        anyValues,
                        ignoreUnknown,
                        beanType
                );
            }
        }
        return propertyBuffer;
    }

    private void skipOrSetAny(DecoderContext decoderContext,
                              Decoder objectDecoder,
                              String property,
                              @Nullable AnyValues<?> anyValues,
                              boolean ignoreUnknown,
                              Argument<?> type) throws IOException {
        if (anyValues != null) {
            anyValues.handle(
                    property,
                    objectDecoder,
                    decoderContext
            );
        } else {
            if (ignoreUnknown) {
                objectDecoder.skipValue();
            } else {
                throw new SerdeException("Unknown property [" + property + "] encountered during deserialization of type: " + type);
            }
        }
    }

    private void applyDefaultValuesOrFail(
            Object obj,
            Map<String, ? extends DeserBean.DerProperty<? super Object, ?>> readProperties,
            @Nullable DeserBean.DerProperty<? super Object, Object>[] unwrappedProperties,
            @Nullable PropertyBuffer buffer,
            DecoderContext decoderContext)
            throws IOException {
        if (ArrayUtils.isNotEmpty(unwrappedProperties)) {
            for (DeserBean.DerProperty<? super Object, Object> dp : unwrappedProperties) {
                if (dp.views != null && !decoderContext.hasView(dp.views)) {
                    continue;
                }
                if (buffer == null) {
                    dp.set(obj, null);
                } else {
                     Object v = materializeFromBuffer(dp, buffer, decoderContext);
                     dp.set(obj, v);
                }
            }
        }
        if (buffer != null && !readProperties.isEmpty()) {
            for (PropertyBuffer propertyBuffer : buffer) {
                final DeserBean.DerProperty<? super Object, ?> derProperty = readProperties.remove(propertyBuffer.name);
                if (derProperty != null) {
                    propertyBuffer.set(obj, decoderContext);
                }
            }
        }
        if (!readProperties.isEmpty()) {
            for (DeserBean.DerProperty<? super Object, ?> dp : readProperties.values()) {
                dp.setDefault(obj);
            }
        }
    }

    private @Nullable Object materializeFromBuffer(
                                         DeserBean.DerProperty<? super Object, ?> property,
                                         PropertyBuffer buffer,
                                         DecoderContext decoderContext) throws IOException {
        @SuppressWarnings("unchecked")
        final DeserBean<Object> unwrapped = (DeserBean<Object>) property.unwrapped;
        if (unwrapped != null) {
            final Map<String, ? extends DeserBean.DerProperty<?, ?>> creatorParams =
                    unwrapped.creatorParams;
            final Map<String, ? extends DeserBean.DerProperty<Object, Object>> readProperties = unwrapped.readProperties;

            Object object;
            if (creatorParams != null) {
                Object[] params = new Object[unwrapped.creatorSize];
                // handle construction
                for (DeserBean.DerProperty<?, ?> der : creatorParams.values()) {
                    boolean satisfied = false;
                    for (PropertyBuffer pb : buffer) {
                        if (pb.property == der) {
                            pb.set(params, decoderContext);
                            satisfied = true;
                            break;
                        }
                    }
                    if (!satisfied) {
                        if (der.defaultValue != null) {
                            params[der.index] = der.defaultValue;
                        } else if (der.required) {
                            throw new SerdeException("Unable to deserialize type [" + unwrapped.introspection.getBeanType() + "]. Required constructor parameter [" + der.argument + "] at index [" + der.index + "] is not present in supplied data");

                        }
                    }
                }
                object = unwrapped.introspection.instantiate(params);
            } else {
                object = unwrapped.introspection.instantiate();
            }

            if (readProperties != null) {
                for (DeserBean.DerProperty<Object, Object> der : readProperties.values()) {
                    boolean satisfied = false;
                    for (PropertyBuffer pb : buffer) {
                        if (pb.property == der) {
                            pb.set(object, decoderContext);
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

    @Override
    public <T> DeserBean<T> getDeserializableBean(Argument<T> type, DecoderContext decoderContext) {
        TypeKey key = new TypeKey(type);
        DeserBean<T> deserBean = (DeserBean<T>) deserBeanMap.get(key);
        if (deserBean == null) {
            try {
                final BeanIntrospection<T> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
                AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                        type.getAnnotationMetadata(),
                        deserializableIntrospection.getAnnotationMetadata()
                );
                if (annotationMetadata.hasAnnotation(SerdeConfig.Subtyped.class)) {
                    deserBean = new SubtypedDeserBean<>(annotationMetadata, deserializableIntrospection, decoderContext, this);
                } else  {
                    deserBean = new DeserBean<>(deserializableIntrospection, decoderContext, this);
                }
            } catch (SerdeException e) {
                throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
            }
            deserBeanMap.put(key, (DeserBean<Object>) deserBean);
        }
        return deserBean;
    }

    private static final class AnyValues<T> {
        Map<String, T> values;
        final DeserBean.AnySetter<T> anySetter;

        private AnyValues(DeserBean.AnySetter<T> anySetter) {
            this.anySetter = anySetter;
        }

        void handle(String property, Decoder objectDecoder, DecoderContext decoderContext) throws IOException {
            if (values == null) {
                 values = new LinkedHashMap<>();
            }
            if (objectDecoder.decodeNull()) {
                values.put(property, null);
            } else {
                if (anySetter.deserializer != null) {
                    values.put(property, anySetter.deserializer.deserialize(
                            objectDecoder,
                            decoderContext,
                            anySetter.valueType
                    ));
                } else {
                    //noinspection unchecked
                    values.put(property, (T) objectDecoder.decodeArbitrary());
                }
            }
        }

        void bind(Object obj) {
            if (values != null) {
                anySetter.bind(values, obj);
            }
        }
    }

    private static final class TokenBuffer implements Iterable<TokenBuffer> {
        final String name;
        final Decoder decoder;
        private final TokenBuffer next;

        private TokenBuffer(@NonNull String name, @NonNull Decoder decoder, @Nullable TokenBuffer next) {
            this.name = name;
            this.decoder = decoder;
            this.next = next;
        }

        TokenBuffer next(@NonNull String name, @NonNull Decoder decoder) {
            return new TokenBuffer(name, decoder, this);
        }

        @Override
        public Iterator<TokenBuffer> iterator() {
            return new Iterator<TokenBuffer>() {
                TokenBuffer thisBuffer = null;

                @Override
                public boolean hasNext() {
                    return thisBuffer == null || thisBuffer.next != null;
                }

                @Override
                public TokenBuffer next() {
                    if (thisBuffer == null) {
                        thisBuffer = TokenBuffer.this;
                    } else {
                        thisBuffer = thisBuffer.next;
                    }
                    return thisBuffer;
                }
            };
        }
    }

    private static final class PropertyBuffer implements Iterable<PropertyBuffer> {

        final DeserBean.DerProperty<Object, Object> property;
        final String name;
        private Object value;
        private final PropertyBuffer next;

        public PropertyBuffer(DeserBean.DerProperty<? super Object, ?> derProperty,
                              String name,
                              Object val,
                              @Nullable PropertyBuffer next) {
            //noinspection unchecked
            this.property = (DeserBean.DerProperty<? super Object, Object>) derProperty;
            this.name = name;
            this.value = val;
            this.next = next;
        }

        PropertyBuffer next(DeserBean.DerProperty<? super Object, ?> rp, String property, Object val) {
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

        public void set(Object obj, DecoderContext decoderContext) throws IOException {
            if (value instanceof Decoder) {
                value = property.deserializer.deserialize(
                        (Decoder) value,
                        decoderContext,
                        property.argument
                );
            }
            property.set(obj, value);
        }

        public void set(Object[] params, DecoderContext decoderContext) throws IOException {
            if (value instanceof Decoder) {
                value = property.deserializer.deserialize(
                        (Decoder) value,
                        decoderContext,
                        property.argument
                );

            }
            params[property.index] = value;
        }
    }

}
