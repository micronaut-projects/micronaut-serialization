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
import io.micronaut.core.annotation.Nullable;
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
    private final boolean subtypesRequireDefaultImpl;
    private final boolean acceptCaseInsensitiveEnums;
    private final CoercionMode coercionMode;
    private final boolean acceptFloatAsInt;
    private final boolean acceptStringAsNumber;
    private final boolean acceptBooleanAsNumber;
    private final boolean acceptNumberAsBoolean;
    private final boolean acceptStringAsBoolean;
    private final boolean acceptScalarAsString;
    private final boolean unwrapSingleValueArrays;

    @ConfigurationInject
    @SuppressWarnings("checkstyle:ParameterNumber")
    DefaultDeserializationConfiguration(@Bindable(defaultValue = StringUtils.TRUE) boolean ignoreUnknown,
                                        @Bindable(defaultValue = "100") int arraySizeThreshold,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean strictNullable,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean failOnNullForPrimitives,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean subtypesRequireDefaultImpl,
                                        @Bindable(defaultValue = StringUtils.FALSE) boolean acceptCaseInsensitiveEnums,
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
        this.coercionMode = coercionMode;
        boolean lenient = coercionMode != CoercionMode.STRICT;
        this.acceptFloatAsInt = acceptFloatAsInt == null ? lenient : acceptFloatAsInt;
        this.acceptStringAsNumber = acceptStringAsNumber == null ? lenient : acceptStringAsNumber;
        this.acceptBooleanAsNumber = acceptBooleanAsNumber == null ? lenient : acceptBooleanAsNumber;
        this.acceptNumberAsBoolean = acceptNumberAsBoolean == null ? lenient : acceptNumberAsBoolean;
        this.acceptStringAsBoolean = acceptStringAsBoolean == null ? lenient : acceptStringAsBoolean;
        this.acceptScalarAsString = acceptScalarAsString == null ? lenient : acceptScalarAsString;
        this.unwrapSingleValueArrays = unwrapSingleValueArrays == null ? lenient : unwrapSingleValueArrays;
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
}
