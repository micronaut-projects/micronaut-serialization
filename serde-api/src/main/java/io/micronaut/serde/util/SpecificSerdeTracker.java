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
 * Implemented by an encoder or decoder context that records whether the specific serdes created with it depend on it.
 *
 * <p>The registry reuses the specific serde of a generated serde for later documents only when nothing created with
 * the context of the document is bound to that context. A serde created for the context of each document, or one
 * that keeps the context to create other serdes later, marks the context.</p>
 *
 * @since 3.3.0
 */
@Internal
public interface SpecificSerdeTracker {

    /**
     * Marks that a serde created with this context is bound to it: the specific serdes being created with the
     * context must not be reused for another document.
     */
    void markContextBound();

    /**
     * Marks that a serde created with the given context is bound to it, if the context records it.
     *
     * @param context The encoder or decoder context
     */
    static void markContextBound(Object context) {
        if (context instanceof SpecificSerdeTracker tracker) {
            tracker.markContextBound();
        }
    }
}
