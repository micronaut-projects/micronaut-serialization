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
package io.micronaut.serde.properties;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;

import java.util.Objects;

/**
 * Java {@code .properties}-specific configuration.
 *
 * @since 3.1.0
 */
@BootstrapContextCompatible
@Internal
@ConfigurationProperties(SerdePropertiesConfiguration.PREFIX)
public final class SerdePropertiesConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".format.properties";

    private ArrayIndexStyle arrayIndexStyle = ArrayIndexStyle.BRACKETED;

    /**
     * Returns the configured array index style.
     *
     * @return The configured array index style
     */
    public ArrayIndexStyle getArrayIndexStyle() {
        return arrayIndexStyle;
    }

    /**
     * Sets the array index style.
     *
     * @param arrayIndexStyle The array index style
     */
    public void setArrayIndexStyle(ArrayIndexStyle arrayIndexStyle) {
        this.arrayIndexStyle = Objects.requireNonNull(arrayIndexStyle, "arrayIndexStyle");
    }

    /**
     * Array index path style.
     *
     * @since 3.1.0
     */
    public enum ArrayIndexStyle {
        /**
         * Zero-based bracketed array indexes; {@code values[0]}.
         */
        BRACKETED,

        /**
         * One-based dotted array indexes; {@code values.1}.
         */
        DOTTED
    }
}
