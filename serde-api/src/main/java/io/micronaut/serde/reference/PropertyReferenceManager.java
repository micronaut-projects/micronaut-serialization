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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * interface for managing property references.
 */
@Internal
public interface PropertyReferenceManager {

    /**
     * Pushes a parent onto the stack.
     * @param reference The reference
     * @param <B> The bean type
     * @param <P> The parent
     * @see #popManagedRef()
     */
    <B, P> void pushManagedRef(@NonNull PropertyReference<B, P> reference);

    /**
     * Remove the last parent (if present) from the stack.
     * @see #pushManagedRef(io.micronaut.serde.reference.PropertyReference)
     */
    void popManagedRef();
}

