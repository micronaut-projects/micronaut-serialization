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
import java.util.concurrent.ConcurrentHashMap;

/**
 * The specific serdes of a generated serde that the registry reuses for later documents, by argument.
 *
 * <p>A specific serde is kept only when its creation did not mark the context of the document as bound
 * ({@link io.micronaut.serde.util.SpecificSerdeTracker}): nothing created with it keeps that context or was created
 * for that context only.</p>
 *
 * <p>Two arguments are the same argument when they have the same type, name, type parameters and annotation metadata
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

    private final Map<Key, S> serdes = new ConcurrentHashMap<>();

    /**
     * @param type The argument
     * @return The specific serde kept for the argument, if any
     */
    @Nullable
    S get(Argument<?> type) {
        return serdes.get(new Key(type));
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
        }
    }

    /**
     * @return The number of arguments whose specific serde is kept
     */
    int size() {
        return serdes.size();
    }

    private static boolean sameArgument(Argument<?> a, Argument<?> b) {
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

    private record Key(Argument<?> type) {

        @Override
        public boolean equals(Object o) {
            return o instanceof Key key && sameArgument(type, key.type);
        }

        @Override
        public int hashCode() {
            return type.typeHashCode();
        }
    }
}
