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
package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.ObjectMapper;

/**
 * A variation of {@link ObjectMapper} that allows to clone {@link JacksonObjectMapper} with a new {@link SerdeJacksonConfiguration}.
 *
 * @author Denis Stepanov
 * @since 2.9
 */
public interface JacksonObjectMapper extends ObjectMapper {

    /**
     * Create a new {@link JacksonObjectMapper} with the given configuration.
     *
     * @param jacksonConfiguration The {@link SerdeJacksonConfiguration}
     * @return A new {@link JacksonObjectMapper} with the updated config
     * @since 2.9
     */
    @NonNull
    JacksonObjectMapper cloneWithConfiguration(@NonNull SerdeJacksonConfiguration jacksonConfiguration);

}
