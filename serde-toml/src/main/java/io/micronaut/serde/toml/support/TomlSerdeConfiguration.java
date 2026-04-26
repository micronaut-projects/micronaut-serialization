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
package io.micronaut.serde.toml.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * TOML-local serde configuration overrides.
 */
@Internal
public final class TomlSerdeConfiguration implements SerdeConfiguration {
    private final SerdeConfiguration delegate;

    public TomlSerdeConfiguration(SerdeConfiguration delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<String> getDateFormat() {
        return delegate.getDateFormat();
    }

    @Override
    public boolean isInetAddressAsNumeric() {
        return delegate.isInetAddressAsNumeric();
    }

    @Override
    public TimeShape getTimeWriteShape() {
        return delegate.getTimeWriteShape();
    }

    @Override
    public NumericTimeUnit getNumericTimeUnit() {
        return delegate.getNumericTimeUnit();
    }

    @Override
    public boolean isWriteBinaryAsArray() {
        return false;
    }

    @Override
    public Optional<Locale> getLocale() {
        return delegate.getLocale();
    }

    @Override
    public Optional<TimeZone> getTimeZone() {
        return delegate.getTimeZone();
    }

    @Override
    public List<String> getIncludedIntrospectionPackages() {
        return delegate.getIncludedIntrospectionPackages();
    }

    @Override
    public int getMaximumNestingDepth() {
        return delegate.getMaximumNestingDepth();
    }

    @Override
    public @Nullable String getPropertyNamingStrategyName() {
        return delegate.getPropertyNamingStrategyName();
    }

    @Override
    public @Nullable PropertyNamingStrategy getPropertyNamingStrategy() {
        return delegate.getPropertyNamingStrategy();
    }

    @Override
    public boolean isJsonViewEnabled() {
        return delegate.isJsonViewEnabled();
    }
}
