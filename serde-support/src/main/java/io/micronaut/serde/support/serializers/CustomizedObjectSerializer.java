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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fallback {@link Serializer} for general {@link Object} values. For deserialization, deserializes to
 * standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link List}.
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link JsonMapper#writeValue}{@code (gen, }{@link Object}{@code
 *     .class)}</li>
 * </ul>
 *
 * @param <T> The type to serialize
 */
@Internal
final class CustomizedObjectSerializer<T> implements ObjectSerializer<T> {
    private final SerBean<T> serBean;

    CustomizedObjectSerializer(SerBean<T> serBean) {
        this.serBean = serBean;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        Encoder childEncoder = encoder.encodeObject(type);
        serializeInto(childEncoder, context, type, value);
        childEncoder.finishStructure();
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        for (SerBean.SerProperty<T, Object> property : serBean.writeProperties) {
            try {
                final Object propertyValue = property.get(value);
                final Serializer<Object> serializer = Objects.requireNonNull(property.serializer);
                final String backRef = property.backRef;
                if (backRef != null) {
                    final PropertyReference<T, Object> ref = context.resolveReference(
                        new SerializationReference<>(backRef,
                            serBean.introspection,
                            property.argument,
                            propertyValue,
                            serializer)
                    );
                    if (ref == null) {
                        continue;
                    }
                }

                Serializer.EncoderContext propertyContext = context.withFeatures(property.featuresWith, property.featuresWithout);

                if (serBean.propertyFilter != null) {
                    if (!serBean.propertyFilter.shouldInclude(propertyContext, serializer, value, property.name, propertyValue)) {
                        continue;
                    }
                } else {
                    SerdeConfig.SerInclude include = property.include;
                    if (include == SerdeConfig.SerInclude.USE_DEFAULTS) {
                        include = propertyContext.getSerializationConfiguration().map(SerializationConfiguration::getInclusion).orElse(SerdeConfig.SerInclude.ALWAYS);
                    }
                    boolean skipped = switch (include) {
                        case ALWAYS, USE_DEFAULTS -> false;
                        case NON_NULL -> propertyValue == null;
                        case NON_ABSENT -> serializer.isAbsent(propertyContext, propertyValue);
                        case NON_DEFAULT -> serializer.isEmpty(propertyContext, propertyValue) || propertyValue != null && serializer.isDefault(propertyContext, propertyValue);
                        case NON_EMPTY -> serializer.isEmpty(propertyContext, propertyValue);
                        case NEVER -> true;
                    };
                    if (skipped) {
                        continue;
                    }
                }

                if (property.views != null && !propertyContext.hasView(property.views)) {
                    continue;
                }

                final String managedRef = property.managedRef;
                if (managedRef != null) {
                    propertyContext.pushManagedRef(
                        new SerializationReference<>(
                            managedRef,
                            serBean.introspection,
                            property.argument,
                            value,
                            serializer
                        )
                    );
                }
                try {
                    if (property.serializableInto) {
                        if (property.objectSerializer != null) {
                            if (propertyValue != null) {
                                property.objectSerializer.serializeInto(encoder, propertyContext, property.argument, propertyValue);
                            }
                        } else {
                            throw new SerdeException("Serializer for a property: " + property.name + " doesn't support serializing into an existing object");
                        }
                    } else {
                        encoder.encodeKey(property.name);
                        if (propertyValue == null) {
                            encoder.encodeNull();
                        } else {
                            serializer.serialize(encoder, propertyContext, property.argument, propertyValue);
                        }
                    }
                } finally {
                    if (managedRef != null) {
                        propertyContext.popManagedRef();
                    }
                }
            } catch (SerdeException e) {
                e.getPath().add(property.getReferencePath());
                throw e;
            } catch (Exception e) {
                SerdeException serdeException = new SerdeException("Error getting property [" + property.argument + "] of type [" + property.beanType + "]: " + e.getMessage(), e);
                serdeException.getPath().add(property.getReferencePath());
                throw serdeException;
            }
        }
    }

}
