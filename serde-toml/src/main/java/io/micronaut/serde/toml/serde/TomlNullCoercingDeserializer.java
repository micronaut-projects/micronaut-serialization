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

/**
 * Deserializer wrapper that coerces TOML empty-string null sentinels back to {@code null}.
 *
 * <p>Since <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0</a> has no null type,
 * the encoder writes null fields as empty strings ({@code ''}). On the read path, this
 * wrapper intercepts empty strings and returns {@code null} for types where an empty string
 * is not a meaningful value.</p>
 *
 * <p>Coercion is <b>skipped</b> for types where empty string is valid input:
 * {@code String}, {@code CharSequence}.
 *
 * <p>Example round-trip:</p>
 * <pre>{@code
 * // Write path: bean.author == null
 * encoder → "author = ''"
 *
 * // Read path: this wrapper intercepts
 * "author = ''" → deserialize → detects empty string → returns null
 * }</pre>
 *
 * @param <T> The deserialized type
 */
@Internal
public final class TomlNullCoercingDeserializer<T> implements Deserializer<T> {
    private final Deserializer<? extends T> delegate;
    private final LimitingStream.RemainingLimits limits;

    private TomlNullCoercingDeserializer(Deserializer<? extends T> delegate,
                                         LimitingStream.RemainingLimits limits) {
        this.delegate = delegate;
        this.limits = limits;
    }

    public static <T> Deserializer<T> wrap(Deserializer<? extends T> delegate,
                                           Argument<? extends T> type,
                                           LimitingStream.RemainingLimits limits) {
        if (supportsEmptyStringNullCoercion(type)) {
            return new TomlNullCoercingDeserializer<>(delegate, limits);
        }
        @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast;
    }

    @Override
    public Deserializer<T> createSpecific(Deserializer.DecoderContext context, Argument<? super T> specificType) throws SerdeException {
        @SuppressWarnings("unchecked") Deserializer<? extends T> specificDelegate = (Deserializer<? extends T>) delegate.createSpecific(context, specificType);
        @SuppressWarnings("unchecked") Argument<? extends T> narrowedType = (Argument<? extends T>) specificType;
        return wrap(specificDelegate, narrowedType, limits);
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    @Override
    public T deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        JsonNode node = decoder.decodeNode();
        Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast.deserialize(JsonNodeDecoder.create(node, limits), context, targetType);
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
        // '' is the null sentinel for every type, except String where it is the empty string.
        return !CharSequence.class.isAssignableFrom(rawType);
    }
}
