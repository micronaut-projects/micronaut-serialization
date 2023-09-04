/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Default implementation of {@link SerializationConfiguration}.
 *
 * @author Denis Stepanov
 */
@ConfigurationProperties(SerializationConfiguration.PREFIX)
@BootstrapContextCompatible
final class DefaultSerializationConfiguration implements SerializationConfiguration {

    private final SerdeConfig.SerInclude inclusion;
    private final boolean alwaysSerializeErrorsAsList;

    @ConfigurationInject
    DefaultSerializationConfiguration(@Bindable(defaultValue = "NON_EMPTY") SerdeConfig.SerInclude inclusion,
                                      @Bindable(defaultValue = StringUtils.TRUE) boolean alwaysSerializeErrorsAsList) {
        this.inclusion = inclusion;
        this.alwaysSerializeErrorsAsList = alwaysSerializeErrorsAsList;
    }

    @Override
    public SerdeConfig.SerInclude getInclusion() {
        return inclusion;
    }

    @Override
    public boolean isAlwaysSerializeErrorsAsList() {
        return alwaysSerializeErrorsAsList;
    }

}
