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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @since 1.0.0
 */
final class SpecificObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private static final String PREFIX_UNABLE_TO_DESERIALIZE_TYPE = "Unable to deserialize type [";
    private final boolean ignoreUnknown;
    private final boolean strictNullable;
    private final DeserBean<? super Object> deserBean;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    public SpecificObjectDeserializer(boolean ignoreUnknown,
                                      boolean strictNullable,
                                      DeserBean<? super Object> deserBean,
                                      @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.ignoreUnknown = ignoreUnknown && deserBean.ignoreUnknown;
        this.strictNullable = strictNullable;
        this.deserBean = deserBean;
        this.preInstantiateCallback = preInstantiateCallback;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
            throws IOException {

        DeserBean<? super Object> db = this.deserBean;
        Class<? super Object> objectType = db.introspection.getBeanType();

        if (db.delegating) {
            if (db.creatorParams != null) {
                final PropertiesBag<Object>.Consumer creatorParams = db.creatorParams.newConsumer();
                final DeserBean.DerProperty<Object, Object> creator = creatorParams.getNotConsumed().iterator().next();
                final Object val = deserializeValue(decoderContext, decoder, creator, creator.argument, null);
                Object[] args = new Object[]  { val };
                if (preInstantiateCallback != null) {
                    preInstantiateCallback.preInstantiate(db.introspection, args);
                }
                return db.introspection.instantiate(strictNullable, args);
            } else {
                throw new IllegalStateException("At least one creator parameter expected");
            }
        } else {

            PropertiesBag<Object>.Consumer readProperties = db.readProperties != null ? db.readProperties.newConsumer() : null;
            boolean hasProperties = readProperties != null;

            Decoder wrapperObjectOuterDecoder = null; // for WRAPPER_OBJECT subtyping
            Decoder objectDecoder = decoder.decodeObject(type);
            TokenBuffer tokenBuffer = null;
            AnyValues<Object> anyValues = db.anySetter != null ? new AnyValues<>(db.anySetter) : null;
            Object obj;

            if (db.isSubtyped() && db instanceof SubtypedDeserBean<Object> subtypedDeserBean) {
                // subtyped binding required
                final String defaultImpl = subtypedDeserBean.defaultImpl;
                final String discriminatorName = subtypedDeserBean.discriminatorName;
                final Map<String, DeserBean<?>> subtypes = subtypedDeserBean.subtypes;
                final SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = subtypedDeserBean.discriminatorType;
                if (discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
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
                                    db = (DeserBean<? super Object>) subtypeDeser;
                                    //noinspection unchecked
                                    objectType = (Class<? super Object>) subtypeDeser.introspection.getBeanType();
                                    readProperties = db.readProperties != null ? db.readProperties.newConsumer() : null;
                                    hasProperties = readProperties != null;
                                    anyValues = db.anySetter != null ? new AnyValues<>(db.anySetter) : null;
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
                                wrapperObjectOuterDecoder = objectDecoder;
                                objectDecoder = objectDecoder.decodeObject(type);
                                db = (DeserBean<? super Object>) subtypeBean;
                                //noinspection unchecked
                                objectType = (Class<? super Object>) subtypeBean.introspection.getBeanType();
                                readProperties = db.readProperties != null ? db.readProperties.newConsumer() : null;
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

                if (defaultImpl != null && subtypedDeserBean == db) {
                    @SuppressWarnings("unchecked")
                    DeserBean<? super Object> defaultSubType = (DeserBean<? super Object>) subtypes.get(defaultImpl);
                    if (defaultSubType != null) {
                        db = defaultSubType;
                        objectType = defaultSubType.introspection.getBeanType();
                        readProperties = db.readProperties != null ? db.readProperties.newConsumer() : null;
                        hasProperties = readProperties != null;

                    }
                }

            }

            if (db.creatorParams != null) {
                final PropertiesBag<Object>.Consumer creatorParameters = db.creatorParams.newConsumer();
                int creatorSize = db.creatorSize;
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
                    final DeserBean.DerProperty<Object, Object> sp = creatorParameters.consume(prop);
                    boolean consumed = false;
                    if (sp != null) {
                        if (sp.views != null && !decoderContext.hasView(sp.views)) {
                            objectDecoder.skipValue();
                            continue;
                        }
                        final Object val = deserializeValue(decoderContext, objectDecoder, sp, sp.argument, null);
                        if (val == null) {
                            sp.setDefaultConstructorValue(decoderContext, params);
                            // Skip consume
                            continue;
                        }
                        if (sp.instrospection.getBeanType() == objectType) {
                            params[sp.index] = val;
                            if (hasProperties && readProperties.isNotConsumed(prop)) {
                                // will need binding to properties as well
                                buffer = initBuffer(buffer, sp, prop, val);
                            }
                        } else {
                            buffer = initBuffer(buffer, sp, prop, val);
                        }
                        if (creatorParameters.isAllConsumed()) {
                            break;
                        }
                        consumed = true;
                    } else if (hasProperties) {
                        final DeserBean.DerProperty<Object, Object> rp = readProperties.findNotConsumed(prop);
                        if (rp != null) {
                            if (rp.managedRef != null) {
                                tokenBuffer = initTokenBuffer(tokenBuffer, objectDecoder, prop);
                            } else {
                                final Object val = deserializeValue(decoderContext, objectDecoder, rp, rp.argument, null);
                                buffer = initBuffer(buffer, rp, prop, val);
                            }
                            consumed = true;
                        }
                    }
                    if (!consumed) {
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

                if (buffer != null && !creatorParameters.isAllConsumed()) {
                    for (PropertyBuffer propertyBuffer : buffer) {
                        final DeserBean.DerProperty<? super Object, ?> derProperty = creatorParameters.consume(propertyBuffer.name);
                        if (derProperty != null) {
                            propertyBuffer.set(
                                    params,
                                    decoderContext
                            );
                        }
                    }
                }

                if (!creatorParameters.isAllConsumed()) {
                    // set unsatisfied parameters to defaults or fail
                    for (DeserBean.DerProperty<Object, Object> sp : creatorParameters.getNotConsumed()) {
                        if (sp.backRef != null) {
                            final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                                    new PropertyReference<>(
                                            sp.backRef,
                                            sp.instrospection,
                                            sp.argument,
                                            null
                                    )
                            );
                            if (ref != null) {
                                final Object o = ref.getReference();
                                if (o == null) {
                                    sp.setDefaultConstructorValue(decoderContext, params);
                                } else {
                                    params[sp.index] = o;
                                }
                                continue;
                            }
                        }
                        if (sp.unwrapped != null && buffer != null) {
                            final Object o = materializeFromBuffer(sp, buffer, decoderContext);
                            if (o == null) {
                                sp.setDefaultConstructorValue(decoderContext, params);
                            } else {
                                params[sp.index] = o;
                            }
                        } else {
                            if (sp.isAnySetter && anyValues != null) {
                                anyValues.bind(params);
                                anyValues = null;
                            } else {
                                sp.setDefaultConstructorValue(decoderContext, params);
                            }
                        }
                    }
                }

                try {
                    if (preInstantiateCallback != null) {
                        preInstantiateCallback.preInstantiate(db.introspection, params);
                    }
                    obj = db.introspection.instantiate(strictNullable, params);
                } catch (InstantiationException e) {
                    throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + type + "]: " + e.getMessage(), e);
                }
                if (hasProperties) {
                    if (buffer != null) {
                        processPropertyBuffer(decoderContext, objectType, readProperties, obj, buffer);
                    }
                    if (tokenBuffer != null) {
                        buffer = initFromTokenBuffer(tokenBuffer, creatorParameters, readProperties, anyValues, decoderContext);
                        if (buffer != null) {
                            processPropertyBuffer(decoderContext, objectType, readProperties, obj, buffer);
                        }
                    }
                    if (!readProperties.isAllConsumed()) {
                        // more properties still to be read
                        buffer = decodeProperties(
                                db,
                                decoderContext,
                                obj,
                                objectDecoder,
                                readProperties,
                                db.unwrappedProperties == null,
                                buffer,
                                anyValues,
                                ignoreUnknown,
                                type
                        );
                    }

                    applyDefaultValuesOrFail(
                            obj,
                            readProperties,
                            db.unwrappedProperties,
                            buffer,
                            decoderContext
                    );
                }
            } else if (db.hasBuilder) {
                BeanIntrospection.Builder<? super Object> builder;
                try {
                    if (preInstantiateCallback != null) {
                        preInstantiateCallback.preInstantiate(db.introspection);
                    }
                    builder = db.introspection.builder();
                } catch (InstantiationException e) {
                    throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + type + "]: " + e.getMessage(), e);
                }
                if (hasProperties) {
                    if (tokenBuffer != null) {
                        for (TokenBuffer buffer : tokenBuffer) {
                            final DeserBean.DerProperty<Object, Object> property = readProperties.consume(buffer.name);
                            if (property != null) {
                                property.deserializeAndCallBuilder(buffer.decoder, decoderContext, builder);
                            } else {
                                skipOrSetAny(
                                    decoderContext,
                                    buffer.decoder,
                                    buffer.name,
                                    anyValues,
                                    ignoreUnknown,
                                    type
                                );
                            }
                        }
                    }
                    while (true) {
                        final String prop = objectDecoder.decodeKey();
                        if (prop == null) {
                            break;
                        }
                        final DeserBean.DerProperty<Object, Object> property = readProperties.consume(prop);
                        if (property != null) {
                            property.deserializeAndCallBuilder(objectDecoder, decoderContext, builder);
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
                }
                try {
                    obj = builder.build();
                } catch (InstantiationException e) {
                    throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + type + "]: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (preInstantiateCallback != null) {
                        preInstantiateCallback.preInstantiate(db.introspection);
                    }
                    obj = db.introspection.instantiate(strictNullable, ArrayUtils.EMPTY_OBJECT_ARRAY);
                } catch (InstantiationException e) {
                    throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + type + "]: " + e.getMessage(), e);
                }
                if (hasProperties) {
                    final PropertyBuffer existingBuffer = initFromTokenBuffer(
                            tokenBuffer,
                            null,
                            readProperties,
                            anyValues,
                            decoderContext);
                    final PropertyBuffer propertyBuffer = decodeProperties(db,
                                                                           decoderContext,
                                                                           obj,
                                                                           objectDecoder,
                                                                           readProperties,
                                                                           db.unwrappedProperties == null,
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
                            db.unwrappedProperties,
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
            finalizeObjectDecoder(decoderContext, type, ignoreUnknown, objectDecoder, anyValues, obj);

            if (wrapperObjectOuterDecoder != null) {
                wrapperObjectOuterDecoder.finishStructure(true);
            }

            return obj;
        }
    }

    @Override
    public Object deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    private void processPropertyBuffer(DecoderContext decoderContext,
                                       Class<? super Object> objectType,
                                       PropertiesBag<Object>.Consumer readProperties,
                                       Object obj,
                                       PropertyBuffer buffer) throws IOException {
        for (PropertyBuffer propertyBuffer : buffer) {
            final DeserBean.DerProperty<Object, Object> derProperty = readProperties.consume(propertyBuffer.name);
            if (derProperty != null) {
                if (derProperty.instrospection.getBeanType() == objectType) {
                    propertyBuffer.set(obj, decoderContext);
                }
            }
        }
    }

    private Object deserializeValue(DecoderContext decoderContext,
                                    Decoder objectDecoder,
                                    DeserBean.DerProperty<Object, Object> derProperty,
                                    Argument<Object> propertyType,
                                    Object constructedBean) throws IOException {
        final boolean hasRef = constructedBean != null && derProperty.managedRef != null;
        try {
            if (hasRef) {
                decoderContext.pushManagedRef(
                        new PropertyReference<>(
                                derProperty.managedRef,
                                derProperty.instrospection,
                                derProperty.argument,
                                constructedBean
                        )
                );
            }
            Deserializer<Object> deserializer = derProperty.deserializer;
            return deserializer.deserializeNullable(
                    objectDecoder,
                    decoderContext,
                    propertyType
            );
        } catch (InvalidFormatException e) {
            throw new InvalidPropertyFormatException(e, propertyType);
        } finally {
            if (hasRef) {
                decoderContext.popManagedRef();
            }
        }
    }

    private void finalizeObjectDecoder(DecoderContext decoderContext,
                           Argument<? super Object> type,
                           boolean ignoreUnknown,
                           Decoder objectDecoder,
                           AnyValues<Object> anyValues,
                           Object obj) throws IOException {
        if (anyValues == null && ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
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
                                                         @Nullable PropertiesBag<Object>.Consumer creatorParameters,
                                                         @Nullable PropertiesBag<Object>.Consumer readProperties,
                                                         @Nullable AnyValues<?> anyValues,
                                                         DecoderContext decoderContext) throws IOException {
        if (tokenBuffer != null) {
            PropertyBuffer propertyBuffer = null;
            for (TokenBuffer buffer : tokenBuffer) {
                final String n = buffer.name;
                if (creatorParameters != null) {
                    final DeserBean.DerProperty<Object, Object> derProperty = creatorParameters.findNotConsumed(n);
                    if (derProperty != null) {
                        propertyBuffer = initBuffer(
                            propertyBuffer,
                            derProperty,
                            n,
                            buffer.decoder
                        );
                        continue;
                    }
                }
                if (readProperties != null) {
                    final DeserBean.DerProperty<Object, Object> derProperty = readProperties.findNotConsumed(n);
                    if (derProperty != null) {
                        propertyBuffer = initBuffer(
                            propertyBuffer,
                            derProperty,
                            n,
                            buffer.decoder
                        );
                        continue;
                    }
                }
                if (anyValues != null) {
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

    private PropertyBuffer decodeProperties(
            DeserBean<? super Object> introspection,
            DecoderContext decoderContext,
            Object obj,
            Decoder objectDecoder,
            PropertiesBag<Object>.Consumer readProperties,
            boolean hasNoUnwrapped,
            PropertyBuffer propertyBuffer,
            @Nullable AnyValues<?> anyValues,
            boolean ignoreUnknown,
            Argument<?> beanType) throws IOException {
        while (true) {
            final String prop = objectDecoder.decodeKey();
            if (prop == null) {
                break;
            }
            final DeserBean.DerProperty<Object, Object> property = readProperties.consume(prop);
            if (property != null && (property.beanProperty != null)) {
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    objectDecoder.skipValue();
                    continue;
                }

                boolean isNull = objectDecoder.decodeNull();
                if (isNull) {
                    if (property.argument.isNullable()) {
                        property.set(obj, null);
                    } else {
                        property.setDefaultPropertyValue(decoderContext, obj);
                    }
                    continue;
                }
                final Object val = deserializeValue(decoderContext, objectDecoder, property, property.argument, obj);

                if (introspection.introspection == property.instrospection) {
                    property.set(obj, val);
                } else {
                    propertyBuffer = initBuffer(propertyBuffer, property, prop, val);
                }
                if (readProperties.isAllConsumed() && hasNoUnwrapped && introspection.anySetter == null) {
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
            PropertiesBag<? super Object>.Consumer readProperties,
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
        if (buffer != null && !readProperties.isAllConsumed()) {
            for (PropertyBuffer propertyBuffer : buffer) {
                final DeserBean.DerProperty<? super Object, ?> derProperty = readProperties.consume(propertyBuffer.name);
                if (derProperty != null) {
                    propertyBuffer.set(obj, decoderContext);
                }
            }
        }
        if (!readProperties.isAllConsumed()) {
            for (DeserBean.DerProperty<? super Object, Object> dp : readProperties.getNotConsumed()) {
                if (dp.backRef != null) {
                    final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                            new PropertyReference<>(
                                    dp.backRef,
                                    dp.instrospection,
                                    dp.argument,
                                    null
                            )
                    );
                    if (ref != null) {
                        final Object o = ref.getReference();
                        if (o == null) {
                            dp.setDefaultPropertyValue(decoderContext, obj);
                        } else {
                            dp.set(obj, o);
                        }
                    }
                } else {
                    dp.setDefaultPropertyValue(decoderContext, obj);
                }
            }
        }
    }

    private @Nullable Object materializeFromBuffer(DeserBean.DerProperty<Object, Object> property,
                                                   PropertyBuffer buffer,
                                                   DecoderContext decoderContext) throws IOException {
        final DeserBean<Object> unwrapped = property.unwrapped;
        if (unwrapped != null) {
            return materializeUnwrapped(buffer, decoderContext, unwrapped);
        }
        return null;
    }

    private Object materializeUnwrapped(PropertyBuffer buffer, DecoderContext decoderContext, DeserBean<Object> unwrapped) throws IOException {
        Object object;
        if (unwrapped.creatorParams != null) {
            Object[] params = new Object[unwrapped.creatorSize];
            // handle construction
            for (DeserBean.DerProperty<?, ?> der : unwrapped.creatorParams.getDerProperties()) {
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
                    } else if (der.mustSetField) {
                        throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + unwrapped.introspection.getBeanType() + "]. Required constructor parameter [" + der.argument + "] at index [" + der.index + "] is not present in supplied data");

                    }
                }
            }
            if (preInstantiateCallback != null) {
                preInstantiateCallback.preInstantiate(unwrapped.introspection, params);
            }
            object = unwrapped.introspection.instantiate(strictNullable, params);
        } else {
            if (preInstantiateCallback != null) {
                preInstantiateCallback.preInstantiate(unwrapped.introspection);
            }
            object = unwrapped.introspection.instantiate(strictNullable, ArrayUtils.EMPTY_OBJECT_ARRAY);
        }

        if (unwrapped.readProperties != null) {
            // nested unwrapped
            DeserBean.@Nullable DerProperty<Object, Object>[] nestedUnwrappedProperties = unwrapped.unwrappedProperties;
            if (nestedUnwrappedProperties != null) {
                for (DeserBean.DerProperty<Object, Object> nestedUnwrappedProperty : nestedUnwrappedProperties) {
                    DeserBean<Object> nested = nestedUnwrappedProperty.unwrapped;
                    Object o = materializeUnwrapped(buffer, decoderContext, nested);
                    nestedUnwrappedProperty.set(object, o);
                }
            }
            for (DeserBean.DerProperty<Object, Object> der : unwrapped.readProperties.getDerProperties()) {
                boolean satisfied = false;
                for (PropertyBuffer pb : buffer) {
                    DeserBean.DerProperty<Object, Object> property = pb.property;
                    if (property == der) {
                        if (property.instrospection == unwrapped.introspection) {
                            pb.set(object, decoderContext);
                            der.set(object, pb.value);
                        }
                        satisfied = true; // belongs to another bean, that bean will handle setting the property
                        break;
                    }
                }
                if (!satisfied) {
                    der.setDefaultPropertyValue(decoderContext, object);
                }
            }
        }
        return object;
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type, Object value)
            throws IOException {
        PropertiesBag<Object>.Consumer readProperties = deserBean.readProperties != null ? deserBean.readProperties.newConsumer() : null;
        boolean hasProperties = readProperties != null;
        boolean ignoreUnknown = this.ignoreUnknown && deserBean.ignoreUnknown;
        AnyValues<Object> anyValues = deserBean.anySetter != null ? new AnyValues<>(deserBean.anySetter) : null;
        final Decoder objectDecoder = decoder.decodeObject(type);
        if (hasProperties) {
            final PropertyBuffer propertyBuffer = decodeProperties(deserBean,
                                                                   decoderContext,
                                                                   value,
                                                                   objectDecoder,
                                                                   readProperties,
                                                                   deserBean.unwrappedProperties == null,
                                                                   null,
                                                                   anyValues,
                                                                   ignoreUnknown,
                                                                   type);
            // the property buffer will be non-null if there were any unwrapped
            // properties in which case we need to go through and materialize unwrapped
            // from the buffer
            applyDefaultValuesOrFail(
                    value,
                    readProperties,
                    deserBean.unwrappedProperties,
                    propertyBuffer,
                    decoderContext
            );
        }
        finalizeObjectDecoder(
                decoderContext,
                type,
                ignoreUnknown,
                objectDecoder,
                anyValues,
                value
        );
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
                    T deserializedValue = anySetter.deserializer.deserializeNullable(
                        objectDecoder,
                        decoderContext,
                        anySetter.valueType
                    );
                    values.put(property, deserializedValue);
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
            return new Iterator<SpecificObjectDeserializer.TokenBuffer>() {
                SpecificObjectDeserializer.TokenBuffer thisBuffer = null;

                @Override
                public boolean hasNext() {
                    return thisBuffer == null || thisBuffer.next != null;
                }

                @Override
                public SpecificObjectDeserializer.TokenBuffer next() throws NoSuchElementException {
                    if (thisBuffer == null) {
                        thisBuffer = SpecificObjectDeserializer.TokenBuffer.this;
                    } else {
                        thisBuffer = thisBuffer.next;
                    }
                    if (thisBuffer == null) {
                        throw new NoSuchElementException();
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
            return new Iterator<>() {
                SpecificObjectDeserializer.PropertyBuffer thisBuffer = null;

                @Override
                public boolean hasNext() {
                    return thisBuffer == null || thisBuffer.next != null;
                }

                @Override
                public SpecificObjectDeserializer.PropertyBuffer next() throws NoSuchElementException {
                    if (thisBuffer == null) {
                        thisBuffer = SpecificObjectDeserializer.PropertyBuffer.this;
                    } else {
                        thisBuffer = thisBuffer.next;
                    }

                    if (thisBuffer == null) {
                        throw new NoSuchElementException();
                    }
                    return thisBuffer;
                }
            };
        }

        public void set(Object obj, DecoderContext decoderContext) throws IOException {
            if (value instanceof Decoder decoder) {
                if (property.managedRef != null) {
                    decoderContext.pushManagedRef(
                            new PropertyReference<>(
                                    property.managedRef,
                                    property.instrospection,
                                    property.argument,
                                    obj
                            )
                    );
                }
                try {
                    value = property.deserializer.deserializeNullable(
                        decoder,
                            decoderContext,
                            property.argument
                    );
                } catch (InvalidFormatException e) {
                    throw new InvalidPropertyFormatException(
                            e,
                            property.argument
                    );
                } finally {
                    decoderContext.popManagedRef();
                }
            }
            property.set(obj, value);
        }

        public void set(Object[] params, DecoderContext decoderContext) throws IOException {
            if (value instanceof Decoder decoder) {
                try {
                    value = property.deserializer.deserializeNullable(
                        decoder,
                        decoderContext,
                        property.argument
                    );
                } catch (InvalidFormatException e) {
                    throw new InvalidPropertyFormatException(
                            e,
                            property.argument
                    );
                }

            }
            params[property.index] = value;
        }
    }

}
