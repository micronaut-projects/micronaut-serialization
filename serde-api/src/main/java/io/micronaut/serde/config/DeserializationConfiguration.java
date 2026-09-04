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

import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;

/**
 * Configuration for deserialization.
 */
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

    /**
     * Whether a null field or a missing value for a primitive should fail the deserialization. Defaults to {@code false}
     * @return True if a null field or a missing value for a primitive should fail the deserialization
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean isFailOnNullForPrimitives() {
        return false;
    }

    /**
     * Whether the supertype is used by default when no supertype is resolved.
     * @return True to avoid the supertype and use `defaultImpl` property
     */
    @NextMajorVersion("Inline to true to have the behaviour the same as for Jackson")
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean isSubtypesRequireDefaultImpl() {
        return false;
    }

    /**
     * Determines whether to accept case-insensitive enumeration values during deserialization.
     * By default, case-insensitive enums are not accepted.
     *
     * @return {@code true} if case-insensitive enumeration values are accepted; {@code false} otherwise
     * @since 2.15.2
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean acceptCaseInsensitiveEnums() {
        return false;
    }

    /**
     * The baseline for scalar coercions performed during deserialization. Individual coercions can
     * be enabled or disabled explicitly.
     *
     * @return The coercion mode
     */
    @Bindable(defaultValue = "LENIENT")
    default CoercionMode getCoercionMode() {
        return CoercionMode.LENIENT;
    }

    /**
     * Whether floating-point numbers should be read into integer properties, truncating them.
     *
     * @return {@code true} if floating-point values should be accepted as integers
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isAcceptFloatAsInt() {
        return getCoercionMode() != CoercionMode.STRICT;
    }

    /**
     * Whether strings should be read into numeric properties.
     *
     * @return {@code true} if strings should be accepted as numbers
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isAcceptStringAsNumber() {
        return getCoercionMode() != CoercionMode.STRICT;
    }

    /**
     * Whether booleans should be read into numeric properties.
     *
     * @return {@code true} if booleans should be accepted as numbers
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isAcceptBooleanAsNumber() {
        return getCoercionMode() != CoercionMode.STRICT;
    }

    /**
     * Whether numbers should be read into boolean properties.
     *
     * @return {@code true} if numbers should be accepted as booleans
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isAcceptNumberAsBoolean() {
        return getCoercionMode() != CoercionMode.STRICT;
    }

    /**
     * Whether strings should be read into boolean properties.
     *
     * @return {@code true} if strings should be accepted as booleans
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isAcceptStringAsBoolean() {
        return getCoercionMode() != CoercionMode.STRICT;
    }

    /**
     * Whether numbers and booleans should be read into string properties.
     *
     * @return {@code true} if scalar values should be accepted as strings
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isAcceptScalarAsString() {
        return getCoercionMode() != CoercionMode.STRICT;
    }

    /**
     * Whether a single-element array should be read into a scalar property.
     *
     * @return {@code true} if single-element arrays should be unwrapped
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isUnwrapSingleValueArrays() {
        return getCoercionMode() != CoercionMode.STRICT;
    }
}
