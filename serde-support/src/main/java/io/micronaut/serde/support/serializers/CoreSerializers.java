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
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.SerializerRegistrar;

import java.util.function.Consumer;

/**
 * Core serializers.
 */
@Internal
public final class CoreSerializers {

    public static void register(SerializationConfiguration serializationConfiguration, Consumer<SerializerRegistrar<?>> consumer) {
        consumer.accept(new CustomizedMapSerializer<>());
        consumer.accept(new IterableSerializer<>());
        consumer.accept(new OptionalMultiValuesSerializer<>(serializationConfiguration));
        consumer.accept(new OptionalValuesSerializer<>());
        consumer.accept(new StreamSerializer<>());
    }

}
