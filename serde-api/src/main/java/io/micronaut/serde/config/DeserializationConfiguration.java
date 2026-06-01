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
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for deserialization.
 */
public interface DeserializationConfiguration {
    String PREFIX = SerdeConfiguration.PREFIX + ".deserialization";

    @SuppressWarnings({"java:S1214", "java:S2386"})
    Set<Feature> DEFAULT_FEATURES = DeserializationConfiguration.features(null);

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
     * Whether a null field for a primitive should fail the deserialization. Defaults to {@code true}
     * @return True if a null field for a primitive should fail the deserialization
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isFailOnNullForPrimitives() {
        return true;
    }

    /**
     * Whether the supertype is used by default when no supertype is resolved.
     * @return True to avoid the supertype and use `defaultImpl` property
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean isSubtypesRequireDefaultImpl() {
        return true;
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
     * Determines whether scalar values can be accepted as single-element arrays.
     *
     * @return {@code true} if scalar values can be read as arrays
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean acceptSingleValueAsArray() {
        return false;
    }

    /**
     * Determines whether properties should be matched case-insensitively.
     *
     * @return {@code true} if property names are case-insensitive
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean acceptCaseInsensitiveProperties() {
        return false;
    }

    /**
     * Determines whether unknown enum values should deserialize as {@code null}.
     *
     * @return {@code true} if unknown enum values should deserialize as {@code null}
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean readUnknownEnumValuesAsNull() {
        return false;
    }

    /**
     * Determines whether unknown enum values should use the configured default enum value.
     *
     * @return {@code true} if unknown enum values should use the default enum value
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean readUnknownEnumValuesUsingDefaultValue() {
        return false;
    }

    /**
     * Determines whether numeric timestamps are read with nanosecond precision.
     *
     * @return {@code true} if numeric timestamps use nanoseconds
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.TRUE)
    default boolean readDateTimestampsAsNanoseconds() {
        return true;
    }

    /**
     * Determines whether temporal values are adjusted to the configured context time zone.
     *
     * @return {@code true} if dates should be adjusted to the context time zone
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean adjustDatesToContextTimeZone() {
        return false;
    }

    /**
     * Determines whether every creator parameter must be present in the JSON input.
     *
     * @return {@code true} if all creator parameters are required
     * @since 3.0.1
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean isRequireAllCreatorParameters() {
        return false;
    }

    /**
     * Determines whether generated deserializers should fall back to the runtime deserializer.
     *
     * @return {@code true} if generated deserializers should be disabled
     * @since 3.0
     */
    @Bindable(defaultValue = StringUtils.FALSE)
    default boolean disableGeneratedDeserializer() {
        return false;
    }

    /**
     * Returns the active format features for deserialization.
     *
     * @return The active format features for deserialization.
     * @since 3.0
     */
    @SuppressWarnings("AmbiguousMethodReference")
    default Set<Feature> features() {
        return features(this);
    }

    /**
     * Resolve the active format features for the given deserialization configuration.
     *
     * @param configuration The deserialization configuration
     * @return The active format features
     * @since 3.0
     */
    @SuppressWarnings({"AmbiguousMethodReference", "java:S3776"})
    static Set<Feature> features(@Nullable DeserializationConfiguration configuration) {
        EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
        if (configuration != null && configuration.acceptSingleValueAsArray()) {
            features.add(Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        }
        if (configuration != null && configuration.acceptCaseInsensitiveProperties()) {
            features.add(Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        }
        if (configuration != null && configuration.readUnknownEnumValuesAsNull()) {
            features.add(Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        }
        if (configuration != null && configuration.readUnknownEnumValuesUsingDefaultValue()) {
            features.add(Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
        }
        if (configuration == null || configuration.readDateTimestampsAsNanoseconds()) {
            features.add(Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
        }
        if (configuration != null && configuration.acceptCaseInsensitiveEnums()) {
            features.add(Feature.ACCEPT_CASE_INSENSITIVE_VALUES);
        }
        if (configuration != null && configuration.adjustDatesToContextTimeZone()) {
            features.add(Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        }
        if (configuration != null && configuration.disableGeneratedDeserializer()) {
            features.add(Feature.DISABLE_GENERATED_DESERIALIZER);
        }
        return Set.copyOf(features);
    }

    /**
     * Deserialization format features.
     *
     * @since 3.0
     */
    enum Feature {
        /**
         * Accept a scalar JSON value as a single-element array or collection.
         */
        ACCEPT_SINGLE_VALUE_AS_ARRAY,
        /**
         * Match bean property names without considering case.
         */
        ACCEPT_CASE_INSENSITIVE_PROPERTIES,
        /**
         * Deserialize unknown enum values as {@code null}.
         */
        READ_UNKNOWN_ENUM_VALUES_AS_NULL,
        /**
         * Deserialize unknown enum values using the enum constant marked as the default value.
         */
        READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE,
        /**
         * Interpret numeric date/time timestamps as nanoseconds when timestamps are enabled.
         */
        READ_DATE_TIMESTAMPS_AS_NANOSECONDS,
        /**
         * Match scalar values, such as enum names, without considering case.
         */
        ACCEPT_CASE_INSENSITIVE_VALUES,
        /**
         * Adjust date/time values to the configured context time zone during deserialization.
         */
        ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        /**
         * Disable generated deserializers and use the runtime deserializer.
         */
        DISABLE_GENERATED_DESERIALIZER
    }
}
