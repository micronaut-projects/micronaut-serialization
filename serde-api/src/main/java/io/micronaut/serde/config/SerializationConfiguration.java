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
package io.micronaut.serde.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Configuration for serialization.
 */
@ConfigurationProperties(SerializationConfiguration.PREFIX)
public interface SerializationConfiguration {
    String PREFIX = SerdeConfiguration.PREFIX + ".serialization";

    /**
     * @return The default inclusion to use. Defaults to {@link SerdeConfig.SerInclude#NON_EMPTY}.
     */
    @Bindable(defaultValue = "NON_EMPTY")
    @NonNull
    SerdeConfig.SerInclude getInclusion();

    /**
     * @return Whether to serialize errors as a list.
     * @see io.micronaut.http.hateoas.JsonError
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean isAlwaysSerializeErrorsAsList();
}
