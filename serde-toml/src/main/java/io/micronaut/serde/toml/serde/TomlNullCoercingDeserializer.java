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
package io.micronaut.serde.toml.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * TOML deserializer wrapper for null sentinel coercion.
 *
 * @param <T> The deserialized type
 */
@Internal
public final class TomlNullCoercingDeserializer<T> implements Deserializer<T> {
    private final Deserializer<? extends T> delegate;
    private final Argument<? extends T> type;
    private final LimitingStream.RemainingLimits limits;

    private TomlNullCoercingDeserializer(Deserializer<? extends T> delegate,
                                         Argument<? extends T> type,
                                         LimitingStream.RemainingLimits limits) {
        this.delegate = delegate;
        this.type = type;
        this.limits = limits;
    }

    public static <T> Deserializer<T> wrap(Deserializer<? extends T> delegate,
                                           Argument<? extends T> type,
                                           LimitingStream.RemainingLimits limits) {
        if (!supportsEmptyStringNullCoercion(type)) {
            @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
            return cast;
        }
        return new TomlNullCoercingDeserializer<>(delegate, type, limits);
    }

    @Override
    public Deserializer<T> createSpecific(Deserializer.DecoderContext context, Argument<? super T> specificType) throws SerdeException {
        @SuppressWarnings("unchecked") Deserializer<? extends T> specificDelegate = (Deserializer<? extends T>) delegate.createSpecific(context, specificType);
        @SuppressWarnings("unchecked") Argument<? extends T> narrowedType = (Argument<? extends T>) specificType;
        return wrap(specificDelegate, narrowedType, limits);
    }

    @Override
    public @Nullable T deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        return deserializeNullable(decoder, context, targetType);
    }

    @Override
    public @Nullable T deserializeNullable(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (node.isString() && node.coerceStringValue().isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast.deserializeNullable(JsonNodeDecoder.create(node, limits), context, targetType);
    }

    @Override
    public @Nullable T getDefaultValue(Deserializer.DecoderContext context, Argument<? super T> targetType) {
        @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast.getDefaultValue(context, targetType);
    }

    private static boolean supportsEmptyStringNullCoercion(Argument<?> type) {
        Class<?> rawType = type.getType();
        return !rawType.isPrimitive()
            && rawType != Object.class
            && !rawType.isEnum()
            && !rawType.isArray()
            && rawType != byte[].class
            && !CharSequence.class.isAssignableFrom(rawType)
            && !Number.class.isAssignableFrom(rawType)
            && rawType != Boolean.class
            && rawType != Character.class
            && !Map.class.isAssignableFrom(rawType)
            && !Iterable.class.isAssignableFrom(rawType);
    }
}
