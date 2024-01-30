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

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Map;

/**
 * Factory class for core serializers.
 */
@Factory
public final class CoreSerializers {

    @Singleton
    @Order(1000) // prioritize over character
    Serializer<String> stringSerializer() {
        return new Serializer<String>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Argument<? extends String> type, String value) throws IOException {
                encoder.encodeString(value);
            }

            @Override
            public boolean isEmpty(EncoderContext context, String value) {
                return value == null || value.isEmpty();
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.Character}.
     *
     * @return A Character serializer
     */
    @Singleton
    Serializer<Character> charSerializer() {
        return (encoder, context, type, value) -> encoder.encodeChar(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Boolean}.
     *
     * @return A boolean serializer
     */
    @Singleton
    Serializer<Boolean> booleanSerializer() {
        return (encoder, context, type, value) -> encoder.encodeBoolean(value);
    }

    /**
     * A serializer for maps.
     *
     * @param <K> The key type
     * @param <V> The value type
     * @return A bit decimal serializer
     */
    @Singleton
    <K, V> Serializer<Map<K, V>> mapSerializer() {
        return new CustomizedMapSerializer<>();
    }

}
