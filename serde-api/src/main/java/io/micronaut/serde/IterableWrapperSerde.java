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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Internal contract implemented by {@link Serde} instances that handle iterable
 * (collection / array) values which may be rendered inside a dedicated wrapper
 * element &mdash; for example XML output such as
 * {@code <books><book/><book/></books>} versus the unwrapped
 * {@code <book><book/>} form.
 *
 *
 * @param <T> The iterable type handled by the serde
 * @see Serde
 * @since 3.0.0
 */
@Internal
public interface IterableWrapperSerde<T> {

    /**
     * Returns a serde variant configured with the resolved iterable wrapper
     * settings for the property currently being processed.
     *
     * @param useWrapping {@code true} if the iterable should be enclosed in a
     *                    wrapper element, {@code false} for the inlined form
     * @param wrapperName The wrapper / item element name to use, or
     *                    {@code null} when no explicit name was configured
     * @return The configured serde; never {@code null}
     */
    @NonNull Serde<T> withIterableWrapper(boolean useWrapping, @Nullable String wrapperName);
}
