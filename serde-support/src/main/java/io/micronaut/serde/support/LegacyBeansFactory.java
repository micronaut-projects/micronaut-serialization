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
package io.micronaut.serde.support;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.serde.Serde;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.deserializers.ObjectDeserializer;
import io.micronaut.serde.support.deserializers.SerdeDeserializationPreInstantiateCallback;
import io.micronaut.serde.support.serdes.ObjectArraySerde;
import io.micronaut.serde.support.serializers.ObjectSerializer;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Factory for the legacy beans removed from the bean context.
 */
@Internal
@Factory
@BootstrapContextCompatible
final class LegacyBeansFactory {

    @Any
    @Prototype
    @BootstrapContextCompatible
    <T> Serde<T> provideSerde(InjectionPoint<Serde<T>> serdeInjectionPoint,
                                        BeanProvider<DefaultSerdeRegistry> serdeRegistry) {
        if (serdeInjectionPoint instanceof ArgumentInjectionPoint<?, ?> argumentInjectionPoint) {
            Argument typeParameter = argumentInjectionPoint.getArgument().getTypeParameters()[0];
            if (typeParameter.getType() == Object[].class) {
                return (Serde<T>) new ObjectArraySerde();
            }
            DefaultSerdeRegistry defaultSerdeRegistry = serdeRegistry.get();
            return defaultSerdeRegistry.findInternalSerde(typeParameter);
        }
        return null;
    }

    @Singleton
    @BootstrapContextCompatible
    ObjectSerializer provideObjectSerializer(BeanContext beanContext,
                                                       SerdeIntrospections introspections,
                                                       SerdeConfiguration serdeConfiguration,
                                                       SerializationConfiguration serializationConfiguration) {

        return new ObjectSerializer(
            introspections,
            serdeConfiguration,
            serializationConfiguration,
            beanContext);
    }

    @Singleton
    @BootstrapContextCompatible
    ObjectDeserializer provideObjectDeserializer(SerdeIntrospections introspections,
                                                           SerdeConfiguration serdeConfiguration,
                                                           DeserializationConfiguration deserializationConfiguration,
                                                           @Nullable SerdeDeserializationPreInstantiateCallback instantiateCallback) {
        return new ObjectDeserializer(introspections,
            deserializationConfiguration,
            serdeConfiguration,
            instantiateCallback
        );
    }

    @Singleton
    @BootstrapContextCompatible
    protected ObjectArraySerde provideObjectArraySerde() {
        return new ObjectArraySerde();
    }
}
