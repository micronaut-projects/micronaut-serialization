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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The specific serdes of a generated serde that the registry reuses for later documents, by argument.
 *
 * <p>A generated serde is created with the context of a document and the type, and resolves the specific serdes of
 * its properties with that context. The specific serde is reused only when nothing created with it depends on the
 * context: the creation is tracked on the creating thread, and every serde resolved during the creation that the
 * registry does not know to be independent of the context, such as a serde written by the user, marks the creation
 * as bound to the context. So does a serde that keeps the context to resolve other serdes later
 * ({@link io.micronaut.serde.util.SpecificSerdeTracker}).</p>
 *
 * <p>An argument is the same argument when it has the same type, name, type parameters and annotation metadata
 * instance, since the specific serde can depend on all of them.</p>
 *
 * @param <S> The serde type
 */
@Internal
final class SpecificSerdeCache<S> {

    /**
     * Limits the arguments of a serde whose specific serde is kept, such as arguments created with new annotation
     * metadata for every document: those are created for every document as before.
     */
    static final int MAX_ARGUMENTS = 128;

    /**
     * The number of creations being tracked on any thread, so that a lookup only reads the thread state when a
     * creation is tracked.
     */
    private static final AtomicInteger TRACKED_CREATIONS = new AtomicInteger();
    private static final ThreadLocal<Tracking> TRACKING = new ThreadLocal<>();

    private final Map<Key, S> serdes = new ConcurrentHashMap<>();
    @Nullable
    private volatile Entry<S> last;

    /**
     * Find the specific serde of the argument.
     *
     * @param type The argument
     * @return The specific serde, or null if it is not kept
     */
    @Nullable
    S get(Argument<?> type) {
        Entry<S> entry = last;
        if (entry != null && entry.type == type) {
            return entry.serde;
        }
        S serde = serdes.get(new Key(type));
        if (serde != null) {
            last = new Entry<>(type, serde);
        }
        return serde;
    }

    /**
     * Keep the specific serde of the argument.
     *
     * @param type  The argument
     * @param serde The specific serde
     */
    void put(Argument<?> type, S serde) {
        if (serdes.size() < MAX_ARGUMENTS) {
            serdes.putIfAbsent(new Key(type), serde);
            last = new Entry<>(type, serde);
        }
    }

    /**
     * @return The number of arguments whose specific serde is kept
     */
    int size() {
        return serdes.size();
    }

    /**
     * Starts tracking the creation of a specific serde on this thread.
     *
     * @return The state of the enclosing creation, to pass to {@link #endCreation(boolean)}
     */
    static boolean beginCreation() {
        TRACKED_CREATIONS.incrementAndGet();
        Tracking tracking = TRACKING.get();
        if (tracking == null) {
            tracking = new Tracking();
            TRACKING.set(tracking);
        }
        boolean enclosingBound = tracking.bound;
        tracking.bound = false;
        tracking.depth++;
        return enclosingBound;
    }

    /**
     * Ends tracking the creation of a specific serde on this thread. A creation bound to the context also binds the
     * creation that encloses it.
     *
     * @param enclosingBound The state returned by {@link #beginCreation()}
     * @return Whether the creation is bound to the context
     */
    static boolean endCreation(boolean enclosingBound) {
        Tracking tracking = Objects.requireNonNull(TRACKING.get());
        tracking.depth--;
        boolean bound = tracking.bound;
        tracking.bound = enclosingBound || bound;
        TRACKED_CREATIONS.decrementAndGet();
        return bound;
    }

    /**
     * Marks the creations tracked on this thread as bound to the context.
     */
    static void markContextBound() {
        if (TRACKED_CREATIONS.get() > 0) {
            // A thread that does not create a specific serde has no state
            Tracking tracking = TRACKING.get();
            if (tracking != null && tracking.depth > 0) {
                tracking.bound = true;
            }
        }
    }

    /**
     * @return Whether a creation is tracked on any thread
     */
    static boolean isTracking() {
        return TRACKED_CREATIONS.get() > 0;
    }

    static boolean sameArgument(Argument<?> a, Argument<?> b) {
        if (a == b) {
            return true;
        }
        if (a.getClass() != b.getClass() || a.getType() != b.getType() || !a.getName().equals(b.getName())) {
            return false;
        }
        AnnotationMetadata metadata = a.getAnnotationMetadata();
        AnnotationMetadata otherMetadata = b.getAnnotationMetadata();
        if (metadata != otherMetadata && !(metadata.isEmpty() && otherMetadata.isEmpty())) {
            return false;
        }
        Argument<?>[] parameters = a.getTypeParameters();
        Argument<?>[] otherParameters = b.getTypeParameters();
        if (parameters.length != otherParameters.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!sameArgument(parameters[i], otherParameters[i])) {
                return false;
            }
        }
        return true;
    }

    private static final class Tracking {
        private int depth;
        private boolean bound;
    }

    private record Entry<S>(Argument<?> type, S serde) {
    }

    private static final class Key {
        private final Argument<?> type;
        private final int hashCode;

        private Key(Argument<?> type) {
            this.type = type;
            this.hashCode = type.typeHashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key key && sameArgument(type, key.type);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
