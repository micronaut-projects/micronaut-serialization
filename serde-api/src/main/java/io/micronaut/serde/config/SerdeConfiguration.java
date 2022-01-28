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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 *
 * @author gkrocher
 */
@ConfigurationProperties(SerdeConfiguration.PREFIX)
public interface SerdeConfiguration {

    String PREFIX = "micronaut.serde";

    /**
     * The date format to use when serializing and deserializing dates.
     * 
     * @return the date format to use
     */
    Optional<String> getDateFormat();

    /**
     * Whether to write dates as time stamps. Defaults to {@code true}.
     *
     * <p>If set to {@code false} the default ISO formats will be used to format dates as strings.</p>
     *
     * @see java.time.format.DateTimeFormatter
     * @return Whether to write dates as timestamps
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean isWriteDatesAsTimestamps();

    /**
     * @return The default locale to use.
     */
    Optional<Locale> getLocale();

    /**
     * @return The default time zone to use.
     */
    Optional<TimeZone> getTimeZone();

    /**
     * The packages containing introspections that should be regarded
     * as serializable by default without the need to add the {@link io.micronaut.serde.annotation.Serdeable} annotation.
     *
     * @return the packages to include
     */
    @Nullable
    List<String> getIncludedIntrospectionPackages();

    /**
     * Whether the introspections in the io.micronaut package should be regarded as serializable by default without the need to add the {@link io.micronaut.serde.annotation.Serdeable} annotation.
     * @return Whether to include package io.micronaut introspections
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean getIncludeIoMicronautIntrospectionPackage();
}
