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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.exceptions.NullValueSerdeException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import org.jspecify.annotations.Nullable;

/**
 * Exception helpers used by generated serdes.
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeExceptionUtil {

    private GeneratedSerdeExceptionUtil() {
    }

    /**
     * Result of a generated deserializer property dispatch.
     *
     * @since 3.0
     */
    public enum PropertyDispatchResult {
        /**
         * The property was handled successfully.
         */
        HANDLED,
        /**
         * No property matched the incoming name.
         */
        UNKNOWN,
        /**
         * The property was already handled.
         */
        DUPLICATE,
        /**
         * The property rejected a null value.
         */
        NULL
    }

    /**
     * Creates a {@link SerdeException} for an unknown property and enriches it with the property path.
     *
     * @param beanType         The declaring bean argument.
     * @param propertyArgument The property argument.
     * @return The configured exception.
     */
    public static SerdeException unknownProperty(Argument<?> beanType,
                                                 Argument<?> propertyArgument) {
        String propertyName = propertyArgument.getName();
        SerdeException serdeException = new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument));
        return serdeException;
    }

    /**
     * Creates a {@link SerdeException} for a duplicate property and enriches it with the property path.
     *
     * @param beanType         The declaring bean argument.
     * @param propertyArgument The property argument.
     * @return The configured exception.
     */
    public static SerdeException duplicateProperty(Argument<?> beanType,
                                                   Argument<?> propertyArgument) {
        String propertyName = propertyArgument.getName();
        SerdeException serdeException = new SerdeException("Duplicate property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument));
        return serdeException;
    }

    /**
     * Creates a {@link SerdeException} for an unknown enum value.
     *
     * @param enumType The enum argument being deserialized.
     * @param value    The incoming value that could not be resolved.
     * @return The configured exception.
     */
    public static SerdeException unknownEnumValue(Argument<?> enumType, String value) {
        Object[] constants = enumType.getType().getEnumConstants();
        String acceptedValues = constants == null ? "[]" : java.util.Arrays.toString(constants);
        return new SerdeException("Cannot deserialize value of type `" + enumType.getType().getName() + "` due to: Expected one of " + acceptedValues + " but was '" + value + "'");
    }

    /**
     * Handles an unknown enum value according to deserialization configuration.
     *
     * @param context  The decoder context.
     * @param enumType The enum argument being deserialized.
     * @param value    The incoming value that could not be resolved.
     * @param <E>      The enum type.
     * @return {@code null} when unknown enum values are configured to deserialize as null.
     * @throws SerdeException If the value should fail deserialization.
     * @since 3.0
     */
    public static <E extends Enum<E>> @Nullable E handleUnknownEnumValue(Deserializer.DecoderContext context,
                                                                         Argument<E> enumType,
                                                                         String value) throws SerdeException {
        boolean unknownAsNull = context.getFeatures().contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        if (unknownAsNull) {
            return null;
        }
        throw unknownEnumValue(enumType, value);
    }

    /**
     * Creates an exception for a null value assigned to a property that cannot accept null.
     *
     * @param beanType         The declaring bean argument.
     * @param propertyArgument The property argument.
     * @return The configured exception.
     * @since 3.0
     */
    public static SerdeException nullValue(Argument<?> beanType,
                                           Argument<?> propertyArgument) {
        return new SerdeException("Unable to deserialize type [" + beanType.getType().getName() +
            "]. Non-null property [" + propertyArgument + "] is null in the supplied data");
    }

    /**
     * Whether primitive values should reject explicit null input.
     *
     * @param context The decoder context.
     * @return {@code true} if explicit null primitive values should fail deserialization
     * @since 3.0
     */
    public static boolean failOnNullForPrimitives(Deserializer.DecoderContext context) {
        return context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::isFailOnNullForPrimitives)
            .orElse(true);
    }

    /**
     * Whether unknown properties should be skipped.
     *
     * @param context The decoder context.
     * @return {@code true} if unknown properties should be ignored
     * @since 3.0
     */
    public static boolean ignoreUnknown(Deserializer.DecoderContext context) {
        return context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::isIgnoreUnknown)
            .orElse(true);
    }

    /**
     * Whether constructor arguments should reject missing or explicit null values.
     *
     * @param context The decoder context.
     * @return {@code true} if strict nullable deserialization is enabled
     * @since 3.0
     */
    public static boolean strictNullable(Deserializer.DecoderContext context) {
        return context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::isStrictNullable)
            .orElse(false);
    }

    /**
     * Creates an exception for a constructor parameter rejected by strict nullable deserialization.
     *
     * @param beanType         The declaring bean argument.
     * @param propertyArgument The property argument.
     * @return The configured exception.
     * @since 3.0
     */
    public static SerdeException strictNullableConstructorParameter(Argument<?> beanType,
                                                                    Argument<?> propertyArgument) {
        SerdeException serdeException = new SerdeException("Unable to deserialize type [" + beanType.getType().getName() +
            "]. Non-null constructor parameter [" + propertyArgument + "] is not present or is null in the supplied data");
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument));
        return serdeException;
    }

    /**
     * Converts any {@link Throwable} into a {@link SerdeException} and appends a property path segment.
     *
     * @param exception        The original exception.
     * @param beanType         The declaring bean argument.
     * @param propertyArgument The property argument.
     * @return A serde exception enriched with property path information.
     */
    public static SerdeException withPropertyPath(Throwable exception,
                                                  Argument<?> beanType,
                                                  Argument<?> propertyArgument) {
        SerdeException serdeException;
        if (exception instanceof NullValueSerdeException) {
            serdeException = nullValue(beanType, propertyArgument);
        } else if (exception instanceof SerdeException existing) {
            serdeException = existing;
        } else {
            serdeException = new SerdeException("Error processing property [" + propertyArgument.getName() + "] of type [" + beanType + "]: " + exception.getMessage(), exception);
        }
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument));
        return serdeException;
    }
}
