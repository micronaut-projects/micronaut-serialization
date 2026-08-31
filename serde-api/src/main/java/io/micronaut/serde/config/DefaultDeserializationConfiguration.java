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
import org.jspecify.annotations.Nullable;

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
    private final CoercionMode coercionMode;
    private final boolean acceptFloatAsInt;
    private final boolean acceptStringAsNumber;
    private final boolean acceptBooleanAsNumber;
    private final boolean acceptNumberAsBoolean;
    private final boolean acceptStringAsBoolean;
    private final boolean acceptScalarAsString;
    private final boolean unwrapSingleValueArrays;
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
                                        @Bindable(defaultValue = "LENIENT") CoercionMode coercionMode,
                                        @Nullable Boolean acceptFloatAsInt,
                                        @Nullable Boolean acceptStringAsNumber,
                                        @Nullable Boolean acceptBooleanAsNumber,
                                        @Nullable Boolean acceptNumberAsBoolean,
                                        @Nullable Boolean acceptStringAsBoolean,
                                        @Nullable Boolean acceptScalarAsString,
                                        @Nullable Boolean unwrapSingleValueArrays) {
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
        this.coercionMode = coercionMode == null ? CoercionMode.LENIENT : coercionMode;
        boolean lenient = this.coercionMode != CoercionMode.STRICT;
        this.acceptFloatAsInt = acceptFloatAsInt == null ? lenient : acceptFloatAsInt;
        this.acceptStringAsNumber = acceptStringAsNumber == null ? lenient : acceptStringAsNumber;
        this.acceptBooleanAsNumber = acceptBooleanAsNumber == null ? lenient : acceptBooleanAsNumber;
        this.acceptNumberAsBoolean = acceptNumberAsBoolean == null ? lenient : acceptNumberAsBoolean;
        this.acceptStringAsBoolean = acceptStringAsBoolean == null ? lenient : acceptStringAsBoolean;
        this.acceptScalarAsString = acceptScalarAsString == null ? lenient : acceptScalarAsString;
        this.unwrapSingleValueArrays = unwrapSingleValueArrays == null ? lenient : unwrapSingleValueArrays;
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
    public CoercionMode getCoercionMode() {
        return coercionMode;
    }

    @Override
    public boolean isAcceptFloatAsInt() {
        return acceptFloatAsInt;
    }

    @Override
    public boolean isAcceptStringAsNumber() {
        return acceptStringAsNumber;
    }

    @Override
    public boolean isAcceptBooleanAsNumber() {
        return acceptBooleanAsNumber;
    }

    @Override
    public boolean isAcceptNumberAsBoolean() {
        return acceptNumberAsBoolean;
    }

    @Override
    public boolean isAcceptStringAsBoolean() {
        return acceptStringAsBoolean;
    }

    @Override
    public boolean isAcceptScalarAsString() {
        return acceptScalarAsString;
    }

    @Override
    public boolean isUnwrapSingleValueArrays() {
        return unwrapSingleValueArrays;
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
