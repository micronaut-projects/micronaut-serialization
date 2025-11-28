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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * The resolved deserializer subtype info.
 *
 * @param <T>         The bean type
 * @author Denis Stepanov
 */
@Internal
interface DeserializerSubtypeInfo<T> {

    /**
     * @return The parent
     */
    DeserBeanSubtypeInfo<T> parent();

    /**
     * Find the {@link Deserializer} for discriminator of not provided one.
     *
     * @param discriminatorValue The discriminator value
     * @return The {@link Deserializer}
     */
    @NonNull
    Deserializer<T> findDeserializer(@Nullable String discriminatorValue) throws SerdeException;

}
