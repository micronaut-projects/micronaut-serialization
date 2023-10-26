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
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @author Denis Stepanov
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
        BeanDeserializer consumer = newBeanDeserializer(null, deserBean, strictNullable, preInstantiateCallback);
        consumer.init(decoderContext);
        return deserialize(decoder, decoderContext, type, consumer);
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type, Object value) throws IOException {
        BeanDeserializer deserializer = newBeanDeserializer(value, deserBean, strictNullable, preInstantiateCallback);
        deserializer.init(decoderContext);
        deserialize(decoder, decoderContext, type, deserializer);
    }

    private Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type, BeanDeserializer beanDeserializer) throws IOException {
        Decoder objectDecoder = decoder.decodeObject(type);

        Object instance = null;
        while (true) {
            final String propertyName = objectDecoder.decodeKey();
            if (propertyName == null) {
                break;
            }
            boolean consumed = beanDeserializer.tryConsume(propertyName, objectDecoder, decoderContext);
            if (!consumed) {
                if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + type);
                }
            }
            if (beanDeserializer.isAllConsumed()) {
                instance = beanDeserializer.provideInstance(decoderContext);
                break;
            }
        }

        if (instance == null) {
            instance = beanDeserializer.provideInstance(decoderContext);
        }
        if (ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
            objectDecoder.finishStructure();
        }
        return instance;
    }

    @Override
    public Object deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    private static BeanDeserializer newBeanDeserializer(Object instance,
                                                        DeserBean<? super Object> db,
                                                        boolean strictNullable,
                                                        @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        if (db.hasBuilder) {
            return new BuilderDeserializer(db, preInstantiateCallback);
        }
        if (db.creatorParams != null) {
            return new ArgsConstructorBeanDeserializer(db, strictNullable, preInstantiateCallback);
        }
        return new NoArgsConstructorDeserializer(instance, db, strictNullable, preInstantiateCallback);
    }

    private static Object deserializeValue(DecoderContext decoderContext,
                                           Decoder objectDecoder,
                                           DeserBean.DerProperty<Object, Object> derProperty,
                                           Argument<Object> propertyType,
                                           Object instance) throws IOException {
        final boolean hasRef = instance != null && derProperty.managedRef != null;
        try {
            if (hasRef) {
                decoderContext.pushManagedRef(
                    new PropertyReference<>(
                        derProperty.managedRef,
                        derProperty.instrospection,
                        derProperty.argument,
                        instance
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

    /**
     * Deserializes unknown properties into the any values map.
     *
     * @author Denis Stepanov
     */
    private static final class AnyValuesDeserializer {

        private final DeserBean.AnySetter<Object> anySetter;
        private Map<String, Object> values;

        AnyValuesDeserializer(DeserBean.AnySetter<Object> anySetter) {
            this.anySetter = anySetter;
        }

        void bind(Object instance) {
            if (values != null) {
                anySetter.bind(values, instance);
            }
        }

        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            if (values == null) {
                values = new LinkedHashMap<>();
            }
            if (decoder.decodeNull()) {
                values.put(propertyName, null);
            } else {
                if (anySetter.deserializer != null) {
                    Object deserializedValue = anySetter.deserializer.deserializeNullable(
                        decoder,
                        decoderContext,
                        anySetter.valueType
                    );
                    values.put(propertyName, deserializedValue);
                } else {
                    values.put(propertyName, decoder.decodeArbitrary());
                }
            }
            return true;
        }
    }

    /**
     * Deserializes the properties into an array to be set later after the bean instance is created.
     *
     * @author Denis Stepanov
     */
    private static final class CachedPropertiesValuesDeserializer {

        private final PropertiesBag<? super Object> properties;
        private final PropertiesBag<Object>.Consumer propertiesConsumer;
        private final Object[] values;
        private final Decoder[] buffered;

        @Nullable
        private final UnwrappedPropertyDeserializer[] unwrappedProperties;

        CachedPropertiesValuesDeserializer(DeserBean<? super Object> db,
                                           boolean strictNullable,
                                           @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            properties = db.injectProperties;
            propertiesConsumer = properties.newConsumer();
            values = new Object[db.injectPropertiesSize];
            buffered = new Decoder[db.injectPropertiesSize];
            if (db.unwrappedProperties == null) {
                unwrappedProperties = null;
            } else {
                unwrappedProperties = new UnwrappedPropertyDeserializer[db.unwrappedProperties.length];
                for (int i = 0; i < db.unwrappedProperties.length; i++) {
                    unwrappedProperties[i] = new UnwrappedPropertyDeserializer(db.unwrappedProperties[i], strictNullable, preInstantiateCallback);
                }
            }
        }

        void init(DecoderContext decoderContext) throws SerdeException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    unwrappedProperty.beanDeserializer.init(decoderContext);
                }
            }
        }

        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            final DeserBean.DerProperty<Object, Object> property = propertiesConsumer.consume(propertyName);
            if (property != null && (property.beanProperty != null)) {
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    decoder.skipValue();
                    return true;
                }
                if (property.managedRef == null) {
                    values[property.index] = deserializeValue(decoderContext, decoder, property, property.argument, null);
                } else {
                    buffered[property.index] = decoder.decodeBuffer();
                }
                return true;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (unwrappedProperty.tryConsume(propertyName, decoder, decoderContext)) {
                        return true;
                    }
                }
            }
            return false;
        }

        void injectProperties(Object instance, DecoderContext decoderContext) throws IOException {
            DeserBean.DerProperty<Object, Object>[] propertiesArray = properties.getPropertiesArray();
            for (int i = 0; i < propertiesArray.length; i++) {
                DeserBean.DerProperty<Object, Object> property = propertiesArray[i];
                if (property.unwrapped != null) {
                    continue;
                }
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    continue;
                }
                Object value;
                if (property.backRef != null) {
                    final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                        new PropertyReference<>(
                            property.backRef,
                            property.instrospection,
                            property.argument,
                            null
                        )
                    );
                    if (ref != null) {
                        value = ref.getReference();
                    } else {
                        value = null;
                    }
                } else {
                    Decoder bufferedDecoder = buffered[i];
                    if (bufferedDecoder != null) {
                        value = deserializeValue(decoderContext, bufferedDecoder, property, property.argument, instance);
                    } else {
                        value = values[i];
                    }
                }
                if (value == null) {
                    property.setDefaultPropertyValue(decoderContext, instance);
                } else {
                    property.set(instance, value);
                }
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                    if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                        continue;
                    }
                    Object value = unwrappedProperty.beanDeserializer.provideInstance(decoderContext);
                    if (value == null) {
                        wrappedProperty.setDefaultPropertyValue(decoderContext, instance);
                    } else {
                        wrappedProperty.set(instance, value);
                    }
                }
            }
        }

        boolean isAllConsumed() {
            if (!propertiesConsumer.isAllConsumed()) {
                return false;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (!unwrappedProperty.isAllConsumed()) {
                        return false;
                    }

                }
            }
            return true;
        }
    }

    /**
     * Deserializes the properties and sets them directly into the bean instance.
     *
     * @author Denis Stepanov
     */
    private static final class PropertiesValuesDeserializer {

        private final PropertiesBag<? super Object> properties;
        @Nullable
        private final PropertiesBag<Object>.Consumer propertiesConsumer;

        @Nullable
        private final UnwrappedPropertyDeserializer[] unwrappedProperties;

        PropertiesValuesDeserializer(DeserBean<? super Object> db,
                                     boolean strictNullable,
                                     @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            properties = db.injectProperties;
            propertiesConsumer = properties.newConsumer();
            if (db.unwrappedProperties == null) {
                unwrappedProperties = null;
            } else {
                unwrappedProperties = new UnwrappedPropertyDeserializer[db.unwrappedProperties.length];
                for (int i = 0; i < db.unwrappedProperties.length; i++) {
                    unwrappedProperties[i] = new UnwrappedPropertyDeserializer(db.unwrappedProperties[i], strictNullable, preInstantiateCallback);
                }
            }
        }

        void init(DecoderContext decoderContext) throws SerdeException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    unwrappedProperty.beanDeserializer.init(decoderContext);
                }
            }
        }

        boolean tryConsumeAndSet(String propertyName, Decoder decoder, DecoderContext decoderContext, Object instance) throws IOException {
            final DeserBean.DerProperty<Object, Object> property = propertiesConsumer.consume(propertyName);
            if (property != null) {
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    decoder.skipValue();
                    return true;
                }
                Object value;
                if (property.backRef != null) {
                    final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                        new PropertyReference<>(
                            property.backRef,
                            property.instrospection,
                            property.argument,
                            instance
                        )
                    );
                    if (ref != null) {
                        value = ref.getReference();
                    } else {
                        value = null;
                    }
                } else {
                    value = deserializeValue(decoderContext, decoder, property, property.argument, instance);
                }
                if (value == null) {
                    property.setDefaultPropertyValue(decoderContext, instance);
                } else {
                    property.set(instance, value);
                }
                return true;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer up : unwrappedProperties) {
                    if (up.tryConsume(propertyName, decoder, decoderContext)) {
                        if (up.isAllConsumed()) {
                            DeserBean.DerProperty<Object, Object> wrappedProperty = up.wrappedProperty;
                            if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                                continue;
                            }
                            propertiesConsumer.consume(wrappedProperty.index);
                            Object wrappedInstance = up.beanDeserializer.provideInstance(decoderContext);
                            wrappedProperty.set(instance, wrappedInstance);
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        void finalize(DecoderContext decoderContext, Object instance) throws IOException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                    if (propertiesConsumer.isConsumed(wrappedProperty.index)) {
                        continue;
                    }
                    if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                        continue;
                    }
                    Object value = unwrappedProperty.beanDeserializer.provideInstance(decoderContext);
                    if (value == null) {
                        wrappedProperty.setDefaultPropertyValue(decoderContext, instance);
                    } else {
                        wrappedProperty.set(instance, value);
                    }
                }
            }
            DeserBean.DerProperty<Object, Object>[] propertiesArray = properties.getPropertiesArray();
            for (int i = 0; i < propertiesArray.length; i++) {
                if (propertiesConsumer.isConsumed(i)) {
                    continue;
                }
                DeserBean.DerProperty<Object, Object> property = propertiesArray[i];
                if (property.unwrapped != null) {
                    continue;
                }
                property.setDefaultPropertyValue(decoderContext, instance);
            }

        }

        boolean isAllConsumed() {
            if (!propertiesConsumer.isAllConsumed()) {
                return false;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (!unwrappedProperty.isAllConsumed()) {
                        return false;
                    }

                }
            }
            return true;
        }
    }

    /**
     * Deserializes the constructor values into an array to be used to instantiate the bean.
     *
     * @author Denis Stepanov
     */
    private static final class ConstructorValuesDeserializer {

        private final PropertiesBag<? super Object> parameters;
        private final PropertiesBag<Object>.Consumer creatorParameters;
        private final Object[] values;

        @Nullable
        private final UnwrappedPropertyDeserializer[] unwrappedProperties;
        @Nullable
        private final AnyValuesDeserializer anyValuesDeserializer;
        private boolean allConsumed;

        ConstructorValuesDeserializer(DeserBean<? super Object> db,
                                      boolean strictNullable,
                                      @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            parameters = db.creatorParams;
            creatorParameters = db.creatorParams.newConsumer();
            int creatorSize = db.creatorSize;
            values = new Object[creatorSize];
            if (db.creatorUnwrapped == null) {
                unwrappedProperties = null;
            } else {
                unwrappedProperties = new UnwrappedPropertyDeserializer[db.creatorUnwrapped.length];
                for (int i = 0; i < db.creatorUnwrapped.length; i++) {
                    unwrappedProperties[i] = new UnwrappedPropertyDeserializer(db.creatorUnwrapped[i], strictNullable, preInstantiateCallback);
                }
            }
            if (db.anySetter == null || !db.anySetter.constructorArgument) {
                anyValuesDeserializer = null;
            } else {
                anyValuesDeserializer = new AnyValuesDeserializer(db.anySetter);
            }
        }

        void init(DecoderContext decoderContext) throws SerdeException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    unwrappedProperty.beanDeserializer.init(decoderContext);
                }
            }
        }

        boolean consume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            if (allConsumed) {
                return false;
            }
            final DeserBean.DerProperty<Object, Object> property = creatorParameters.consume(propertyName);
            if (property == null) {
                if (unwrappedProperties != null) {
                    for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                        if (unwrappedProperty.tryConsume(propertyName, decoder, decoderContext)) {
                            return true;
                        }
                    }
                }
                if (anyValuesDeserializer != null) {
                    return anyValuesDeserializer.tryConsume(propertyName, decoder, decoderContext);
                }
                return false;
            }
            if (property.views != null && !decoderContext.hasView(property.views)) {
                decoder.skipValue();
                return true;
            }
            if (property.unwrapped != null) {
                return false;
            }
            Object value;
            if (property.backRef != null) {
                final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                    new PropertyReference<>(
                        property.backRef,
                        property.instrospection,
                        property.argument,
                        null
                    )
                );
                if (ref != null) {
                    value = ref.getReference();
                } else {
                    value = null;
                }
            } else {
                value = deserializeValue(decoderContext, decoder, property, property.argument, null);
            }
            if (value == null) {
                property.setDefaultConstructorValue(decoderContext, values);
            } else {
                values[property.index] = value;
            }
            return true;
        }

        boolean isAllConsumed() {
            if (allConsumed) {
                return true;
            }
            if (!creatorParameters.isAllConsumed()) {
                return false;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (!unwrappedProperty.isAllConsumed()) {
                        return false;
                    }

                }
            }
            allConsumed = true;
            return true;
        }

        Object[] getValues(DecoderContext decoderContext) throws IOException {
            if (anyValuesDeserializer != null) {
                anyValuesDeserializer.bind(values);
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    Object value = unwrappedProperty.beanDeserializer.provideInstance(decoderContext);
                    DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                    if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                        continue;
                    }
                    if (value == null) {
                        wrappedProperty.setDefaultConstructorValue(decoderContext, values);
                    } else {
                        values[wrappedProperty.index] = value;
                    }
                }
            }
            DeserBean.DerProperty<Object, Object>[] propertiesArray = parameters.getPropertiesArray();
            for (int i = 0; i < propertiesArray.length; i++) {
                if (creatorParameters.isConsumed(i)) {
                    continue;
                }
                DeserBean.DerProperty<Object, Object> property = propertiesArray[i];
                if (property.unwrapped != null) {
                    continue;
                }
                Object value = null;
                if (property.backRef != null) {
                    final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                        new PropertyReference<>(
                            property.backRef,
                            property.instrospection,
                            property.argument,
                            null
                        )
                    );
                    if (ref != null) {
                        value = ref.getReference();
                    }
                }
                if (value == null) {
                    property.setDefaultConstructorValue(decoderContext, values);
                } else {
                    values[i] = value;
                }
            }
            return values;
        }
    }

    /**
     * Deserializes the unwrapped properties into the wrapped bean.
     *
     * @author Denis Stepanov
     */
    private static final class UnwrappedPropertyDeserializer {

        private final DeserBean.DerProperty<Object, Object> wrappedProperty;
        private final BeanDeserializer beanDeserializer;

        private UnwrappedPropertyDeserializer(DeserBean.DerProperty<Object, Object> unwrappedProperty,
                                              boolean strictNullable,
                                              @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            this.wrappedProperty = unwrappedProperty;
            this.beanDeserializer = newBeanDeserializer(null, unwrappedProperty.unwrapped, strictNullable, preInstantiateCallback);
        }

        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                return false;
            }
            return beanDeserializer.tryConsume(propertyName, decoder, decoderContext);
        }

        boolean isAllConsumed() {
            return beanDeserializer.isAllConsumed();
        }
    }

    /**
     * Deserializes a bean with a non-empty constructor.
     *
     * @author Denis Stepanov
     */
    private static final class ArgsConstructorBeanDeserializer extends BeanDeserializer {

        private final boolean strictNullable;
        private @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback;
        private final BeanIntrospection<Object> introspection;
        private final ConstructorValuesDeserializer constructorValuesDeserializer;
        @Nullable
        private final CachedPropertiesValuesDeserializer propertiesConsumer;
        @Nullable
        private final AnyValuesDeserializer anyValuesDeserializer;

        ArgsConstructorBeanDeserializer(DeserBean<? super Object> db,
                                        boolean strictNullable,
                                        @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            this.strictNullable = strictNullable;
            this.preInstantiateCallback = preInstantiateCallback;
            this.introspection = db.introspection;
            constructorValuesDeserializer = new ConstructorValuesDeserializer(db, strictNullable, preInstantiateCallback);
            if (db.injectProperties == null) {
                propertiesConsumer = null;
            } else {
                propertiesConsumer = new CachedPropertiesValuesDeserializer(db, strictNullable, preInstantiateCallback);
            }
            if (db.anySetter == null) {
                anyValuesDeserializer = null;
            } else {
                anyValuesDeserializer = new AnyValuesDeserializer(db.anySetter);
            }
        }

        @Override
        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            if (constructorValuesDeserializer.consume(propertyName, decoder, decoderContext)) {
                return true;
            }
            if (propertiesConsumer != null && propertiesConsumer.tryConsume(propertyName, decoder, decoderContext)) {
                return true;
            }
            return anyValuesDeserializer != null && anyValuesDeserializer.tryConsume(propertyName, decoder, decoderContext);
        }

        @Override
        boolean isAllConsumed() {
            return anyValuesDeserializer == null && constructorValuesDeserializer.isAllConsumed() && (propertiesConsumer == null || propertiesConsumer.isAllConsumed());
        }

        @Override
        void init(DecoderContext decoderContext) throws SerdeException {
            constructorValuesDeserializer.init(decoderContext);
            if (propertiesConsumer != null) {
                propertiesConsumer.init(decoderContext);
            }
        }

        @Override
        public Object provideInstance(DecoderContext decoderContext) throws IOException {
            Object instance;
            try {
                Object[] values = constructorValuesDeserializer.getValues(decoderContext);
                if (anyValuesDeserializer != null && anyValuesDeserializer.anySetter.constructorArgument) {
                    anyValuesDeserializer.bind(values);
                }
                if (preInstantiateCallback != null) {
                    preInstantiateCallback.preInstantiate(introspection, values);
                }
                instance = introspection.instantiate(strictNullable, values);
            } catch (InstantiationException e) {
                throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
            if (propertiesConsumer != null) {
                propertiesConsumer.injectProperties(instance, decoderContext);
            }
            if (anyValuesDeserializer != null && !anyValuesDeserializer.anySetter.constructorArgument) {
                anyValuesDeserializer.bind(instance);
            }
            return instance;
        }
    }

    /**
     * Deserializes a bean with a no-args constructor.
     *
     * @author Denis Stepanov
     */
    private static final class NoArgsConstructorDeserializer extends BeanDeserializer {

        @Nullable
        private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;
        private final BeanIntrospection<Object> introspection;
        @Nullable
        private final PropertiesValuesDeserializer propertiesConsumer;
        @Nullable
        private final AnyValuesDeserializer anyValuesDeserializer;
        private Object instance;

        NoArgsConstructorDeserializer(Object instance,
                                      DeserBean<? super Object> db,
                                      boolean strictNullable,
                                      @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            this.instance = instance;
            this.introspection = db.introspection;
            this.preInstantiateCallback = preInstantiateCallback;
            if (db.injectProperties != null) {
                this.propertiesConsumer = new PropertiesValuesDeserializer(db, strictNullable, preInstantiateCallback);
            } else {
                this.propertiesConsumer = null;
            }
            if (db.anySetter == null) {
                anyValuesDeserializer = null;
            } else {
                anyValuesDeserializer = new AnyValuesDeserializer(db.anySetter);
            }
        }

        @Override
        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            if (propertiesConsumer != null && propertiesConsumer.tryConsumeAndSet(propertyName, decoder, decoderContext, instance)) {
                return true;
            }
            return anyValuesDeserializer != null && anyValuesDeserializer.tryConsume(propertyName, decoder, decoderContext);
        }

        @Override
        boolean isAllConsumed() {
            return anyValuesDeserializer == null && (propertiesConsumer == null || propertiesConsumer.isAllConsumed());
        }

        @Override
        void init(DecoderContext decoderContext) throws SerdeException {
            if (propertiesConsumer != null) {
                propertiesConsumer.init(decoderContext);
            }
            if (instance == null) {
                try {
                    if (preInstantiateCallback != null) {
                        preInstantiateCallback.preInstantiate(introspection, ArrayUtils.EMPTY_OBJECT_ARRAY);
                    }
                    instance = introspection.instantiate(ArrayUtils.EMPTY_OBJECT_ARRAY);
                } catch (InstantiationException e) {
                    throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
                }
            }
        }

        @Override
        public Object provideInstance(DecoderContext decoderContext) throws IOException {
            if (propertiesConsumer != null) {
                propertiesConsumer.finalize(decoderContext, instance);
            }
            if (anyValuesDeserializer != null) {
                anyValuesDeserializer.bind(instance);
            }
            return instance;
        }
    }

    /**
     * Deserializes a bean using a builder.
     *
     * @author Denis Stepanov
     */
    private static final class BuilderDeserializer extends BeanDeserializer {

        @Nullable
        private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;
        private final BeanIntrospection<Object> introspection;
        private final PropertiesBag<? super Object>.Consumer propertiesConsumer;
        private BeanIntrospection.Builder<? super Object> builder;

        BuilderDeserializer(DeserBean<? super Object> db,
                            @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
            this.introspection = db.introspection;
            this.preInstantiateCallback = preInstantiateCallback;
            this.propertiesConsumer = db.injectProperties.newConsumer();
        }

        @Override
        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            final DeserBean.DerProperty<Object, Object> property = propertiesConsumer.consume(propertyName);
            if (property != null) {
                property.deserializeAndCallBuilder(decoder, decoderContext, builder);
                return true;
            }
            return false;
        }

        @Override
        boolean isAllConsumed() {
            return propertiesConsumer.isAllConsumed();
        }

        @Override
        void init(DecoderContext decoderContext) throws SerdeException {
            try {
                if (preInstantiateCallback != null) {
                    preInstantiateCallback.preInstantiate(introspection);
                }
                builder = introspection.builder();
            } catch (InstantiationException e) {
                throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
        }

        @Override
        public Object provideInstance(DecoderContext decoderContext) throws IOException {
            try {
                return builder.build();
            } catch (InstantiationException e) {
                throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
        }
    }

    /**
     * The bean deserializes based on it's shape.
     *
     * @author Denis Stepanov
     */
    private static abstract sealed class BeanDeserializer {

        abstract boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException;

        abstract boolean isAllConsumed();

        abstract void init(DecoderContext decoderContext) throws SerdeException;

        abstract Object provideInstance(DecoderContext decoderContext) throws IOException;

    }

}
