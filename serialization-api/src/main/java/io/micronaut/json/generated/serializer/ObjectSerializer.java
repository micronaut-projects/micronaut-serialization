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
package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Fallback {@link Serializer} for general {@link Object} values. For deserialization, deserializes to standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link List}. For serialization, serializes
 * using {@link Encoder#encodeArbitrary}.
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link io.micronaut.json.JsonMapper#writeValue}{@code (gen, }{@link Object}{@code .class)}</li>
 * </ul>
 */
@Internal
@Secondary
public final class ObjectSerializer implements Serializer<Object>, Deserializer<Object> {
    @Override
    public Object deserialize(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            throw decoder.createDeserializationException("Unexpected null value");
        }
        return decoder.decodeArbitrary();
    }

    @Override
    public void serialize(Encoder encoder, Object value) throws IOException {
        encoder.encodeArbitrary(value);
    }
}
