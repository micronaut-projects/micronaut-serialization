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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Resolves a Jackson object identity reference from the current document.
 *
 * @since 3.2
 */
@Internal
@Singleton
public final class JsonIdentityReferenceDeserializer implements Deserializer<Object> {
    @Override
    @SuppressWarnings("unchecked")
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (node.isObject()) {
            Deserializer<Object> objectDeserializer = (Deserializer<Object>) context.findDeserializer(Argument.of(type.getType()));
            return objectDeserializer.deserialize(JsonNodeDecoder.create(node, LimitingStream.DEFAULT_LIMITS), context, type);
        }
        Object id = JsonNodeDecoder.create(node, LimitingStream.DEFAULT_LIMITS).decodeArbitrary();
        if (id == null) {
            throw new SerdeException("Cannot resolve a null JSON object identity reference for type " + type.getType());
        }
        Object value = context.resolveObjectId(id, type);
        if (value == null) {
            throw new SerdeException("Unable to resolve JSON object identity [" + id + "]");
        }
        return value;
    }

    /**
     * Deserializes an identity property and defers a scalar reference when its object has not been read yet.
     *
     * @param decoder The JSON decoder
     * @param context The decoder context
     * @param type The referenced type
     * @param consumer Receives the referenced object
     * @throws IOException If the input cannot be decoded
     */
    @SuppressWarnings("unchecked")
    public static void deserializeAndResolve(Decoder decoder,
                                             DecoderContext context,
                                             Argument<?> type,
                                             DecoderContext.ObjectIdConsumer consumer) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (node.isObject()) {
            Deserializer<Object> objectDeserializer = (Deserializer<Object>) context.findDeserializer(Argument.of(type.getType()));
            consumer.accept(objectDeserializer.deserialize(JsonNodeDecoder.create(node, LimitingStream.DEFAULT_LIMITS), context, (Argument<Object>) type));
            return;
        }
        Object id = JsonNodeDecoder.create(node, LimitingStream.DEFAULT_LIMITS).decodeArbitrary();
        if (id == null) {
            throw new SerdeException("Cannot resolve a null JSON object identity reference for type " + type.getType());
        }
        context.resolveOrDeferObjectId(id, type, consumer);
    }
}
