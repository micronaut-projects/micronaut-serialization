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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Serdes kept for the duration of one document by the encoder or decoder context of the document. A serde that is
 * kept between documents stores the serdes it specialized for a document here, under itself, instead of holding
 * them: such a serde can keep its context, so holding it would keep the document.
 *
 * @since 3.3.0
 */
@Internal
public interface DocumentSerdes {

    /**
     * @param owner The serde that stored the value
     * @return The value stored by the owner for this document, or {@code null}
     */
    @Nullable Object getDocumentSerde(Object owner);

    /**
     * Stores a value for the rest of the document.
     *
     * @param owner The serde that stores the value
     * @param value The value
     */
    void putDocumentSerde(Object owner, Object value);

    /**
     * @param context The encoder or decoder context
     * @param owner   The serde that stored the value
     * @return The value stored by the owner for the document of the context, or {@code null} when there is none or
     * the context keeps no document serdes
     */
    static @Nullable Object get(Object context, Object owner) {
        return context instanceof DocumentSerdes documentSerdes ? documentSerdes.getDocumentSerde(owner) : null;
    }

    /**
     * Stores a value for the rest of the document of the context, when the context keeps document serdes.
     *
     * @param context The encoder or decoder context
     * @param owner   The serde that stores the value
     * @param value   The value
     */
    static void put(Object context, Object owner, Object value) {
        if (context instanceof DocumentSerdes documentSerdes) {
            documentSerdes.putDocumentSerde(owner, value);
        }
    }
}
