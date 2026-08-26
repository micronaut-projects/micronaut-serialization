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

import java.util.Set;

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
    private final boolean subtypesRequireDefaultImpl;
    private final boolean acceptCaseInsensitiveEnums;
    private final boolean acceptSingleValueAsArray;
    private final boolean acceptCaseInsensitiveProperties;
    private final boolean readUnknownEnumValuesAsNull;
    private final boolean readUnknownEnumValuesUsingDefaultValue;
    private final boolean readDateTimestampsAsNanoseconds;
    private final boolean adjustDatesToContextTimeZone;
    private final boolean requireAllCreatorParameters;
    private final boolean disableGeneratedDeserializer;
    private final boolean acceptFloatAsInt;
    private final Set<DeserializationConfiguration.Feature> features;

    @ConfigurationInject
    @SuppressWarnings("checkstyle:ParameterNumber")
    DefaultDeserializationConfiguration(@Bindable(defaultValue = StringUtils.TRUE) boolean ignoreUnknown,
                                        @Bindable(defaultValue = "100") int arraySizeThreshold,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean strictNullable,
                                        @Bindable(defaultValue = StringUtils.TRUE) boolean failOnNullForPrimitives,
                                        @Bindable(defaultValue = StringUtils.TRUE) boolean subtypesRequireDefaultImpl,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean acceptCaseInsensitiveEnums,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean acceptSingleValueAsArray,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean acceptCaseInsensitiveProperties,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean readUnknownEnumValuesAsNull,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean readUnknownEnumValuesUsingDefaultValue,
                                        @Bindable(defaultValue = StringUtils.TRUE) boolean readDateTimestampsAsNanoseconds,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean adjustDatesToContextTimeZone,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean requireAllCreatorParameters,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean disableGeneratedDeserializer,
                                        @Bindable(defaultValue = StringUtils.TRUE) boolean acceptFloatAsInt) {
        this.ignoreUnknown = ignoreUnknown;
        this.arraySizeThreshold = arraySizeThreshold;
        this.strictNullable = strictNullable;
        this.failOnNullForPrimitives = failOnNullForPrimitives;
        this.subtypesRequireDefaultImpl = subtypesRequireDefaultImpl;
        this.acceptCaseInsensitiveEnums = acceptCaseInsensitiveEnums;
        this.acceptSingleValueAsArray = acceptSingleValueAsArray;
        this.acceptCaseInsensitiveProperties = acceptCaseInsensitiveProperties;
        this.readUnknownEnumValuesAsNull = readUnknownEnumValuesAsNull;
        this.readUnknownEnumValuesUsingDefaultValue = readUnknownEnumValuesUsingDefaultValue;
        this.readDateTimestampsAsNanoseconds = readDateTimestampsAsNanoseconds;
        this.adjustDatesToContextTimeZone = adjustDatesToContextTimeZone;
        this.requireAllCreatorParameters = requireAllCreatorParameters;
        this.disableGeneratedDeserializer = disableGeneratedDeserializer;
        this.acceptFloatAsInt = acceptFloatAsInt;
        this.features = DeserializationConfiguration.super.features();
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

    @Override
    public boolean isSubtypesRequireDefaultImpl() {
        return subtypesRequireDefaultImpl;
    }

    @Override
    public boolean acceptCaseInsensitiveEnums() {
        return acceptCaseInsensitiveEnums;
    }

    @Override
    public boolean isAcceptFloatAsInt() {
        return acceptFloatAsInt;
    }

    @Override
    public boolean acceptSingleValueAsArray() {
        return acceptSingleValueAsArray;
    }

    @Override
    public boolean acceptCaseInsensitiveProperties() {
        return acceptCaseInsensitiveProperties;
    }

    @Override
    public boolean readUnknownEnumValuesAsNull() {
        return readUnknownEnumValuesAsNull;
    }

    @Override
    public boolean readUnknownEnumValuesUsingDefaultValue() {
        return readUnknownEnumValuesUsingDefaultValue;
    }

    @Override
    public boolean readDateTimestampsAsNanoseconds() {
        return readDateTimestampsAsNanoseconds;
    }

    @Override
    public boolean adjustDatesToContextTimeZone() {
        return adjustDatesToContextTimeZone;
    }

    @Override
    public boolean isRequireAllCreatorParameters() {
        return requireAllCreatorParameters;
    }

    @Override
    public boolean disableGeneratedDeserializer() {
        return disableGeneratedDeserializer;
    }

    @Override
    public Set<DeserializationConfiguration.Feature> features() {
        return features;
    }
}
