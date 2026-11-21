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
 * Tracks whether a serde created with the context of a document keeps that context, so that it
 * must not serve other documents. A mapper caches the specific serde of a type only when creating it
 * did not bind it to the document.
 *
 * <p>A serde is bound to the document when the graph of serdes created with the context holds a
 * user serde that takes the context as a constructor argument, or a serde that resolves other serdes
 * later with the context. A serde that keeps the serdes it created between documents, such as the runtime bean
 * serdes, which keep the bean of each type, marks every later document it serves as bound when creating those serdes
 * bound the first one, since the later documents do not look them up again.</p>
 *
 * <p>This only keeps a mapper from caching such serdes. The property serdes a runtime bean keeps are still the ones
 * created with the context of the first document; specializing them for each document is a separate change.</p>
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
     * @return How many times the serdes created with this context were bound to the document, so that a
     * serde can tell whether creating another serde bound it
     */
    int documentBindings();

    /**
     * Finds a value that a serde keeps for the current document only, so that a serde shared by several
     * documents does not retain what it created for one of them after that document.
     *
     * @param key The key, usually the serde itself
     * @return The value, or null if none is kept
     */
    default @Nullable Object getDocumentValue(Object key) {
        return null;
    }

    /**
     * Keeps a value for the current document only, released when the document is finished. Only a
     * context that serves one document and is closed at its end keeps values; a context shared by
     * several documents or threads returns false.
     *
     * @param key   The key, usually the serde itself
     * @param value The value
     * @return Whether the value is kept, false when the context does not keep values
     */
    default boolean putDocumentValue(Object key, Object value) {
        return false;
    }

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
        return !(context instanceof DocumentContextTracker tracker) || tracker.documentBindings() > 0;
    }

    /**
     * @param context The encoder or decoder context
     * @return How many times the serdes created with the context were bound to the document, -1 when
     * the context does not track it
     */
    static int documentBindings(Object context) {
        return context instanceof DocumentContextTracker tracker ? tracker.documentBindings() : -1;
    }

    /**
     * @param context The encoder or decoder context
     * @param key     The key
     * @return The value kept for the document of the context, null if none is kept
     */
    static @Nullable Object getDocumentValue(Object context, Object key) {
        return context instanceof DocumentContextTracker tracker ? tracker.getDocumentValue(key) : null;
    }

    /**
     * @param context The encoder or decoder context
     * @param key     The key
     * @param value   The value
     * @return Whether the value is kept for the document of the context
     */
    static boolean putDocumentValue(Object context, Object key, Object value) {
        return context instanceof DocumentContextTracker tracker && tracker.putDocumentValue(key, value);
    }
}
