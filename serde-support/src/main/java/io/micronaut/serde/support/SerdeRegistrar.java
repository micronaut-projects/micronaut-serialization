/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serde;

import java.util.Collections;

/**
 * The registrar of {@link Serde}.
 * @param <T> The serde type
 */
@Internal
public interface SerdeRegistrar<T> extends Serde<T>, SerializerRegistrar<T>, DeserializerRegistrar<T> {

    /**
     * @return The serde argument type
     */
    @NonNull
    Argument<T> getType();

    /**
     * @return The multiple serde argument types
     */
    @NonNull
    default Iterable<Argument<?>> getTypes() {
        return Collections.singleton(getType());
    }

}
