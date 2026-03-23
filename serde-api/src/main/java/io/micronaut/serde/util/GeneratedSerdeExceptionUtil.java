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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;

import java.io.IOException;

/**
 * Exception helpers used by generated serdes.
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeExceptionUtil {

    private GeneratedSerdeExceptionUtil() {
    }

    /**
     * Creates a {@link SerdeException} for an unknown property and enriches it with the property path.
     *
     * @param propertyName The unknown property name.
     * @param beanType The declaring bean argument.
     * @return The configured exception.
     */
    public static SerdeException unknownProperty(String propertyName, Argument<?> beanType) {
        SerdeException serdeException = new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    /**
     * Creates a {@link SerdeException} for a duplicate property and enriches it with the property path.
     *
     * @param propertyName The duplicate property name.
     * @param beanType The declaring bean argument.
     * @return The configured exception.
     */
    public static SerdeException duplicateProperty(String propertyName, Argument<?> beanType) {
        SerdeException serdeException = new SerdeException("Duplicate property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    /**
     * Creates a {@link SerdeException} for an unknown enum value.
     *
     * @param enumType The enum argument being deserialized.
     * @param value The incoming value that could not be resolved.
     * @return The configured exception.
     */
    public static SerdeException unknownEnumValue(Argument<?> enumType, String value) {
        Object[] constants = enumType.getType().getEnumConstants();
        String acceptedValues = constants == null ? "[]" : java.util.Arrays.toString(constants);
        return new SerdeException("Cannot deserialize value of type `" + enumType.getType().getName() + "` due to: Expected one of " + acceptedValues + " but was '" + value + "'");
    }

    /**
     * Handles an unknown property according to deserialization configuration.
     *
     * @param decoder The decoder currently positioned on the unknown value.
     * @param context The decoder context.
     * @param propertyName The unknown property name.
     * @param beanType The declaring bean argument.
     * @throws IOException If skipping the value fails.
     */
    public static void handleUnknownProperty(Decoder decoder,
                                             Deserializer.DecoderContext context,
                                             String propertyName,
                                             Argument<?> beanType) throws IOException {
        boolean ignoreUnknown = context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::isIgnoreUnknown)
            .orElse(true);
        if (ignoreUnknown) {
            decoder.skipValue();
        } else {
            throw unknownProperty(propertyName, beanType);
        }
    }

    /**
     * Appends a property path segment to an existing {@link SerdeException}.
     *
     * @param exception The exception to enrich.
     * @param beanType The declaring bean argument.
     * @param propertyName The property name.
     * @param propertyArgument The property argument.
     * @return The same exception instance.
     */
    public static SerdeException withPropertyPath(SerdeException exception,
                                                  Argument<?> beanType,
                                                  String propertyName,
                                                  Argument<?> propertyArgument) {
        exception.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument.withName(propertyName)));
        return exception;
    }

    /**
     * Converts any {@link Throwable} into a {@link SerdeException} and appends a property path segment.
     *
     * @param exception The original exception.
     * @param beanType The declaring bean argument.
     * @param propertyName The property name.
     * @param propertyArgument The property argument.
     * @return A serde exception enriched with property path information.
     */
    public static SerdeException withPropertyPath(Throwable exception,
                                                  Argument<?> beanType,
                                                  String propertyName,
                                                  Argument<?> propertyArgument) {
        SerdeException serdeException = exception instanceof SerdeException existing
            ? existing
            : new SerdeException("Error processing property [" + propertyName + "] of type [" + beanType + "]: " + exception.getMessage(), exception);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument.withName(propertyName)));
        return serdeException;
    }
}
