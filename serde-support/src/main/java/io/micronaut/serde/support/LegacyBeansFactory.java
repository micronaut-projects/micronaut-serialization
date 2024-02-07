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
import io.micronaut.serde.support.serdes.InetAddressSerde;
import io.micronaut.serde.support.serdes.InstantSerde;
import io.micronaut.serde.support.serdes.LocalDateSerde;
import io.micronaut.serde.support.serdes.LocalDateTimeSerde;
import io.micronaut.serde.support.serdes.LocalTimeSerde;
import io.micronaut.serde.support.serdes.ObjectArraySerde;
import io.micronaut.serde.support.serdes.OffsetDateTimeSerde;
import io.micronaut.serde.support.serdes.YearSerde;
import io.micronaut.serde.support.serdes.ZonedDateTimeSerde;
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
    <S extends Serde<T>, T> S provideSerde(InjectionPoint<Serde<T>> serdeInjectionPoint,
                                           BeanProvider<DefaultSerdeRegistry> serdeRegistry) {
        if (serdeInjectionPoint instanceof ArgumentInjectionPoint<?, ?> argumentInjectionPoint) {
            Argument typeParameter = argumentInjectionPoint.getArgument().getTypeParameters()[0];
            if (typeParameter.getType() == Object[].class) {
                return (S) new ObjectArraySerde();
            }
            DefaultSerdeRegistry defaultSerdeRegistry = serdeRegistry.get();
            return (S) defaultSerdeRegistry.findInternalSerde(typeParameter);
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
    ObjectArraySerde provideObjectArraySerde() {
        return new ObjectArraySerde();
    }

    @Singleton
    @BootstrapContextCompatible
    InetAddressSerde inetAddressSerde(SerdeConfiguration serdeConfiguration) {
        return new InetAddressSerde(serdeConfiguration);
    }

    @Singleton
    @BootstrapContextCompatible
    InstantSerde instantSerde(SerdeConfiguration serdeConfiguration) {
        return new InstantSerde(serdeConfiguration);
    }

    @Singleton
    @BootstrapContextCompatible
    LocalDateSerde localDateSerde(SerdeConfiguration serdeConfiguration) {
        return new LocalDateSerde(serdeConfiguration);
    }

    @Singleton
    @BootstrapContextCompatible
    LocalDateTimeSerde localDateTimeSerde(SerdeConfiguration serdeConfiguration) {
        return new LocalDateTimeSerde(serdeConfiguration);
    }

    @Singleton
    @BootstrapContextCompatible
    LocalTimeSerde localTimeSerde(SerdeConfiguration serdeConfiguration) {
        return new LocalTimeSerde(serdeConfiguration);
    }

    @Singleton
    @BootstrapContextCompatible
    OffsetDateTimeSerde offsetDateTimeSerde(SerdeConfiguration serdeConfiguration) {
        return new OffsetDateTimeSerde(serdeConfiguration);
    }

    @Singleton
    @BootstrapContextCompatible
    YearSerde yearSerde() {
        return new YearSerde();
    }

    @Singleton
    @BootstrapContextCompatible
    ZonedDateTimeSerde zonedDateTimeSerde(SerdeConfiguration serdeConfiguration) {
        return new ZonedDateTimeSerde(serdeConfiguration);
    }
}
