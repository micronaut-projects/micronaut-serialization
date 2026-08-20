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
import java.util.Objects;

/**
 * Deserializes immutable record-like objects through the runtime constructor-only fast path.
 *
 * <p>This implementation is selected for objects whose JSON object can be bound entirely to creator parameters and
 * instantiated once, after all constructor values have been collected. The shape is deliberately simpler than
 * {@link SpecificObjectDeserializer}: no mutable/injected properties, unwrapped properties, external type ids,
 * subtypes, ignored property names, any-setters, views, aliases, managed/back references, unresolved type variables,
 * or properties from another introspection. Beans that need any of those features stay on the general path.</p>
 *
 * <p>Like {@link SimpleObjectDeserializer}, this fast path relies on identity key indexes. The aggregate
 * {@link Keys} index is also the constructor-parameter array index, so a decoded key can write straight to
 * {@code localConstructorParameters[keyIndex]} and the matching slot in the constructor argument array. This avoids a
 * {@link PropertiesBag} consumer, string lookup, alias remapping, and a second property-index conversion inside the
 * object loop.</p>
 *
 * <p>Seen tracking is represented as a single {@code long} mask. {@code consumedProperties} begins with all
 * non-creator bits set, then each decoded constructor property flips its bit. The loop can stop as soon as all
 * constructor bits are consumed; remaining JSON fields are relevant only for unknown or duplicate checks. Missing
 * creator values are filled from defaults by iterating the remaining zero bits before invoking
 * {@link BeanIntrospection#instantiate(boolean, Object...)}.</p>
 *
 * <p>The fast path does not support updating an existing instance because the object is immutable from the
 * deserializer's perspective: constructor values must be collected before the single instantiation step.</p>
 *
 * @author Denis Stepanov
 */
@Internal
final class SimpleRecordLikeObjectDeserializer implements Deserializer<Object>, UpdatingDeserializer<Object> {
    private final BeanIntrospection<Object> introspection;
    private final long propertiesMask;
    private final DeserBean.DerProperty<Object, Object>[] constructorParameters;
    private final Keys propertyKeys;
    private final List<String> propertyKeyNames;
    private final int valuesSize;
    private final boolean ignoreUnknown;
    private final boolean strictNullable;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    SimpleRecordLikeObjectDeserializer(boolean strictNullable,
                                       DeserBean<? super Object> deserBean,
                                       @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.introspection = deserBean.introspection;
        PropertiesBag<Object> properties = Objects.requireNonNull(deserBean.creatorParams);
        this.constructorParameters = properties.getPropertiesArray();
        this.propertiesMask = properties.propertiesMask();
        this.propertyKeys = deserBean.propertyKeys;
        this.propertyKeyNames = deserBean.propertyKeyNames();
        this.valuesSize = deserBean.creatorSize;
        this.preInstantiateCallback = preInstantiateCallback;
        this.ignoreUnknown = deserBean.ignoreUnknown;
        this.strictNullable = strictNullable;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType) throws IOException {
        final KeysAwareDecoder objectDecoder = KeysAwareDecoder.of(decoder.decodeObject(beanType));
        final DeserBean.DerProperty<Object, Object>[] localConstructorParameters = constructorParameters;
        final Object[] params = new Object[valuesSize];
        long consumedProperties = ~propertiesMask;
        boolean finished = false;
        while (consumedProperties != -1) {
            final int keyIndex = objectDecoder.decodeKey(propertyKeys);
            if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                finished = true;
                break;
            }
            if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                String propertyName = objectDecoder.decodeKey();
                if (propertyName == null) {
                    finished = true;
                    break;
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
                    localConstructorParameters[keyIndex].deserializeAndSetConstructorValue(objectDecoder, decoderContext, params);
                } else if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw duplicateProperty(beanType, propertyKeyNames.get(keyIndex));
                }
            }
        }
        long remainingProperties = ~consumedProperties;
        while (remainingProperties != 0) {
            int index = Long.numberOfTrailingZeros(remainingProperties);
            localConstructorParameters[index].setDefaultConstructorValue(decoderContext, params);
            remainingProperties &= remainingProperties - 1;
        }
        if (!finished && !ignoreUnknown) {
            int keyIndex = objectDecoder.decodeKey(propertyKeys);
            if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                String propertyName = objectDecoder.decodeKey();
                if (propertyName != null) {
                    throw unknownProperty(beanType, propertyName);
                }
            } else if (keyIndex != KeysAwareDecoder.MATCH_END_OBJECT) {
                throw duplicateProperty(beanType, propertyKeyNames.get(keyIndex));
            }
        }

        Object obj;
        try {
            if (preInstantiateCallback != null) {
                preInstantiateCallback.preInstantiate(introspection, params);
            }
            obj = introspection.instantiate(strictNullable, params);
        } catch (InstantiationException e) {
            throw new SerdeException("Unable to deserialize type [" + beanType + "]: " + e.getMessage(), e);
        }

        objectDecoder.finishStructure(true);
        return obj;
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

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> beanType, Object value)
        throws IOException {
        throw new SerdeException("Unsupported deserialize into immutable [" + beanType + "]");
    }

}
