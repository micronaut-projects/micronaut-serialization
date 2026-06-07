/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.jsonb;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import jakarta.inject.Singleton;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * JSON-B beans backed by the current Micronaut context.
 */
@Internal
@Factory
final class JsonbFactory {
    /**
     * Default JSON-B configuration.
     *
     * @return The configuration
     */
    @Singleton
    @Requires(missingBeans = JsonbConfig.class)
    JsonbConfig jsonbConfig(List<JsonbAdapter<?, ?>> adapters,
                            List<JsonbSerializer<?>> serializers,
                            List<JsonbDeserializer<?>> deserializers,
                            @Nullable PropertyVisibilityStrategy visibilityStrategy) {
        JsonbConfig config = new JsonbConfig();
        if (!adapters.isEmpty()) {
            config.withAdapters(adapters.toArray(JsonbAdapter[]::new));
        }
        if (!serializers.isEmpty()) {
            config.withSerializers(serializers.toArray(JsonbSerializer[]::new));
        }
        if (!deserializers.isEmpty()) {
            config.withDeserializers(deserializers.toArray(JsonbDeserializer[]::new));
        }
        if (visibilityStrategy != null) {
            config.setProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY, visibilityStrategy);
        }
        return config;
    }

    /**
     * JSON-B instance backed by the current Micronaut serialization mapper.
     *
     * @param config JSON-B configuration
     * @param beanContext The current bean context
     * @param objectMapper The current object mapper
     * @param serdeIntrospections The current serde introspections
     * @param serdeConfiguration The current serde configuration
     * @param serializationConfiguration The current serialization configuration
     * @param deserializationConfiguration The current deserialization configuration
     * @param jsonbConfiguration The JSON-B integration configuration
     * @return The JSON-B instance
     */
    @SuppressWarnings("unused")
    @Singleton
    Jsonb jsonb(JsonbConfig config,
                BeanContext beanContext,
                ObjectMapper objectMapper,
                SerdeIntrospections serdeIntrospections,
                SerdeConfiguration serdeConfiguration,
                SerializationConfiguration serializationConfiguration,
                DeserializationConfiguration deserializationConfiguration,
                JsonbConfiguration jsonbConfiguration) {
        if (jsonbConfiguration.isReflectionEnabled() || MicronautJsonbProvider.MicronautJsonb.hasReflectionOnlyFeatures(config)) {
            return MicronautJsonbReflectionProvider.create(
                config,
                beanContext,
                objectMapper,
                serdeIntrospections,
                serdeConfiguration,
                serializationConfiguration,
                deserializationConfiguration
            );
        }
        return new MicronautJsonbProvider.MicronautJsonb(
            config,
            objectMapper,
            serdeConfiguration,
            serializationConfiguration,
            deserializationConfiguration
        );
    }
}
