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
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Deserializes mutable bean-shaped objects through the runtime simple-object fast path.
 *
 * <p>This implementation is selected for beans that can be instantiated up front with the no-args constructor and then
 * populated through ordinary introspection properties. The shape is intentionally narrow: no creator parameters,
 * unwrapped properties, external type ids, subtypes, ignored property names, any-setters, views, aliases,
 * managed/back references, unresolved type variables, or properties from a different introspection. Those cases need
 * the more general {@link SpecificObjectDeserializer}; keeping them out of this class lets the hot loop avoid
 * remapping, secondary consumers, and per-property feature checks.</p>
 *
 * <p>The optimized representation assumes that the aggregate {@link Keys} index is also the property array index.
 * {@link KeysAwareDecoder#decodeKey(Keys)} therefore dispatches directly to
 * {@code localProperties[keyIndex]} without a name lookup or {@link PropertiesBag} consumer. Duplicate tracking and
 * default-value completion use a single {@code long} mask, so this path is limited to property sets that fit the
 * mask. {@code consumedProperties} starts with all non-property bits set and flips each matched property bit as it is
 * decoded; at the end, the remaining zero bits identify default values to apply.</p>
 *
 * <p>Unknown names are handled only after {@code decodeKey(Keys)} reports no match. Because the simple path excludes
 * aliases and remapped keys, an unknown name cannot later become a known property through another lookup. Duplicate
 * errors can therefore use the key index directly, and ignored unknown values can be skipped without consulting a
 * slower property model.</p>
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
final class SimpleObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private static final DeserBean.DerProperty<Object, Object>[] EMPTY_PROPERTIES = emptyProperties();

    private final boolean ignoreUnknown;
    private final boolean strictNullable;
    private final BeanIntrospection<Object> introspection;
    private final long propertiesMask;
    private final DeserBean.DerProperty<Object, Object>[] propertiesArray;
    private final Keys propertyKeys;
    private final List<String> propertyKeyNames;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    SimpleObjectDeserializer(boolean strictNullable,
                             DeserBean<? super Object> deserBean,
                             @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.ignoreUnknown = deserBean.ignoreUnknown;
        this.strictNullable = strictNullable;
        this.introspection = deserBean.introspection;
        this.propertyKeys = deserBean.propertyKeys;
        this.propertyKeyNames = deserBean.propertyKeyNames();
        PropertiesBag<Object> properties = deserBean.injectProperties;
        if (properties == null) {
            this.propertiesArray = EMPTY_PROPERTIES;
            this.propertiesMask = 0;
        } else {
            this.propertiesArray = properties.getPropertiesArray();
            this.propertiesMask = properties.propertiesMask();
        }
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

        deserializeInto(decoder, decoderContext, beanType, obj, true);

        return obj;
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object beanInstance)
            throws IOException {
        deserializeInto(decoder, decoderContext, beanType, beanInstance, false);
    }

    private void deserializeInto(Decoder decoder,
                                 DecoderContext decoderContext,
                                 Argument<? super Object> beanType,
                                 Object beanInstance,
                                 boolean applyDefaults) throws IOException {
        KeysAwareDecoder objectDecoder = KeysAwareDecoder.of(decoder.decodeObject(beanType));
        DeserBean.DerProperty<Object, Object>[] localProperties = propertiesArray;
        long consumedProperties = ~propertiesMask;
        while (true) {
            final int keyIndex = objectDecoder.decodeKey(propertyKeys);
            if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                if (applyDefaults) {
                    setDefaultPropertyValues(decoderContext, beanInstance, localProperties, consumedProperties);
                }
                objectDecoder.finishStructure();
                return;
            }
            if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                String propertyName = objectDecoder.decodeKey();
                if (propertyName == null) {
                    if (applyDefaults) {
                        setDefaultPropertyValues(decoderContext, beanInstance, localProperties, consumedProperties);
                    }
                    objectDecoder.finishStructure();
                    return;
                }
                if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw unknownProperty(beanType, propertyName);
                }
            } else {
                long propertyMask = 1L << keyIndex;
                if ((consumedProperties & propertyMask) == 0) {
                    consumedProperties |= propertyMask;
                    localProperties[keyIndex].deserializeAndSetSimplePropertyValue(objectDecoder, decoderContext, beanInstance);
                } else if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw duplicateProperty(beanType, propertyKeyNames.get(keyIndex));
                }
            }
        }
    }

    private static void setDefaultPropertyValues(DecoderContext decoderContext,
                                                 Object beanInstance,
                                                 DeserBean.DerProperty<Object, Object>[] localProperties,
                                                 long consumedProperties) throws IOException {
        long remainingProperties = ~consumedProperties;
        while (remainingProperties != 0) {
            int index = Long.numberOfTrailingZeros(remainingProperties);
            localProperties[index].setDefaultPropertyValue(decoderContext, beanInstance);
            remainingProperties &= remainingProperties - 1;
        }
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

    @SuppressWarnings({"unchecked"})
    private static DeserBean.DerProperty<Object, Object>[] emptyProperties() {
        return new DeserBean.DerProperty[0];
    }
}
