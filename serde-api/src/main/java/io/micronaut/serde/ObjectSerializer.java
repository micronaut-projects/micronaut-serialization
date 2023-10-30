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
package io.micronaut.serde;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;

import java.io.IOException;

/**
 * A variation of {@link Serializer} that is serializing an object and supports serializing its content into an existing object.
 *
 * @param <T> The object type to be serialized
 * @author Denis Stepanov
 * @since 2.3
 */
@Indexed(ObjectSerializer.class)
public interface ObjectSerializer<T> extends Serializer<T> {

    /**
     * Serializes the object values using the passed object encoder.
     *
     * @param encoder The object encoder to use
     * @param context The encoder context, never {@code null}
     * @param type    Models the generic type of the value
     * @param value   The value, can be {@code null}
     * @throws IOException If an error occurs during serialization
     */
    void serializeInto(@NonNull Encoder encoder,
                       @NonNull EncoderContext context,
                       @NonNull Argument<? extends T> type,
                       @NonNull T value) throws IOException;

}
