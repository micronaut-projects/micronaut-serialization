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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Enum helpers used by generated serdes.
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeEnumUtil {

    private static final ConcurrentMap<Class<?>, EnumLookup<?>> ENUM_LOOKUPS = new ConcurrentHashMap<>();

    private GeneratedSerdeEnumUtil() {
    }

    /**
     * Resolves an enum constant from serialized input.
     *
     * @param enumType        The enum type.
     * @param serializedValue The serialized value.
     * @param context         The decoder context.
     * @param <E>             The enum type.
     * @return The matching enum constant.
     */
    @SuppressWarnings("java:S1172")
    public static <E extends Enum<E>> E enumValueOf(Class<E> enumType, String serializedValue, Deserializer.DecoderContext context) {
        E resolved = enumLookup(enumType).resolve(serializedValue);
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

    private record EnumLookup<E extends Enum<E>>(Map<E, String> serializedNames,
                                                 Map<String, E> bySerialized,
                                                 Map<String, E> bySerializedLowerCase) {

        private @Nullable E resolve(String serializedValue) {
            return bySerialized.get(serializedValue);
        }
    }
}
