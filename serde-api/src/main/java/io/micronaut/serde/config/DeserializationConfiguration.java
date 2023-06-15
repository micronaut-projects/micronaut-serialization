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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;

/**
 * Configuration for deserialization.
 */
@ConfigurationProperties(DeserializationConfiguration.PREFIX)
@BootstrapContextCompatible
public interface DeserializationConfiguration {
    String PREFIX = SerdeConfiguration.PREFIX + ".deserialization";

    /**
     * Whether to ignore unknown values during deserialization.
     * @return True if unknown values should simply be ignored.
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean isIgnoreUnknown();

    /**
     * @return The array size thresh hold for use in binding. Defaults to {@code 100}.
     */
    @Bindable(defaultValue = "100")
    int getArraySizeThreshold();

    /**
     * Whether null field should be annotated with a nullable annotations. Defaults to {@code false}
     * @return True if null field should be annotated with a nullable annotations
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    boolean isStrictNullable();
}
