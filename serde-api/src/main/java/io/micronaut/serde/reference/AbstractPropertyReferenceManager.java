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
import java.util.Objects;

import org.jspecify.annotations.Nullable;

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
    @Nullable
    private ArrayDeque<ReferenceScopeImpl> referenceScopes;
    @Nullable
    private ReferenceScopeImpl currentScope;

    @Override
    public ReferenceScope openReferenceScope() {
        ReferenceScopeImpl scope = new ReferenceScopeImpl(this, currentScope);
        currentScope = scope;
        return scope;
    }

    @Override
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        if (reference != null) {
            if (refs == null) {
                refs = new ArrayDeque<>(5);
                referenceScopes = new ArrayDeque<>(5);
            }
            refs.addFirst(reference);
            ReferenceScopeImpl scope = currentScope;
            Objects.requireNonNull(referenceScopes).addFirst(scope == null ? ReferenceScopeImpl.NO_SCOPE : scope);
            if (scope != null) {
                scope.referenceCount++;
            }
        }
    }

    @Override
    public void popManagedRef() {
        if (refs != null && !refs.isEmpty()) {
            refs.removeFirst();
            ReferenceScopeImpl scope = Objects.requireNonNull(referenceScopes).removeFirst();
            if (scope != ReferenceScopeImpl.NO_SCOPE) {
                scope.referenceCount--;
            }
        }
    }

    private static final class ReferenceScopeImpl implements ReferenceScope {
        private static final ReferenceScopeImpl NO_SCOPE = new ReferenceScopeImpl(null, null);

        private final @Nullable AbstractPropertyReferenceManager manager;
        private final @Nullable ReferenceScopeImpl parent;
        private int referenceCount;

        private ReferenceScopeImpl(@Nullable AbstractPropertyReferenceManager manager,
                                   @Nullable ReferenceScopeImpl parent) {
            this.manager = manager;
            this.parent = parent;
        }

        @Override
        public void close() {
            AbstractPropertyReferenceManager referenceManager = manager;
            if (referenceManager == null) {
                return;
            }
            if (referenceManager.currentScope != this) {
                throw new IllegalStateException("Reference scopes must be closed in reverse order");
            }
            while (referenceCount > 0) {
                referenceManager.popManagedRef();
            }
            referenceManager.currentScope = parent;
        }
    }
}
