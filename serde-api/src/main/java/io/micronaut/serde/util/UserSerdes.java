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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The serde classes a registry resolved from beans of the application, as opposed to the serdes Micronaut
 * Serialization registers itself and the generated serdes. A user serde can keep the context it is specialized
 * with, or state of one document, and can change how a value is read, so the shortcuts that skip it are not taken.
 *
 * @since 3.3.0
 */
@Internal
public final class UserSerdes {

    private static final ClassValue<AtomicBoolean> USER = new ClassValue<>() {
        @Override
        protected AtomicBoolean computeValue(Class<?> type) {
            return new AtomicBoolean();
        }
    };

    private UserSerdes() {
    }

    /**
     * Records that the registry resolved a serde of this class from a bean of the application.
     *
     * @param serdeType The class of the serde
     */
    public static void mark(Class<?> serdeType) {
        USER.get(serdeType).set(true);
    }

    /**
     * @param serdeType The class of a serde
     * @return Whether a registry resolved a serde of this class from a bean of the application
     */
    public static boolean isUser(Class<?> serdeType) {
        return USER.get(serdeType).get();
    }
}
