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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

/**
 * Inclusion helpers used by generated serializers so global
 * {@code micronaut.serde.serialization.inclusion} is honored without falling
 * back to the runtime object serializer for every simple shape.
 *
 * @since 3.1.1
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeInclusionUtil {

    private GeneratedSerdeInclusionUtil() {
    }

    /**
     * Whether a property value should be written using the active global inclusion strategy
     * and the property serializer's empty/absent/default checks.
     *
     * @param context    The encoder context
     * @param serializer The property serializer
     * @param value      The property value
     * @return {@code true} if the property should be serialized
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean shouldSerialize(Serializer.EncoderContext context,
                                          Serializer serializer,
                                          @Nullable Object value) {
        SerdeConfig.SerInclude include = resolveInclusion(context);
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> true;
            case NON_NULL -> value != null;
            case NON_ABSENT -> !serializer.isAbsent(context, value);
            case NON_EMPTY -> !serializer.isEmpty(context, value);
            case NON_DEFAULT -> !serializer.isEmpty(context, value)
                && (value == null || !serializer.isDefault(context, value));
            case NEVER -> false;
        };
    }

    /**
     * Whether a scalar reference property (encoded without a cached property serializer)
     * should be written using the active global inclusion strategy.
     *
     * @param context The encoder context
     * @param value   The property value
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializeScalar(Serializer.EncoderContext context,
                                                @Nullable Object value) {
        SerdeConfig.SerInclude include = resolveInclusion(context);
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> true;
            case NEVER -> false;
            case NON_NULL, NON_ABSENT -> value != null;
            case NON_EMPTY -> value != null && !isEmptyCharSequence(value);
            case NON_DEFAULT -> value != null && !isDefaultScalar(value);
        };
    }

    /**
     * Whether a primitive property should be written using the active global inclusion strategy.
     *
     * @param context   The encoder context
     * @param isDefault Whether the primitive value equals the Java language default for its type
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializePrimitive(Serializer.EncoderContext context,
                                                   boolean isDefault) {
        SerdeConfig.SerInclude include = resolveInclusion(context);
        return switch (include) {
            case NEVER -> false;
            case NON_DEFAULT -> !isDefault;
            default -> true;
        };
    }

    private static SerdeConfig.SerInclude resolveInclusion(Serializer.EncoderContext context) {
        return context.getSerializationConfiguration()
            .map(SerializationConfiguration::getInclusion)
            .orElse(SerdeConfig.SerInclude.NON_EMPTY);
    }

    private static boolean isEmptyCharSequence(Object value) {
        return value instanceof CharSequence charSequence && charSequence.isEmpty();
    }

    /**
     * Default-value check for scalar wrappers encoded without a property serializer field.
     * Empty char sequences are treated as default (matches {@code NON_DEFAULT} vs {@code NON_EMPTY}
     * for strings); numeric zero / false / NUL match the corresponding number/boolean serdes.
     */
    private static boolean isDefaultScalar(Object value) {
        if (value instanceof CharSequence charSequence) {
            return charSequence.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() == 0d;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        if (value instanceof Character character) {
            return character == '\0';
        }
        return false;
    }
}

