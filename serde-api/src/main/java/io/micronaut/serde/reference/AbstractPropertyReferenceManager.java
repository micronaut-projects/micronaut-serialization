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
package io.micronaut.serde.reference;

import java.util.ArrayDeque;

import io.micronaut.core.annotation.Nullable;

/**
 * Abstract implementation of {@link io.micronaut.serde.reference.PropertyReferenceManager}.
 *
 * @since 1.0.0
 */
public abstract class AbstractPropertyReferenceManager implements PropertyReferenceManager {
    /**
     * used to store current references.
     */
    @Nullable
    protected ArrayDeque<PropertyReference<?, ?>> refs;

    @Override
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        if (reference != null) {
            if (refs == null) {
                refs = new ArrayDeque<>(5);
            }
            refs.addFirst(reference);
        }
    }

    @Override
    public void popManagedRef() {
        if (refs != null && !refs.isEmpty()) {
            refs.removeFirst();
        }
    }
}
