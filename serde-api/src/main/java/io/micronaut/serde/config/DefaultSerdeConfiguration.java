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
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * The default implementation of SerdeConfiguration.
 *
 * @author Denis Stepanov
 */
@ConfigurationProperties(SerdeConfiguration.PREFIX)
@BootstrapContextCompatible
final class DefaultSerdeConfiguration implements SerdeConfiguration {

    private final Optional<String> dateFormat;
    private final TimeShape timeWriteShape;
    private final NumericTimeUnit numericTimeUnit;
    private final boolean writeBinaryAsArray;
    private final Optional<Locale> locale;
    private final Optional<TimeZone> timeZone;
    private final List<String> includedIntrospectionPackages;
    private final int maximumNestingDepth;
    private final boolean inetAddressAsNumeric;
    private final String propertyNamingStrategyName;
    private final PropertyNamingStrategy propertyNamingStrategy;
    private final boolean jsonViewEnabled;

    @ConfigurationInject
    DefaultSerdeConfiguration(Optional<String> dateFormat,
                              @Bindable(defaultValue = "STRING") TimeShape timeWriteShape,
                              @Bindable(defaultValue = "SECONDS") NumericTimeUnit numericTimeUnit,
                              @Bindable(defaultValue = "true") boolean writeBinaryAsArray,
                              Optional<Locale> locale,
                              Optional<TimeZone> timeZone,
                              @Bindable(defaultValue = "io.micronaut") List<String> includedIntrospectionPackages,
                              @Bindable(defaultValue = LimitingStream.DEFAULT_MAXIMUM_DEPTH + "") int maximumNestingDepth,
                              @Bindable(defaultValue = DEFAULT_INET_ADDRESS_AS_NUMERIC + "") boolean inetAddressAsNumeric,
                              @Nullable String propertyNamingStrategy,
                              @Property(name = "jackson.json-view.enabled", defaultValue = "false") boolean jsonViewEnabled) {
        this.dateFormat = dateFormat;
        this.timeWriteShape = timeWriteShape;
        this.numericTimeUnit = numericTimeUnit;
        this.writeBinaryAsArray = writeBinaryAsArray;
        this.locale = locale;
        this.timeZone = timeZone;
        this.includedIntrospectionPackages = includedIntrospectionPackages;
        this.maximumNestingDepth = maximumNestingDepth;
        this.inetAddressAsNumeric = inetAddressAsNumeric;
        this.propertyNamingStrategyName = propertyNamingStrategy;
        this.propertyNamingStrategy = PropertyNamingStrategy.forName(propertyNamingStrategy).orElse(null);
        this.jsonViewEnabled = jsonViewEnabled;
    }

    @Override
    public PropertyNamingStrategy getPropertyNamingStrategy() {
        return propertyNamingStrategy;
    }

    @Override
    public String getPropertyNamingStrategyName() {
        return propertyNamingStrategyName;
    }

    @Override
    public Optional<String> getDateFormat() {
        return dateFormat;
    }

    @Override
    public TimeShape getTimeWriteShape() {
        return timeWriteShape;
    }

    @Override
    public NumericTimeUnit getNumericTimeUnit() {
        return numericTimeUnit;
    }

    @Override
    public boolean isWriteBinaryAsArray() {
        return writeBinaryAsArray;
    }

    @Override
    public Optional<Locale> getLocale() {
        return locale;
    }

    @Override
    public Optional<TimeZone> getTimeZone() {
        return timeZone;
    }

    @Override
    public List<String> getIncludedIntrospectionPackages() {
        return includedIntrospectionPackages;
    }

    @Override
    public int getMaximumNestingDepth() {
        return maximumNestingDepth;
    }

    @Override
    public boolean isInetAddressAsNumeric() {
        return inetAddressAsNumeric;
    }

    @Override
    public boolean isJsonViewEnabled() {
        return jsonViewEnabled;
    }
}
