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
package io.micronaut.serde.cbor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Helpers for CBOR-specific {@link SerdeConfiguration} adjustments.
 */
@Internal
final class CborSerdeConfigurationSupport {

    private CborSerdeConfigurationSupport() {
    }

    /**
     * Create a registry clone that applies CBOR binary-array preferences while keeping other
     * serde settings.
     *
     * @param registry           The source registry
     * @param serdeConfiguration The base configuration
     * @param cborConfiguration  The CBOR configuration
     * @return A registry using the effective CBOR configuration
     */
    static SerdeRegistry registryForCbor(SerdeRegistry registry,
                                         SerdeConfiguration serdeConfiguration,
                                         SerdeCborConfiguration cborConfiguration) {
        SerdeConfiguration effective = withWriteBinaryAsArray(serdeConfiguration, cborConfiguration.isWriteBinaryAsArray());
        // cloning a registry re-scans the bean context, so skip it when nothing changed
        return effective == serdeConfiguration ? registry : registry.cloneWithConfiguration(effective, null, null);
    }

    /**
     * Overlay {@code writeBinaryAsArray} on an existing configuration.
     *
     * @param delegate            The base configuration
     * @param writeBinaryAsArray  Whether to use legacy numeric arrays for {@code byte[]}
     * @return Effective configuration for CBOR
     */
    static SerdeConfiguration withWriteBinaryAsArray(SerdeConfiguration delegate, boolean writeBinaryAsArray) {
        if (delegate.isWriteBinaryAsArray() == writeBinaryAsArray) {
            return delegate;
        }
        return new Overlay(delegate, writeBinaryAsArray);
    }

    private static final class Overlay implements SerdeConfiguration {
        private final SerdeConfiguration delegate;
        private final boolean writeBinaryAsArray;

        private Overlay(SerdeConfiguration delegate, boolean writeBinaryAsArray) {
            this.delegate = delegate;
            this.writeBinaryAsArray = writeBinaryAsArray;
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
            return writeBinaryAsArray;
        }

        @Override
        public boolean isWriteDurationsAsStrings() {
            return delegate.isWriteDurationsAsStrings();
        }

        @Override
        public boolean isWriteJavaUtilDatesWithZoneId() {
            return delegate.isWriteJavaUtilDatesWithZoneId();
        }

        @Override
        public boolean isRejectDeprecatedThreeLetterTimeZoneIds() {
            return delegate.isRejectDeprecatedThreeLetterTimeZoneIds();
        }

        @Override
        public boolean isWriteDateTimesAsStrictIJson() {
            return delegate.isWriteDateTimesAsStrictIJson();
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
        public @Nullable PropertyNamingStrategy getPropertyNamingStrategy() {
            return delegate.getPropertyNamingStrategy();
        }

        @Override
        public @Nullable String getPropertyNamingStrategyName() {
            return delegate.getPropertyNamingStrategyName();
        }

        @Override
        public boolean isJsonViewEnabled() {
            return delegate.isJsonViewEnabled();
        }
    }
}
