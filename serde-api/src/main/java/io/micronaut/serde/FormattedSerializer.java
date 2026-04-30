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
package io.micronaut.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.NonNull;

/**
 * A serializer that can react to format metadata.
 *
 * @param <T> The type to be serialized
 * @author Denis Stepanov
 * @since 3.0
 */
public interface FormattedSerializer<T> extends Serializer<T> {

    /**
     * Create a more specific serializer for the given definition and format configuration.
     *
     * @param context The encoder context
     * @param type    The type definition including any annotation metadata
     * @param format  The format configuration
     * @return The more specific serializer
     * @throws SerdeException If the serializer cannot be selected
     */
    default @NonNull Serializer<T> createSpecific(@NonNull EncoderContext context,
                                                  @NonNull Argument<? extends T> type,
                                                  @NonNull FormatConfiguration format) throws SerdeException {
        return createSpecific(context, type);
    }
}
