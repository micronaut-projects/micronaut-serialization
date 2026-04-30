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

/**
 * A deserializer that can react to format metadata.
 *
 * @param <T> The type to be deserialized
 * @author Denis Stepanov
 * @since 3.0
 */
public interface FormattedDeserializer<T> extends Deserializer<T> {

    /**
     * Create a more specific deserializer for the given definition and format configuration.
     *
     * @param context The decoder context
     * @param type    The type definition including any annotation metadata
     * @param format  The format configuration
     * @return The more specific deserializer
     * @throws SerdeException If the deserializer cannot be selected
     */
    default Deserializer<T> createSpecific(DecoderContext context,
                                                    Argument<? super T> type,
                                                    FormatConfiguration format) throws SerdeException {
        return createSpecific(context, type);
    }
}
