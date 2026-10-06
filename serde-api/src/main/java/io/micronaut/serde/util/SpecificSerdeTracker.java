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
 * Implemented by an encoder or decoder context that tracks how the specific serdes created with it depend on it.
 *
 * <p>The registry reuses a specific serde for later documents only when it was created without depending on the
 * context it was created with. A serde that keeps the context, or that resolves other serdes with it after it was
 * created, marks the context so that the specific serde being created is not reused.</p>
 *
 * @since 3.3.0
 */
@Internal
public interface SpecificSerdeTracker {

    /**
     * Marks the specific serde that is being created with this context as bound to the context: it must not be
     * reused for another document.
     */
    void markContextBound();

    /**
     * Marks the specific serde that is being created with the given context as bound to the context, if the context
     * tracks it.
     *
     * @param context The encoder or decoder context
     */
    static void markContextBound(Object context) {
        if (context instanceof SpecificSerdeTracker tracker) {
            tracker.markContextBound();
        }
    }
}
