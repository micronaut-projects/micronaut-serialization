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

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.NullMarked;

/**
 * Configuration for serialization.
 */
@NullMarked
public interface SerializationConfiguration {
    String PREFIX = SerdeConfiguration.PREFIX + ".serialization";

    /**
     * @return The default inclusion to use. Defaults to {@link SerdeConfig.SerInclude#NON_EMPTY}.
     */
    @Bindable(defaultValue = "NON_EMPTY")
    SerdeConfig.SerInclude getInclusion();

    /**
     * @return Whether to serialize errors as a list.
     * @see io.micronaut.http.hateoas.JsonError
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean isAlwaysSerializeErrorsAsList();

    /**
     * Determines whether properties should be serialized in alphabetical order.
     *
     * @return true if properties should be sorted alphabetically, false otherwise
     * @since 2.14
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean sortPropertiesAlphabetically() {
        return false;
    }

    /**
     * Determines whether timestamps are written with nanosecond precision.
     *
     * @return true if timestamp values should preserve nanoseconds
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean writeDateTimestampsAsNanoseconds() {
        return true;
    }

    /**
     * Determines whether zone ids are included when serializing zoned date/time values.
     *
     * @return true if zone ids should be written
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean writeDatesWithZoneId() {
        return true;
    }

    /**
     * Determines whether single-element arrays should be written as unwrapped scalar values.
     *
     * @return true if single-element arrays should be unwrapped
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean writeSingleElemArraysUnwrapped() {
        return false;
    }

    /**
     * Determines whether map entries should be written sorted by key.
     *
     * @return true if map entries should be sorted
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean writeSortedMapEntries() {
        return false;
    }
}
