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
package io.micronaut.serde.xml.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Base XML serde implementation that delegates serialization to an {@link XmlGenerator}.
 *
 * @param <T> The serialized type
 */
public abstract class XmlSerde<T> implements Serde<T> {

    protected abstract void doSerialize(XmlGenerator generator, EncoderContext context, T value, Argument<?> type) throws IOException;

    @Override
    public @Nullable T deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super T> type) throws IOException {
        return null;
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends T> type, @NonNull T value) throws IOException {
        doSerialize((XmlGenerator) encoder, context, value, type);
    }
}
