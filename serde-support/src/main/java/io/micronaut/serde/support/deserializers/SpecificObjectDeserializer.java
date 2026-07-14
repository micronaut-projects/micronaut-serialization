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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.reference.PropertyReference;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
final class SpecificObjectDeserializer implements UpdatingDeserializer<Object> {
    private static final String PREFIX_UNABLE_TO_DESERIALIZE_TYPE = "Unable to deserialize type [";
    private final Conf conf;
    private final DeserBean<? super Object> deserBean;

    public SpecificObjectDeserializer(boolean strictNullable,
                                      DeserBean<? super Object> deserBean,
                                      @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this(deserBean, new Conf(strictNullable, preInstantiateCallback));
    }

    SpecificObjectDeserializer(DeserBean<? super Object> deserBean, Conf conf) {
        this.deserBean = deserBean;
        this.conf = conf;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
        BeanDeserializer deserializer = newBeanDeserializer(null, deserBean, conf, false, false);
        deserializer.init(decoderContext);
        if (deserBean.externalProperties == null) {
            return requireNonNull(deserialize(decoder, decoderContext, type, deserializer), type);
        } else {
            return requireNonNull(deserializeAwaitForExternalProperties(decoder, decoderContext, type, deserializer), type);
        }
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type, Object value) throws IOException {
        if (deserBean.hasBuilder || deserBean.creatorParams != null) {
            throw unsupportedUpdate(type);
        }
        BeanDeserializer deserializer = newBeanDeserializer(value, deserBean, conf, false, true);
        deserializer.init(decoderContext);
        if (deserBean.externalProperties == null) {
            deserialize(decoder, decoderContext, type, deserializer);
        } else {
            deserializeAwaitForExternalProperties(decoder, decoderContext, type, deserializer);
        }
    }

    private static Object requireNonNull(@Nullable Object value, Argument<? super Object> type) throws SerdeException {
        if (value == null) {
            throw new SerdeException("Null value encountered during deserialization of type: " + type);
        }
        return value;
    }

    private static SerdeException unsupportedUpdate(Argument<?> type) {
        return new SerdeException("Unsupported deserialize into immutable [" + type + "]");
    }

    private @Nullable Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type, BeanDeserializer beanDeserializer) throws IOException {
        KeysAwareDecoder objectDecoder = KeysAwareDecoder.of(decoder.decodeObject(type));

        Object instance = null;
        boolean completed = false;
        while (true) {
            int keyIndex = objectDecoder.decodeKey(deserBean.propertyKeys);
            if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                completed = true;
                break;
            }
            if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                String propertyName = objectDecoder.decodeKey();
                if (propertyName == null) {
                    completed = true;
                    break;
                }
                if (!beanDeserializer.tryConsumeUnknown(propertyName, objectDecoder, decoderContext, type)) {
                    handleUnknownProperty(objectDecoder, propertyName, deserBean);
                }
            } else {
                if (deserBean.isIgnoredPropertyKey(keyIndex)) {
                    objectDecoder.skipValue();
                    continue;
                }
                if (!beanDeserializer.tryConsume(keyIndex, objectDecoder, decoderContext, type)) {
                    handleUnexpectedProperty(objectDecoder, keyIndex, deserBean);
                }
            }
            if (beanDeserializer.isAllConsumed()) {
                instance = beanDeserializer.provideInstance(type, decoderContext);
                break;
            }
        }

        if (instance == null) {
            instance = beanDeserializer.provideInstance(type, decoderContext);
        }
        if (deserBean.ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
            if (!completed) {
                while (true) {
                    int keyIndex = objectDecoder.decodeKey(deserBean.propertyKeys);
                    if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                        break;
                    }
                    if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                        String propertyName = objectDecoder.decodeKey();
                        if (propertyName == null) {
                            break;
                        }
                        handleUnknownProperty(objectDecoder, propertyName, deserBean);
                    } else {
                        handleUnexpectedProperty(objectDecoder, keyIndex, deserBean);
                    }
                }
            }
            objectDecoder.finishStructure();
        }
        return instance;
    }

    private @Nullable Object deserializeAwaitForExternalProperties(Decoder decoder,
                                                                   DecoderContext decoderContext,
                                                                   Argument<? super Object> type,
                                                                   BeanDeserializer beanDeserializer) throws IOException {
        Set<String> missingExternalProperties = deserBean.externalProperties == null ? Set.of() : new HashSet<>(deserBean.externalProperties);
        List<PropertyReference<?, ?>> references = new ArrayList<>(missingExternalProperties.size());
        Map<String, BufferedProperty> cache = new HashMap<>();

        final KeysAwareDecoder rootObjectDecoder = KeysAwareDecoder.of(decoder.decodeObject(type));
        try {
            Object instance = null;
            boolean completed = false;
            Iterator<BufferedProperty> cacheIterator = null;
            int keyIndex = Keys.UNKNOWN_KEY;
            while (true) {
                Decoder objectDecoder = rootObjectDecoder;
                DeserBean<?> sourceDeserBean = deserBean;

                final String propertyName;
                if (cacheIterator == null || !cacheIterator.hasNext()) {
                    keyIndex = rootObjectDecoder.decodeKey(deserBean.propertyKeys);
                    if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                        completed = true;
                        break;
                    }
                    if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                        propertyName = rootObjectDecoder.decodeKey();
                        if (propertyName == null) {
                            completed = true;
                            break;
                        }
                        keyIndex = Keys.UNKNOWN_KEY;
                    } else {
                        propertyName = deserBean.propertyKeyName(keyIndex);
                    }
                    if (deserBean.isIgnoredPropertyKey(keyIndex)) {
                        objectDecoder.skipValue();
                        continue;
                    }
                    if (!missingExternalProperties.isEmpty()) {
                        if (missingExternalProperties.remove(propertyName)) {
                            String externalPropertyValue;
                            if (deserBean.subtypeInfo != null && deserBean.subtypeInfo.info().discriminatorVisible()) {
                                Decoder cachedBuffer = decoder.decodeBuffer();
                                cache.put(propertyName, new BufferedProperty(deserBean, keyIndex, propertyName, cachedBuffer));
                                externalPropertyValue = cachedBuffer.decodeString();
                            } else {
                                externalPropertyValue = objectDecoder.decodeString();
                            }
                            PropertyReference<Object, String> reference = SubtypedExternalPropertyObjectDeserializer
                                .createExternalPropertyReference(decoderContext, propertyName, externalPropertyValue);
                            decoderContext.pushManagedRef(reference);
                            references.add(reference);

                            if (missingExternalProperties.isEmpty()) {
                                cacheIterator = cache.values().iterator();
                            }
                        } else {
                            cache.put(propertyName, new BufferedProperty(deserBean, keyIndex, propertyName, decoder.decodeBuffer()));
                        }
                        continue;
                    }
                } else {
                    BufferedProperty bufferedProperty = cacheIterator.next();
                    propertyName = bufferedProperty.propertyName;
                    objectDecoder = bufferedProperty.decoder;
                    sourceDeserBean = bufferedProperty.sourceDeserBean;
                    keyIndex = bufferedProperty.keyIndex;
                }

                boolean consumed;
                if (keyIndex == Keys.UNKNOWN_KEY) {
                    consumed = beanDeserializer.tryConsumeUnknown(propertyName, objectDecoder, decoderContext, type);
                } else {
                    int targetKeyIndex = targetKeyIndex(deserBean, sourceDeserBean, keyIndex);
                    consumed = targetKeyIndex == Keys.UNKNOWN_KEY
                        ? beanDeserializer.tryConsumeUnknown(propertyName, objectDecoder, decoderContext, type)
                        : beanDeserializer.tryConsume(targetKeyIndex, objectDecoder, decoderContext, type);
                }
                if (!consumed) {
                    if (keyIndex == Keys.UNKNOWN_KEY) {
                        handleUnknownProperty(objectDecoder, propertyName, deserBean);
                    } else {
                        int targetKeyIndex = targetKeyIndex(deserBean, sourceDeserBean, keyIndex);
                        if (targetKeyIndex == Keys.UNKNOWN_KEY) {
                            handleUnknownProperty(objectDecoder, propertyName, deserBean);
                        } else {
                            handleUnexpectedProperty(objectDecoder, targetKeyIndex, deserBean);
                        }
                    }
                }
                if (beanDeserializer.isAllConsumed()) {
                    instance = beanDeserializer.provideInstance(type, decoderContext);
                    break;
                }
            }

            if (instance == null) {
                instance = beanDeserializer.provideInstance(type, decoderContext);
            }
            if (deserBean.ignoreUnknown) {
                rootObjectDecoder.finishStructure(true);
            } else {
                if (deserBean.ignoredProperties != null && !completed) {
                    while (true) {
                        keyIndex = rootObjectDecoder.decodeKey(deserBean.propertyKeys);
                        if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                            break;
                        }
                        if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                            String propertyName = rootObjectDecoder.decodeKey();
                            if (propertyName == null) {
                                break;
                            }
                            handleUnknownProperty(rootObjectDecoder, propertyName, deserBean);
                        } else {
                            handleUnexpectedProperty(rootObjectDecoder, keyIndex, deserBean);
                        }
                    }
                }
                rootObjectDecoder.finishStructure();
            }
            return instance;
        } finally {
            for (int i = 0; i < references.size(); i++) {
                decoderContext.popManagedRef();
            }
        }
    }

    private static void handleUnexpectedProperty(Decoder objectDecoder,
                                                 int keyIndex,
                                                 String propertyName,
                                                 DeserBean<?> deserBean) throws IOException {
        if (keyIndex == Keys.UNKNOWN_KEY) {
            handleUnknownProperty(objectDecoder, propertyName, deserBean);
            return;
        }
        if (deserBean.ignoreUnknown || deserBean.isIgnoredPropertyKey(keyIndex)) {
            objectDecoder.skipValue();
        } else {
            Class<?> beanType = deserBean.introspection.getBeanType();
            if (deserBean.isKnownPropertyKey(keyIndex)) {
                throw duplicateProperty(propertyName, beanType);
            }
            throw unknownProperty(propertyName, deserBean.introspection.asArgument());
        }
    }

    private static void handleUnexpectedProperty(Decoder objectDecoder,
                                                 int keyIndex,
                                                 DeserBean<?> deserBean) throws IOException {
        if (deserBean.ignoreUnknown || deserBean.isIgnoredPropertyKey(keyIndex)) {
            objectDecoder.skipValue();
            return;
        }
        handleUnexpectedProperty(objectDecoder, keyIndex, deserBean.propertyKeyName(keyIndex), deserBean);
    }

    private static void handleUnknownProperty(Decoder objectDecoder,
                                              String propertyName,
                                              DeserBean<?> deserBean) throws IOException {
        if (deserBean.ignoreUnknown) {
            objectDecoder.skipValue();
        } else {
            throw unknownProperty(propertyName, deserBean.introspection.asArgument());
        }
    }

    private static SerdeException duplicateProperty(String propertyName, Class<?> beanType) {
        SerdeException serdeException = new SerdeException("Duplicate property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType, Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    private static SerdeException unknownProperty(String propertyName, Argument<?> beanType) {
        SerdeException serdeException = new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    private static BeanDeserializer newBeanDeserializer(@Nullable Object instance,
                                                        DeserBean<? super Object> db,
                                                        Conf conf,
                                                        boolean allowSubtype,
                                                        boolean updateMode) {
        if (db.hasBuilder) {
            return new BuilderDeserializer(db, conf);
        }
        if (allowSubtype && db.subtypeInfo != null) {
            if (db.subtypeInfo.info().deduct()) {
                return new SubtypedDeductionBeanDeserializer(db, db.subtypeInfo, conf);
            } else {
                SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = db.subtypeInfo.info().discriminatorType();
                return switch (discriminatorType) {
                    case PROPERTY, EXISTING_PROPERTY ->
                        new SubtypedPropertyBeanDeserializer(db, db.subtypeInfo, conf);
                    case WRAPPER_OBJECT -> new SubtypedWrapperBeanDeserializer(db, conf);
                    default ->
                        throw new IllegalStateException(discriminatorType + " not supported in this scenario!");
                };
            }
        }
        if (db.creatorParams != null) {
            return new ArgsConstructorBeanDeserializer(db, conf);
        }
        return new NoArgsConstructorDeserializer(instance, db, conf, updateMode);
    }

    private static boolean tryConsumeResolved(BeanDeserializer resolvedBeanDeserializer,
                                              DeserBean<?> targetDeserBean,
                                              DeserBean<?> sourceDeserBean,
                                              int sourceKeyIndex,
                                              String propertyName,
                                              Decoder decoder,
                                              DecoderContext decoderContext,
                                              Argument<? super Object> objectArgument) throws IOException {
        if (sourceKeyIndex == Keys.UNKNOWN_KEY) {
            return resolvedBeanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument);
        }
        int targetKeyIndex = targetKeyIndex(targetDeserBean, sourceDeserBean, sourceKeyIndex);
        if (targetKeyIndex == Keys.UNKNOWN_KEY) {
            return resolvedBeanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument);
        }
        return resolvedBeanDeserializer.tryConsume(targetKeyIndex, decoder, decoderContext, objectArgument);
    }

    private static void handleResolvedUnexpected(Decoder objectDecoder,
                                                 DeserBean<?> targetDeserBean,
                                                 DeserBean<?> sourceDeserBean,
                                                 int sourceKeyIndex,
                                                 String propertyName) throws IOException {
        if (sourceKeyIndex == Keys.UNKNOWN_KEY) {
            handleUnknownProperty(objectDecoder, propertyName, targetDeserBean);
            return;
        }
        int targetKeyIndex = targetKeyIndex(targetDeserBean, sourceDeserBean, sourceKeyIndex);
        if (targetKeyIndex == Keys.UNKNOWN_KEY) {
            handleUnknownProperty(objectDecoder, propertyName, targetDeserBean);
        } else {
            handleUnexpectedProperty(objectDecoder, targetKeyIndex, targetDeserBean);
        }
    }

    private static int targetKeyIndex(DeserBean<?> targetDeserBean,
                                      DeserBean<?> sourceDeserBean,
                                      int sourceKeyIndex) {
        return targetDeserBean.propertyKeys.indexOf(sourceDeserBean.propertyKeyName(sourceKeyIndex));
    }

    private static int[] targetKeyIndexes(DeserBean<?> targetDeserBean,
                                          DeserBean<?> sourceDeserBean) {
        int[] targetKeyIndexes = new int[sourceDeserBean.propertyKeyCount()];
        for (int i = 0; i < targetKeyIndexes.length; i++) {
            targetKeyIndexes[i] = targetKeyIndex(targetDeserBean, sourceDeserBean, i);
        }
        return targetKeyIndexes;
    }

    private static int targetKeyIndex(int[] targetKeyIndexes, int sourceKeyIndex) {
        return sourceKeyIndex >= 0 && sourceKeyIndex < targetKeyIndexes.length
            ? targetKeyIndexes[sourceKeyIndex]
            : Keys.UNKNOWN_KEY;
    }

    private static void deserializeAndSetPropertyValue(DecoderContext decoderContext,
                                                       Decoder objectDecoder,
                                                       DeserBean.DerProperty<Object, Object> derProperty,
                                                       Argument<? super Object> objectArgument,
                                                       Object instance) throws IOException {
        String managedRef = derProperty.managedRef;

        try {
            if (managedRef != null) {
                decoderContext.pushManagedRef(
                    new PropertyReference<>(
                        managedRef,
                        derProperty.introspection,
                        derProperty.argument,
                        instance
                    )
                );
            }
            Deserializer<Object> deserializer = Objects.requireNonNull(derProperty.deserializer);
            if (derProperty.unresolvedTypeVariableName != null) {
                deserializer = findTypeVariableDeserializer(decoderContext, objectArgument, derProperty, deserializer);
            }
            derProperty.deserializeAndSetPropertyValue(
                deserializer,
                objectDecoder,
                decoderContext,
                instance
            );
        } catch (InvalidFormatException e) {
            throw new InvalidPropertyFormatException(e, derProperty.argument);
        } finally {
            if (managedRef != null) {
                decoderContext.popManagedRef();
            }
        }
    }

    private static Deserializer<Object> findTypeVariableDeserializer(DecoderContext decoderContext,
                                                                     Argument<? super Object> objectArgument,
                                                                     DeserBean.DerProperty<Object, Object> property,
                                                                     Deserializer<Object> deserializer) throws SerdeException {
        Argument typeArgument = objectArgument.getTypeVariables().get(property.unresolvedTypeVariableName);
        if (typeArgument != null) {
            Deserializer genericDeserializer = decoderContext.findDeserializer(typeArgument)
                .createSpecific(decoderContext, typeArgument);
            deserializer = (Deserializer<Object>) genericDeserializer;
        }
        return deserializer;
    }

    /**
     * Deserializes unknown properties into the any values map.
     *
     * @author Denis Stepanov
     */
    private static final class AnyValuesDeserializer {

        private final DeserBean<?> deserBean;
        private final DeserBean.AnySetter anySetter;
        @Nullable
        private Map<String, Object> values;

        AnyValuesDeserializer(DeserBean<?> deserBean) {
            this.deserBean = deserBean;
            this.anySetter = Objects.requireNonNull(deserBean.anySetter);
        }

        void bind(Object instance) {
            Map<String, Object> resolvedValues = values;
            if (resolvedValues != null) {
                anySetter.bind(resolvedValues, instance);
            }
        }

        boolean tryConsume(String propertyName, Decoder decoder, DecoderContext decoderContext) throws IOException {
            if (values == null) {
                values = new LinkedHashMap<>();
            }
            Object value;
            if (decoder.decodeNull()) {
                value = null;
            } else {
                Argument<?> argument = Argument.OBJECT_ARGUMENT;
                try {
                    if (anySetter.deserializer != null) {
                        argument = anySetter.valueType;
                        value = anySetter.deserializer.deserializeNullable(
                            decoder,
                            decoderContext,
                            anySetter.valueType
                        );
                    } else {
                        value = decoder.decodeArbitrary();
                    }
                } catch (SerdeException e) {
                    e.getPath().add(ReferencePath.ofProperty(deserBean.introspection.getBeanType(), argument.withName(propertyName)));
                    throw e;
                }
            }
            values.put(propertyName, value);
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

        private final UnwrappedPropertyDeserializer @Nullable [] unwrappedProperties;

        CachedPropertiesValuesDeserializer(DeserBean<? super Object> db, Conf conf) {
            properties = Objects.requireNonNull(db.injectProperties);
            propertiesConsumer = properties.newConsumer();
            values = new Object[db.injectPropertiesSize];
            buffered = new Decoder[db.injectPropertiesSize];
            if (db.unwrappedProperties == null) {
                unwrappedProperties = null;
            } else {
                unwrappedProperties = new UnwrappedPropertyDeserializer[db.unwrappedProperties.length];
                for (int i = 0; i < db.unwrappedProperties.length; i++) {
                    unwrappedProperties[i] = new UnwrappedPropertyDeserializer(db, db.unwrappedProperties[i], conf);
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

        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            final DeserBean.DerProperty<Object, Object> property = propertiesConsumer.consumeKeyIndex(keyIndex);
            if (tryConsumeProperty(property, decoder, decoderContext)) {
                return true;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (unwrappedProperty.tryConsume(keyIndex, decoder, decoderContext, objectArgument)) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (unwrappedProperty.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean tryConsumeProperty(DeserBean.@Nullable DerProperty<Object, Object> property,
                                           Decoder decoder,
                                           DecoderContext decoderContext) throws IOException {
            if (property != null && property.beanProperty != null) {
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    decoder.skipValue();
                    return true;
                }
                if (property.managedRef == null && !property.merge) {
                    values[property.index] = property.deserializeValue(Objects.requireNonNull(property.deserializer), decoder, decoderContext);
                } else {
                    buffered[property.index] = decoder.decodeBuffer();
                }
                return true;
            }
            return false;
        }

        void injectProperties(Argument<? super Object> objectArgument,
                              Object instance,
                              DecoderContext decoderContext,
                              boolean applyDefaults) throws IOException {
            DeserBean.DerProperty<Object, Object>[] propertiesArray = properties.getPropertiesArray();
            for (int i = 0; i < propertiesArray.length; i++) {
                DeserBean.DerProperty<Object, Object> property = propertiesArray[i];
                if (property.unwrapped != null) {
                    continue;
                }
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    continue;
                }
                if (property.backRef != null) {
                    final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                        new PropertyReference<>(
                            property.backRef,
                            property.introspection,
                            property.argument,
                            null
                        )
                    );
                    Object value = null;
                    if (ref != null) {
                        value = ref.getReference();
                    }
                    property.set(decoderContext, instance, value);
                } else {
                    if (!propertiesConsumer.isConsumed(i)) {
                        if (applyDefaults) {
                            property.setDefaultPropertyValue(decoderContext, instance);
                        }
                        continue;
                    }
                    Decoder bufferedDecoder = buffered[i];
                    if (bufferedDecoder != null) {
                        deserializeAndSetPropertyValue(decoderContext, bufferedDecoder, property, objectArgument, instance);
                    } else {
                        property.set(decoderContext, instance, values[i]);
                    }
                }
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                    if (!propertiesConsumer.isConsumed(wrappedProperty.index) && !applyDefaults) {
                        continue;
                    }
                    if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                        continue;
                    }
                    wrappedProperty.set(
                        decoderContext,
                        instance,
                        unwrappedProperty.beanDeserializer.provideInstance(objectArgument, decoderContext)
                    );
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
        private final PropertiesBag<Object>.Consumer propertiesConsumer;

        private final UnwrappedPropertyDeserializer @Nullable [] unwrappedProperties;

        PropertiesValuesDeserializer(DeserBean<? super Object> db, Conf conf) {
            properties = Objects.requireNonNull(db.injectProperties);
            propertiesConsumer = properties.newConsumer();
            if (db.unwrappedProperties == null) {
                unwrappedProperties = null;
            } else {
                unwrappedProperties = new UnwrappedPropertyDeserializer[db.unwrappedProperties.length];
                for (int i = 0; i < db.unwrappedProperties.length; i++) {
                    unwrappedProperties[i] = new UnwrappedPropertyDeserializer(db, db.unwrappedProperties[i], conf);
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

        boolean tryConsumeAndSet(int keyIndex,
                                 Decoder decoder,
                                 DecoderContext decoderContext,
                                 Argument<? super Object> objectArgument,
                                 Object instance) throws IOException {
            final DeserBean.DerProperty<Object, Object> property = propertiesConsumer.consumeKeyIndex(keyIndex);
            if (tryConsumeAndSetProperty(property, decoder, decoderContext, objectArgument, instance)) {
                return true;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (unwrappedProperty.tryConsume(keyIndex, decoder, decoderContext, objectArgument)) {
                        if (unwrappedProperty.isAllConsumed()) {
                            DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                            if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                                continue;
                            }
                            propertiesConsumer.consume(wrappedProperty.index);
                            wrappedProperty.set(
                                decoderContext,
                                instance,
                                unwrappedProperty.beanDeserializer.provideInstance(objectArgument, decoderContext)
                            );
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        boolean tryConsumeUnknownAndSet(String propertyName,
                                        Decoder decoder,
                                        DecoderContext decoderContext,
                                        Argument<? super Object> objectArgument,
                                        Object instance) throws IOException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer up : unwrappedProperties) {
                    if (up.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                        if (up.isAllConsumed()) {
                            DeserBean.DerProperty<Object, Object> wrappedProperty = up.wrappedProperty;
                            if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                                continue;
                            }
                            propertiesConsumer.consume(wrappedProperty.index);
                            wrappedProperty.set(
                                decoderContext,
                                instance,
                                up.beanDeserializer.provideInstance(objectArgument, decoderContext)
                            );
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean tryConsumeAndSetProperty(DeserBean.@Nullable DerProperty<Object, Object> property,
                                                 Decoder decoder,
                                                 DecoderContext decoderContext,
                                                 Argument<? super Object> objectArgument,
                                                 Object instance) throws IOException {
            if (property != null) {
                if (property.views != null && !decoderContext.hasView(property.views)) {
                    decoder.skipValue();
                    return true;
                }
                if (property.backRef != null) {
                    final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                        new PropertyReference<>(
                            property.backRef,
                            property.introspection,
                            property.argument,
                            instance
                        )
                    );
                    Object value = null;
                    if (ref != null) {
                        value = ref.getReference();
                    }
                    property.set(decoderContext, instance, value);
                } else {
                    deserializeAndSetPropertyValue(decoderContext, decoder, property, objectArgument, instance);
                }
                return true;
            }
            return false;
        }

        void finalizeProperties(DecoderContext decoderContext,
                                Argument<? super Object> objectArgument,
                                Object instance,
                                boolean applyDefaults) throws IOException {
            if (applyDefaults && unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                    if (propertiesConsumer.isConsumed(wrappedProperty.index)) {
                        continue;
                    }
                    if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                        continue;
                    }
                    wrappedProperty.set(
                        decoderContext,
                        instance,
                        unwrappedProperty.beanDeserializer.provideInstance(objectArgument, decoderContext)
                    );
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
                if (applyDefaults) {
                    property.setDefaultPropertyValue(decoderContext, instance);
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
     * Deserializes the constructor values into an array to be used to instantiate the bean.
     *
     * @author Denis Stepanov
     */
    private static final class ConstructorValuesDeserializer {

        private final PropertiesBag<? super Object> parameters;
        private final PropertiesBag<Object>.Consumer creatorParameters;
        private final Object[] values;

        private final UnwrappedPropertyDeserializer @Nullable [] unwrappedProperties;
        @Nullable
        private final AnyValuesDeserializer anyValuesDeserializer;
        private boolean allConsumed;

        ConstructorValuesDeserializer(DeserBean<? super Object> db, Conf conf) {
            parameters = Objects.requireNonNull(db.creatorParams);
            creatorParameters = parameters.newConsumer();
            int creatorSize = db.creatorSize;
            values = new Object[creatorSize];
            if (db.creatorUnwrapped == null) {
                unwrappedProperties = null;
            } else {
                unwrappedProperties = new UnwrappedPropertyDeserializer[db.creatorUnwrapped.length];
                for (int i = 0; i < db.creatorUnwrapped.length; i++) {
                    unwrappedProperties[i] = new UnwrappedPropertyDeserializer(db, db.creatorUnwrapped[i], conf);
                }
            }
            if (db.anySetter == null || !db.anySetter.constructorArgument) {
                anyValuesDeserializer = null;
            } else {
                anyValuesDeserializer = new AnyValuesDeserializer(db);
            }
        }

        void init(DecoderContext decoderContext) throws SerdeException {
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    unwrappedProperty.beanDeserializer.init(decoderContext);
                }
            }
        }

        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (allConsumed) {
                return false;
            }
            final DeserBean.DerProperty<Object, Object> property = creatorParameters.consumeKeyIndex(keyIndex);
            if (tryConsumeProperty(property, decoder, decoderContext, objectArgument)) {
                return true;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (unwrappedProperty.tryConsume(keyIndex, decoder, decoderContext, objectArgument)) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (allConsumed) {
                return false;
            }
            if (unwrappedProperties != null) {
                for (UnwrappedPropertyDeserializer unwrappedProperty : unwrappedProperties) {
                    if (unwrappedProperty.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                        return true;
                    }
                }
            }
            if (anyValuesDeserializer != null) {
                return anyValuesDeserializer.tryConsume(propertyName, decoder, decoderContext);
            }
            return false;
        }

        private boolean tryConsumeProperty(DeserBean.@Nullable DerProperty<Object, Object> property,
                                           Decoder decoder,
                                           DecoderContext decoderContext,
                                           Argument<? super Object> objectArgument) throws IOException {
            if (property == null) {
                return false;
            }
            if (property.views != null && !decoderContext.hasView(property.views)) {
                decoder.skipValue();
                return true;
            }
            Object value;
            if (property.backRef != null) {
                final PropertyReference<? super Object, ?> ref = decoderContext.resolveReference(
                    new PropertyReference<>(
                        property.backRef,
                        property.introspection,
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
                Deserializer<Object> deserializer = Objects.requireNonNull(property.deserializer);
                if (property.unresolvedTypeVariableName != null) {
                    deserializer = findTypeVariableDeserializer(decoderContext, objectArgument, property, deserializer);
                }
                value = property.deserializeConstructorValue(deserializer, decoder, decoderContext);
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
                    DeserBean.DerProperty<Object, Object> wrappedProperty = unwrappedProperty.wrappedProperty;
                    Object value = unwrappedProperty.beanDeserializer.provideInstance(wrappedProperty.argument, decoderContext);
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
                            property.introspection,
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
        private final int[] keyIndexes;

        private UnwrappedPropertyDeserializer(DeserBean<?> parentDeserBean,
                                              DeserBean.DerProperty<Object, Object> unwrappedProperty,
                                              Conf conf) {
            this.wrappedProperty = unwrappedProperty;
            DeserBean<?> unwrappedBean = Objects.requireNonNull(unwrappedProperty.unwrapped);
            this.beanDeserializer = newBeanDeserializer(null, (DeserBean<? super Object>) unwrappedBean, conf, true, false);
            this.keyIndexes = new int[parentDeserBean.propertyKeyCount()];
            for (int i = 0; i < keyIndexes.length; i++) {
                if (!parentDeserBean.isKnownPropertyKey(i) && !parentDeserBean.isIgnoredPropertyKey(i)) {
                    keyIndexes[i] = unwrappedBean.propertyKeyIndexOf(parentDeserBean.propertyKeyName(i));
                } else {
                    keyIndexes[i] = Keys.UNKNOWN_KEY;
                }
            }
        }

        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                return false;
            }
            int unwrappedKeyIndex = unwrappedKeyIndex(keyIndex);
            if (unwrappedKeyIndex == Keys.UNKNOWN_KEY) {
                return false;
            }
            return beanDeserializer.tryConsume(unwrappedKeyIndex, decoder, decoderContext, objectArgument);
        }

        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (wrappedProperty.views != null && !decoderContext.hasView(wrappedProperty.views)) {
                return false;
            }
            return beanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument);
        }

        boolean isAllConsumed() {
            return beanDeserializer.isAllConsumed();
        }

        private int unwrappedKeyIndex(int keyIndex) {
            return keyIndex < keyIndexes.length ? keyIndexes[keyIndex] : Keys.UNKNOWN_KEY;
        }
    }

    /**
     * Deserializes a bean with a non-empty constructor.
     *
     * @author Denis Stepanov
     */
    private static final class ArgsConstructorBeanDeserializer extends BeanDeserializer {

        private final Conf conf;
        private final BeanIntrospection<Object> introspection;
        private final ConstructorValuesDeserializer constructorValuesDeserializer;
        @Nullable
        private final CachedPropertiesValuesDeserializer propertiesConsumer;
        @Nullable
        private final AnyValuesDeserializer anyValuesDeserializer;

        ArgsConstructorBeanDeserializer(DeserBean<? super Object> db, Conf conf) {
            this.conf = conf;
            this.introspection = db.introspection;
            constructorValuesDeserializer = new ConstructorValuesDeserializer(db, conf);
            if (db.injectProperties == null) {
                propertiesConsumer = null;
            } else {
                propertiesConsumer = new CachedPropertiesValuesDeserializer(db, conf);
            }
            if (db.anySetter == null) {
                anyValuesDeserializer = null;
            } else {
                anyValuesDeserializer = new AnyValuesDeserializer(db);
            }
        }

        @Override
        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (constructorValuesDeserializer.tryConsume(keyIndex, decoder, decoderContext, objectArgument)) {
                return true;
            }
            return propertiesConsumer != null && propertiesConsumer.tryConsume(keyIndex, decoder, decoderContext, objectArgument);
        }

        @Override
        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (constructorValuesDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                return true;
            }
            if (propertiesConsumer != null && propertiesConsumer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
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
        public @Nullable Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) throws IOException {
            Object instance;
            try {
                Object[] values = constructorValuesDeserializer.getValues(decoderContext);
                if (anyValuesDeserializer != null && anyValuesDeserializer.anySetter.constructorArgument) {
                    anyValuesDeserializer.bind(values);
                }
                if (conf.preInstantiateCallback != null) {
                    conf.preInstantiateCallback.preInstantiate(introspection, values);
                }
                if (objectArgument.isNullable() && allNull(values) && propertiesConsumer == null && anyValuesDeserializer == null) {
                    return null;
                }
                instance = introspection.instantiate(conf.strictNullable, values);
            } catch (InstantiationException e) {
                throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
            if (propertiesConsumer != null) {
                propertiesConsumer.injectProperties(objectArgument, instance, decoderContext, true);
            }
            if (anyValuesDeserializer != null && !anyValuesDeserializer.anySetter.constructorArgument) {
                anyValuesDeserializer.bind(instance);
            }
            return instance;
        }

        private boolean allNull(Object[] values) {
            for (Object value : values) {
                if (value != null) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Deserializes a bean with a no-args constructor.
     *
     * @author Denis Stepanov
     */
    private static final class NoArgsConstructorDeserializer extends BeanDeserializer {

        private final Conf conf;
        private final BeanIntrospection<Object> introspection;
        @Nullable
        private final PropertiesValuesDeserializer propertiesConsumer;
        @Nullable
        private final AnyValuesDeserializer anyValuesDeserializer;
        @Nullable
        private Object instance;
        private final boolean updateMode;

        NoArgsConstructorDeserializer(@Nullable Object instance,
                                      DeserBean<? super Object> db,
                                      Conf conf,
                                      boolean updateMode) {
            this.instance = instance;
            this.introspection = db.introspection;
            this.conf = conf;
            this.updateMode = updateMode;
            if (db.injectProperties != null) {
                this.propertiesConsumer = new PropertiesValuesDeserializer(db, conf);
            } else {
                this.propertiesConsumer = null;
            }
            if (db.anySetter == null) {
                anyValuesDeserializer = null;
            } else {
                anyValuesDeserializer = new AnyValuesDeserializer(db);
            }
        }

        @Override
        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            return propertiesConsumer != null
                && propertiesConsumer.tryConsumeAndSet(keyIndex, decoder, decoderContext, objectArgument, Objects.requireNonNull(instance));
        }

        @Override
        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (propertiesConsumer != null && propertiesConsumer.tryConsumeUnknownAndSet(propertyName, decoder, decoderContext, objectArgument, Objects.requireNonNull(instance))) {
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
                    if (conf.preInstantiateCallback != null) {
                        conf.preInstantiateCallback.preInstantiate(introspection, ArrayUtils.EMPTY_OBJECT_ARRAY);
                    }
                    instance = introspection.instantiate(ArrayUtils.EMPTY_OBJECT_ARRAY);
                } catch (InstantiationException e) {
                    throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
                }
            }
        }

        @Override
        public Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) throws IOException {
            if (propertiesConsumer != null) {
                propertiesConsumer.finalizeProperties(decoderContext, objectArgument, Objects.requireNonNull(instance), !updateMode);
            }
            if (anyValuesDeserializer != null) {
                anyValuesDeserializer.bind(Objects.requireNonNull(instance));
            }
            return Objects.requireNonNull(instance);
        }
    }

    /**
     * Deserializes a subtyped-bean with a property type resolution.
     *
     * @author Denis Stepanov
     */
    private static final class SubtypedPropertyBeanDeserializer extends BeanDeserializer {

        private final DeserBean<? super Object> deserBean;
        private final DeserBeanSubtypeInfo<? super Object> subtypeInfo;
        private final Conf conf;

        @Nullable
        private Map<String, BufferedProperty> buffer;
        @Nullable
        private BeanDeserializer beanDeserializer;
        @Nullable
        private DeserBean<?> resolvedDeserBean;
        private int @Nullable [] resolvedKeyIndexes;
        private final int discriminatorKeyIndex;

        SubtypedPropertyBeanDeserializer(DeserBean<? super Object> db,
                                         DeserBeanSubtypeInfo<? super Object> subtypeInfo,
                                         Conf conf) {
            this.deserBean = db;
            this.subtypeInfo = subtypeInfo;
            this.conf = conf;
            this.discriminatorKeyIndex = deserBean.propertyKeyIndexOf(subtypeInfo.info().discriminatorName());
        }

        @Override
        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (beanDeserializer != null) {
                DeserBean<?> resolvedDeserBean = Objects.requireNonNull(this.resolvedDeserBean);
                int targetKeyIndex = targetKeyIndex(Objects.requireNonNull(resolvedKeyIndexes), keyIndex);
                if (targetKeyIndex == Keys.UNKNOWN_KEY) {
                    String propertyName = deserBean.propertyKeyName(keyIndex);
                    if (!beanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                        handleUnknownProperty(decoder, propertyName, resolvedDeserBean);
                    }
                } else if (!beanDeserializer.tryConsume(targetKeyIndex, decoder, decoderContext, objectArgument)) {
                    handleUnexpectedProperty(decoder, targetKeyIndex, resolvedDeserBean);
                }
                return true;
            }
            if (discriminatorKeyIndex != Keys.UNKNOWN_KEY && keyIndex == discriminatorKeyIndex) {
                consumeDiscriminator(keyIndex, subtypeInfo.info().discriminatorName(), decoder, decoderContext, objectArgument);
            } else {
                bufferProperty(keyIndex, deserBean.propertyKeyName(keyIndex), decoder);
            }
            return true;
        }

        @Override
        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (beanDeserializer != null) {
                DeserBean<?> resolvedDeserBean = Objects.requireNonNull(this.resolvedDeserBean);
                if (!beanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                    handleUnknownProperty(decoder, propertyName, resolvedDeserBean);
                }
                return true;
            }
            if (subtypeInfo.info().discriminatorName().equals(propertyName)) {
                consumeDiscriminator(Keys.UNKNOWN_KEY, propertyName, decoder, decoderContext, objectArgument);
            } else {
                bufferProperty(Keys.UNKNOWN_KEY, propertyName, decoder);
            }
            return true;
        }

        private void consumeDiscriminator(int keyIndex,
                                          String propertyName,
                                          Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Object> objectArgument) throws IOException {
            Decoder bufferedDiscriminatorValue = null;
            String discriminatorValue;
            if (subtypeInfo.info().discriminatorVisible()) {
                bufferedDiscriminatorValue = decoder.decodeBuffer();
                discriminatorValue = bufferedDiscriminatorValue.decodeString();
            } else {
                discriminatorValue = decoder.decodeString();
            }
            DeserBean<?> deserBean = subtypeInfo.findDeserBean(discriminatorValue);
            createBeanDeserializerAndConsumeBuffer(decoder, decoderContext, objectArgument, deserBean);
            if (bufferedDiscriminatorValue != null) {
                BeanDeserializer resolvedBeanDeserializer = Objects.requireNonNull(beanDeserializer);
                boolean consumed = tryConsumeResolved(resolvedBeanDeserializer, deserBean, this.deserBean, keyIndex, propertyName, bufferedDiscriminatorValue, decoderContext, objectArgument);
                if (!consumed) {
                    handleResolvedUnexpected(decoder, deserBean, this.deserBean, keyIndex, propertyName);
                }
            }
        }

        private void bufferProperty(int keyIndex, String propertyName, Decoder decoder) throws IOException {
            if (buffer == null) {
                buffer = new LinkedHashMap<>();
            }
            buffer.put(propertyName, new BufferedProperty(deserBean, keyIndex, propertyName, decoder.decodeBuffer()));
        }

        private void createBeanDeserializerAndConsumeBuffer(Decoder decoder,
                                                            DecoderContext decoderContext,
                                                            Argument<? super Object> objectArgument,
                                                            DeserBean<?> deserBean) throws IOException {
            BeanDeserializer resolvedBeanDeserializer = newBeanDeserializer(
                null,
                (DeserBean<? super Object>) deserBean,
                conf,
                true,
                false);
            beanDeserializer = resolvedBeanDeserializer;
            resolvedDeserBean = deserBean;
            resolvedKeyIndexes = targetKeyIndexes(deserBean, this.deserBean);
            resolvedBeanDeserializer.init(decoderContext);
            if (buffer != null) {
                for (BufferedProperty bufferedProperty : buffer.values()) {
                    boolean consumed = tryConsumeResolved(
                        resolvedBeanDeserializer,
                        deserBean,
                        bufferedProperty.sourceDeserBean,
                        bufferedProperty.keyIndex,
                        bufferedProperty.propertyName,
                        bufferedProperty.decoder,
                        decoderContext,
                        objectArgument
                    );
                    if (!consumed) {
                        handleResolvedUnexpected(
                            decoder,
                            deserBean,
                            bufferedProperty.sourceDeserBean,
                            bufferedProperty.keyIndex,
                            bufferedProperty.propertyName
                        );
                    }
                }
                buffer = null;
            }
        }

        @Override
        boolean isAllConsumed() {
            if (beanDeserializer != null) {
                return beanDeserializer.isAllConsumed();
            }
            return false;
        }

        @Override
        void init(DecoderContext decoderContext) {
        }

        @Override
        public @Nullable Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) throws IOException {
            if (beanDeserializer == null) {
                return null;
            }
            return beanDeserializer.provideInstance(objectArgument, decoderContext);
        }
    }

    /**
     * Deserializes a subtype deduction.
     *
     * @author Denis Stepanov
     */
    private static final class SubtypedDeductionBeanDeserializer extends BeanDeserializer {

        private final DeserBean<? super Object> deserBean;
        private final Conf conf;
        private final Map<String, DeserBeanSubtypeInfo.SubtypeDef<?>> subtypes;

        @Nullable
        private Map<String, BufferedProperty> buffer;
        @Nullable
        private BeanDeserializer beanDeserializer;
        @Nullable
        private DeserBean<?> resolvedDeserBean;
        private int @Nullable [] resolvedKeyIndexes;

        SubtypedDeductionBeanDeserializer(DeserBean<? super Object> db,
                                          DeserBeanSubtypeInfo<? super Object> subtypeInfo,
                                          Conf conf) {
            this.deserBean = db;
            this.conf = conf;
            subtypes = new LinkedHashMap<>(subtypeInfo.subtypes());
        }

        @Override
        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (beanDeserializer != null) {
                DeserBean<?> resolvedDeserBean = Objects.requireNonNull(this.resolvedDeserBean);
                int targetKeyIndex = targetKeyIndex(Objects.requireNonNull(resolvedKeyIndexes), keyIndex);
                if (targetKeyIndex == Keys.UNKNOWN_KEY) {
                    String propertyName = deserBean.propertyKeyName(keyIndex);
                    if (!beanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                        handleUnknownProperty(decoder, propertyName, resolvedDeserBean);
                    }
                } else if (!beanDeserializer.tryConsume(targetKeyIndex, decoder, decoderContext, objectArgument)) {
                    handleUnexpectedProperty(decoder, targetKeyIndex, resolvedDeserBean);
                }
                return true;
            }
            return tryConsumeName(keyIndex, deserBean.propertyKeyName(keyIndex), decoder, decoderContext, objectArgument);
        }

        @Override
        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            if (beanDeserializer != null) {
                DeserBean<?> resolvedDeserBean = Objects.requireNonNull(this.resolvedDeserBean);
                if (!beanDeserializer.tryConsumeUnknown(propertyName, decoder, decoderContext, objectArgument)) {
                    handleUnknownProperty(decoder, propertyName, resolvedDeserBean);
                }
                return true;
            }
            return tryConsumeName(Keys.UNKNOWN_KEY, propertyName, decoder, decoderContext, objectArgument);
        }

        private boolean tryConsumeName(int keyIndex,
                                       String propertyName,
                                       Decoder decoder,
                                       DecoderContext decoderContext,
                                       Argument<? super Object> objectArgument) throws IOException {
            Iterator<Map.Entry<String, DeserBeanSubtypeInfo.SubtypeDef<?>>> iterator = subtypes.entrySet().iterator();
            while (iterator.hasNext()) {
                DeserBean<?> subtype = iterator.next().getValue().deserBean();
                if (subtype == null) {
                    iterator.remove();
                    continue;
                }
                if (subtype.isKnownProperty(propertyName)) {
                    // Found property
                    continue;
                }
                // Not found
                iterator.remove();
            }
            if (subtypes.size() == 1) {
                DeserBean<?> subtypeDeserBean = subtypes.values().iterator().next().deserBean();
                createBeanDeserializerAndConsumeBuffer(decoder, decoderContext, objectArgument, Objects.requireNonNull(subtypeDeserBean));
                BeanDeserializer resolvedBeanDeserializer = Objects.requireNonNull(beanDeserializer);
                return tryConsumeResolved(resolvedBeanDeserializer, subtypeDeserBean, deserBean, keyIndex, propertyName, decoder, decoderContext, objectArgument);
            } else {
                if (buffer == null) {
                    buffer = new LinkedHashMap<>();
                }
                buffer.put(propertyName, new BufferedProperty(deserBean, keyIndex, propertyName, decoder.decodeBuffer()));
            }
            return true;
        }

        private void createBeanDeserializerAndConsumeBuffer(Decoder decoder,
                                                            DecoderContext decoderContext,
                                                            Argument<? super Object> argument,
                                                            DeserBean<?> deserBean) throws IOException {
            BeanDeserializer resolvedBeanDeserializer = newBeanDeserializer(
                null,
                (DeserBean<? super Object>) deserBean,
                conf,
                false,
                false);
            beanDeserializer = resolvedBeanDeserializer;
            resolvedDeserBean = deserBean;
            resolvedKeyIndexes = targetKeyIndexes(deserBean, this.deserBean);
            resolvedBeanDeserializer.init(decoderContext);
            if (buffer != null) {
                for (BufferedProperty bufferedProperty : buffer.values()) {
                    boolean consumed = tryConsumeResolved(
                        resolvedBeanDeserializer,
                        deserBean,
                        bufferedProperty.sourceDeserBean,
                        bufferedProperty.keyIndex,
                        bufferedProperty.propertyName,
                        bufferedProperty.decoder,
                        decoderContext,
                        argument
                    );
                    if (!consumed) {
                        handleResolvedUnexpected(
                            decoder,
                            deserBean,
                            bufferedProperty.sourceDeserBean,
                            bufferedProperty.keyIndex,
                            bufferedProperty.propertyName
                        );
                    }
                }
                buffer = null;
            }
        }

        @Override
        boolean isAllConsumed() {
            if (beanDeserializer != null) {
                return beanDeserializer.isAllConsumed();
            }
            return false;
        }

        @Override
        void init(DecoderContext decoderContext) {
        }

        @Override
        public @Nullable Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) throws IOException {
            if (beanDeserializer == null) {
                if (buffer != null) {
                    throw new SerdeException("Cannot deduct the subtype for bean " + objectArgument.getType().getName());
                }
                return null;
            }
            return beanDeserializer.provideInstance(objectArgument, decoderContext);
        }
    }

    /**
     * Deserializes a subtyped-bean with a wrapper type resolution.
     *
     * @author Denis Stepanov
     */
    private static final class SubtypedWrapperBeanDeserializer extends BeanDeserializer {

        private final DeserBean<? super Object> db;
        private final DeserBeanSubtypeInfo<? super Object> subtypeInfo;
        private final Conf conf;

        private boolean consumed;
        @Nullable
        private Object instance;

        SubtypedWrapperBeanDeserializer(DeserBean<? super Object> db, Conf conf) {
            this.db = db;
            this.subtypeInfo = Objects.requireNonNull(db.subtypeInfo);
            this.conf = conf;
        }

        @Override
        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            return tryConsumeName(db.propertyKeyName(keyIndex), decoder, decoderContext, objectArgument);
        }

        @Override
        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            return tryConsumeName(propertyName, decoder, decoderContext, objectArgument);
        }

        private boolean tryConsumeName(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            DeserBeanSubtypeInfo.SubtypeDef<?> subtype = subtypeInfo.subtypes().get(propertyName);
            DeserBean<?> subDeserBean = null;
            if (subtype == null) {
                if (subtypeInfo.defaultType() != null) {
                    subDeserBean = subtypeInfo.defaultType().deserBean();
                }
            } else {
                subDeserBean = subtype.deserBean();
            }
            if (subDeserBean == null) {
                subDeserBean = db;
            }
            SpecificObjectDeserializer deserializer = new SpecificObjectDeserializer(
                (DeserBean<? super Object>) subDeserBean,
                conf
            );
            instance = deserializer.deserialize(decoder, decoderContext, objectArgument);
            consumed = true;
            return true;
        }

        @Override
        boolean isAllConsumed() {
            return consumed;
        }

        @Override
        void init(DecoderContext decoderContext) {
        }

        @Override
        public @Nullable Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) {
            return instance;
        }
    }

    /**
     * Deserializes a bean using a builder.
     *
     * @author Denis Stepanov
     */
    private static final class BuilderDeserializer extends BeanDeserializer {

        private final Conf conf;
        private final BeanIntrospection<Object> introspection;
        private final PropertiesBag<? super Object>.Consumer propertiesConsumer;
        private BeanIntrospection.@Nullable Builder<? super Object> builder;

        BuilderDeserializer(DeserBean<? super Object> db, Conf conf) {
            this.introspection = db.introspection;
            this.conf = conf;
            this.propertiesConsumer = Objects.requireNonNull(db.injectProperties).newConsumer();
        }

        @Override
        boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException {
            final DeserBean.DerProperty<Object, Object> property = propertiesConsumer.consumeKeyIndex(keyIndex);
            if (property != null) {
                property.deserializeAndCallBuilder(decoder, decoderContext, Objects.requireNonNull(builder));
                return true;
            }
            return false;
        }

        @Override
        boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) {
            return false;
        }

        @Override
        boolean isAllConsumed() {
            return propertiesConsumer.isAllConsumed();
        }

        @Override
        void init(DecoderContext decoderContext) throws SerdeException {
            try {
                if (conf.preInstantiateCallback != null) {
                    conf.preInstantiateCallback.preInstantiate(introspection);
                }
                builder = introspection.builder();
            } catch (InstantiationException e) {
                throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
        }

        @Override
        public Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) throws IOException {
            try {
                return Objects.requireNonNull(builder).build();
            } catch (InstantiationException e) {
                throw new SerdeException(PREFIX_UNABLE_TO_DESERIALIZE_TYPE + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
        }
    }

    private record Conf(boolean strictNullable,
                        @Nullable
                        SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {

    }

    private record BufferedProperty(DeserBean<?> sourceDeserBean, int keyIndex, String propertyName, Decoder decoder) {

    }

    /**
     * The bean deserializes based on its shape.
     *
     * @author Denis Stepanov
     */
    private abstract static sealed class BeanDeserializer {

        abstract boolean tryConsume(int keyIndex, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException;

        abstract boolean tryConsumeUnknown(String propertyName, Decoder decoder, DecoderContext decoderContext, Argument<? super Object> objectArgument) throws IOException;

        abstract boolean isAllConsumed();

        abstract void init(DecoderContext decoderContext) throws SerdeException;

        @Nullable
        abstract Object provideInstance(Argument<? super Object> objectArgument, DecoderContext decoderContext) throws IOException;

    }

}
