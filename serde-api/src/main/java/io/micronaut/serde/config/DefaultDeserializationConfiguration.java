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

/**
 * Default implementation of {@link DeserializationConfiguration}.
 *
 * @author Denis Stepanov
 */
@ConfigurationProperties(DeserializationConfiguration.PREFIX)
@BootstrapContextCompatible
final class DefaultDeserializationConfiguration implements DeserializationConfiguration {
    private final boolean ignoreUnknown;
    private final int arraySizeThreshold;
    private final boolean strictNullable;
    private final boolean failOnNullForPrimitives;

    @ConfigurationInject
    DefaultDeserializationConfiguration(@Bindable(defaultValue = StringUtils.TRUE) boolean ignoreUnknown,
                                        @Bindable(defaultValue = "100") int arraySizeThreshold,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean strictNullable,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean failOnNullForPrimitives) {
        this.ignoreUnknown = ignoreUnknown;
        this.arraySizeThreshold = arraySizeThreshold;
        this.strictNullable = strictNullable;
        this.failOnNullForPrimitives = failOnNullForPrimitives;
    }

    @Override
    public boolean isIgnoreUnknown() {
        return ignoreUnknown;
    }

    @Override
    public int getArraySizeThreshold() {
        return arraySizeThreshold;
    }

    @Override
    public boolean isStrictNullable() {
        return strictNullable;
    }

    @Override
    public boolean isFailOnNullForPrimitives() {
        return failOnNullForPrimitives;
    }
}
