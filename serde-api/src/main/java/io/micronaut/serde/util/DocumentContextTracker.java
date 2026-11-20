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

/**
 * Tracks whether a serde created with the context of a document keeps that context, so that it
 * must not serve other documents. A mapper caches the specific serde of a type only when creating it
 * did not bind it to the document.
 *
 * <p>A serde is bound to the document when the graph of serdes created with the context holds a
 * user serde that takes the context as a constructor argument, or a serde that resolves other serdes
 * later with the context.</p>
 *
 * @since 3.3
 */
@Internal
public interface DocumentContextTracker {

    /**
     * Marks the serdes created with this context as bound to the document.
     */
    void markDocumentBound();

    /**
     * @return Whether the serdes created with this context are bound to the document
     */
    boolean isDocumentBound();

    /**
     * Marks the serdes created with the given context as bound to the document.
     *
     * @param context The encoder or decoder context
     */
    static void markDocumentBound(Object context) {
        if (context instanceof DocumentContextTracker tracker) {
            tracker.markDocumentBound();
        }
    }

    /**
     * @param context The encoder or decoder context
     * @return Whether the serdes created with the context are bound to the document, true when the
     * context does not track it
     */
    static boolean isDocumentBound(Object context) {
        return !(context instanceof DocumentContextTracker tracker) || tracker.isDocumentBound();
    }
}
