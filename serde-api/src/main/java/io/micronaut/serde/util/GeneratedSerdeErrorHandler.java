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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared error and enum helper methods used by generated serdes.
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeErrorHandler {

    private static final ConcurrentMap<Class<?>, EnumLookup<?>> ENUM_LOOKUPS = new ConcurrentHashMap<>();

    private GeneratedSerdeErrorHandler() {
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
     * Returns the serialized name for an enum constant.
     *
     * @param enumValue The enum constant.
     * @param <E> The enum type.
     * @return The serialized name.
     */
    public static <E extends Enum<E>> String enumSerializedName(E enumValue) {
        @SuppressWarnings("unchecked") Class<E> enumType = (Class<E>) enumValue.getDeclaringClass();
        return enumLookup(enumType).serializedName(enumValue);
    }

    /**
     * Resolves an enum constant from serialized input.
     *
     * @param enumType The enum type.
     * @param serializedValue The serialized value.
     * @param context The decoder context.
     * @param <E> The enum type.
     * @return The matching enum constant.
     */
    public static <E extends Enum<E>> E enumValueOf(Class<E> enumType, String serializedValue, Deserializer.DecoderContext context) {
        boolean caseInsensitive = acceptCaseInsensitiveEnums(context);
        E resolved = enumLookup(enumType).resolve(serializedValue, caseInsensitive);
        if (resolved != null) {
            return resolved;
        }
        return Enum.valueOf(enumType, serializedValue);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> EnumLookup<E> enumLookup(Class<E> enumType) {
        return (EnumLookup<E>) ENUM_LOOKUPS.computeIfAbsent(enumType, type -> buildEnumLookup((Class<E>) type));
    }

    private static <E extends Enum<E>> EnumLookup<E> buildEnumLookup(Class<E> enumType) {
        Map<E, String> serializedNames = new HashMap<>();
        Map<String, E> bySerialized = new HashMap<>();
        Map<String, E> bySerializedLowerCase = new HashMap<>();
        try {
            BeanIntrospection<E> introspection = BeanIntrospection.getIntrospection(enumType);
            if (introspection instanceof EnumBeanIntrospection<E> enumBeanIntrospection) {
                for (EnumBeanIntrospection.EnumConstant<E> enumConstant : enumBeanIntrospection.getConstants()) {
                    E enumValue = enumConstant.getValue();
                    String serializedName = enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(enumValue.name());
                    serializedNames.put(enumValue, serializedName);
                    bySerialized.putIfAbsent(serializedName, enumValue);
                    bySerializedLowerCase.putIfAbsent(serializedName.toLowerCase(Locale.ROOT), enumValue);
                }
                return new EnumLookup<>(Map.copyOf(serializedNames), Map.copyOf(bySerialized), Map.copyOf(bySerializedLowerCase));
            }
        } catch (IntrospectionException ignore) {
        }
        E[] enumConstants = enumType.getEnumConstants();
        if (enumConstants != null) {
            for (E enumConstant : enumConstants) {
                String name = enumConstant.name();
                serializedNames.put(enumConstant, name);
                bySerialized.putIfAbsent(name, enumConstant);
                bySerializedLowerCase.putIfAbsent(name.toLowerCase(Locale.ROOT), enumConstant);
            }
        }
        return new EnumLookup<>(Map.copyOf(serializedNames), Map.copyOf(bySerialized), Map.copyOf(bySerializedLowerCase));
    }

    /**
     * Resolves whether case-insensitive enum deserialization is enabled for the given context.
     *
     * @param context The decoder context.
     * @return {@code true} if case-insensitive enum matching is enabled.
     */
    public static boolean acceptCaseInsensitiveEnums(Deserializer.DecoderContext context) {
        return context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::acceptCaseInsensitiveEnums)
            .orElse(false);
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

    private static final class EnumLookup<E extends Enum<E>> {
        private final Map<E, String> serializedNames;
        private final Map<String, E> bySerialized;
        private final Map<String, E> bySerializedLowerCase;

        private EnumLookup(Map<E, String> serializedNames,
                           Map<String, E> bySerialized,
                           Map<String, E> bySerializedLowerCase) {
            this.serializedNames = serializedNames;
            this.bySerialized = bySerialized;
            this.bySerializedLowerCase = bySerializedLowerCase;
        }

        private String serializedName(E enumValue) {
            return serializedNames.getOrDefault(enumValue, enumValue.name());
        }

        private E resolve(String serializedValue, boolean caseInsensitive) {
            E resolved = bySerialized.get(serializedValue);
            if (resolved != null) {
                return resolved;
            }
            if (caseInsensitive && serializedValue != null) {
                return bySerializedLowerCase.get(serializedValue.toLowerCase(Locale.ROOT));
            }
            return null;
        }
    }
}
