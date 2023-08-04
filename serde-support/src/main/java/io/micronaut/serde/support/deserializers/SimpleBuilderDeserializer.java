/*
 * Copyright 2017-2023 original authors
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

import java.io.IOException;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;

final class SimpleBuilderDeserializer implements Deserializer<Object> {
    private final PropertiesBag<Object> builderParameters;
    private final BeanIntrospection<Object> introspection;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;
    private final boolean ignoreUnknown;
    private final boolean strictNullable;

    SimpleBuilderDeserializer(
        PropertiesBag<Object> builderParameters,
        BeanIntrospection<Object> introspection,
        SerdeDeserializationPreInstantiateCallback preInstantiateCallback, boolean ignoreUnknown, boolean strictNullable) {
        this.builderParameters = builderParameters;
        this.introspection = introspection;
        this.preInstantiateCallback = preInstantiateCallback;
        this.ignoreUnknown = ignoreUnknown;
        this.strictNullable = strictNullable;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        BeanIntrospection.Builder<Object> builder = introspection.builder();
        Decoder objectDecoder = decoder.decodeObject(type);

        if (builderParameters != null) {
            PropertiesBag<Object>.Consumer propertiesConsumer = builderParameters.newConsumer();

            boolean allConsumed = false;
            while (!allConsumed) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                final DeserBean.DerProperty<Object, Object> consumedProperty = propertiesConsumer.consume(prop);
                if (consumedProperty != null) {
                    consumedProperty.deserializeAndCallBuilder(objectDecoder, context, builder);
                    allConsumed = propertiesConsumer.isAllConsumed();

                } else if (ignoreUnknown) {
                    objectDecoder.skipValue();
                } else {
                    throw unknownProperty(type, prop);
                }
            }
        }

        if (ignoreUnknown) {
            objectDecoder.finishStructure(true);
        } else {
            String unknownProp = objectDecoder.decodeKey();
            if (unknownProp != null) {
                throw unknownProperty(type, unknownProp);
            }
            objectDecoder.finishStructure();
        }

        return builder.build();
    }

    @Override
    public Object deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    private SerdeException unknownProperty(Argument<? super Object> beanType, String prop) {
        return new SerdeException("Unknown property [" + prop + "] encountered during deserialization of type: " + beanType);
    }
}
