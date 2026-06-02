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

import io.micronaut.core.type.Argument;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * JSON-B deserializer callback context backed by the cloned Micronaut runtime mapper.
 * <p>
 * Configured JSON-B deserializers can recursively deserialize nested values through this context
 * while retaining the same JSON-B runtime-introspection and fallback configuration.
 *
 * @param codec The bounded runtime codec configured with JSON-B introspection support
 */
record JsonbDeserializationContext(JsonbFallbackCodec codec) implements DeserializationContext {

    @Override
    public <T> T deserialize(Class<T> type, jakarta.json.stream.JsonParser parser) {
        return deserialize((Type) type, parser);
    }

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T> T deserialize(Type type, jakarta.json.stream.JsonParser parser) {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) codec.readValue(JsonbJsonpBridge.parseNext(parser), Argument.of(type));
            return Objects.requireNonNull(result, "JSON-B deserialization context result");
        } catch (IOException e) {
            throw new JsonbException("Cannot deserialize JSON-B context value", e);
        }
    }
}
