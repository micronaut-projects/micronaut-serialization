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
 * Inclusion helpers used by generated serializers so that the global
 * {@code micronaut.serde.serialization.inclusion} setting is honored without falling back to the
 * runtime object serializer for every simple shape.
 *
 * <p>The active inclusion is resolved once, when a generated serializer is created, and is stored in
 * a generated field. The per-property methods take the already resolved inclusion so that no
 * configuration lookup happens on the serialization hot path.</p>
 *
 * <p>The scalar methods mirror the {@code isEmpty} / {@code isAbsent} / {@code isDefault} contract of
 * the runtime serde that would otherwise write the value, so a generated serializer and the runtime
 * object serializer agree for the same model and configuration.</p>
 *
 * @since 3.2
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeInclusionUtil {

    private GeneratedSerdeInclusionUtil() {
    }

    /**
     * Resolve the inclusion to apply for the given context. Invoked once per generated serializer
     * instance, never on the per-property path.
     *
     * @param context The encoder context
     * @return The active inclusion
     */
    public static SerdeConfig.SerInclude resolveInclusion(Serializer.EncoderContext context) {
        SerializationConfiguration configuration = context.getSerializationConfiguration().orElse(null);
        // Matches CustomizedObjectSerializer, which also falls back to ALWAYS for a context that
        // exposes no serialization configuration.
        return configuration == null ? SerdeConfig.SerInclude.ALWAYS : configuration.getInclusion();
    }

    /**
     * Whether the resolved inclusion writes every property, which lets generated serializers skip the
     * per-property inclusion check entirely.
     *
     * @param include The resolved inclusion
     * @return {@code true} if no property can be skipped
     */
    public static boolean includeAlways(SerdeConfig.SerInclude include) {
        return include == SerdeConfig.SerInclude.ALWAYS || include == SerdeConfig.SerInclude.USE_DEFAULTS;
    }

    /**
     * Whether a property written by the given serializer should be included.
     *
     * @param include    The resolved inclusion
     * @param context    The encoder context
     * @param serializer The property serializer
     * @param value      The property value
     * @return {@code true} if the property should be serialized
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean shouldSerialize(SerdeConfig.SerInclude include,
                                          Serializer.EncoderContext context,
                                          Serializer serializer,
                                          @Nullable Object value) {
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
     * Whether a primitive property should be included. Primitive values are never {@code null} and
     * never empty, so only {@code NEVER} and {@code NON_DEFAULT} can skip them.
     *
     * @param include   The resolved inclusion
     * @param isDefault Whether the value equals the default the matching serde reports for its type
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializePrimitive(SerdeConfig.SerInclude include, boolean isDefault) {
        return switch (include) {
            case NEVER -> false;
            case NON_DEFAULT -> !isDefault;
            default -> true;
        };
    }

    /**
     * Whether a {@link String} property should be included. Mirrors {@code StringSerde}, which reports
     * {@code null} and the empty string as empty and reports no default value.
     *
     * @param include The resolved inclusion
     * @param value   The property value
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializeString(SerdeConfig.SerInclude include, @Nullable String value) {
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> true;
            case NON_NULL, NON_ABSENT -> value != null;
            // NON_DEFAULT skips empty values too, and StringSerde reports no default value
            case NON_EMPTY, NON_DEFAULT -> value != null && !value.isEmpty();
            case NEVER -> false;
        };
    }

    /**
     * Whether a {@link Number} property should be included. Mirrors the number serdes, which report
     * only {@code null} as empty and treat the boxed zero of their own type as the default value.
     *
     * @param include The resolved inclusion
     * @param value   The property value
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializeNumber(SerdeConfig.SerInclude include, @Nullable Number value) {
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> true;
            case NON_NULL, NON_ABSENT, NON_EMPTY -> value != null;
            case NON_DEFAULT -> value != null && !isDefaultNumber(value);
            case NEVER -> false;
        };
    }

    /**
     * Whether a {@link Boolean} property should be included. Mirrors {@code BooleanSerde}, which
     * treats {@code false} as the default value.
     *
     * @param include The resolved inclusion
     * @param value   The property value
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializeBoolean(SerdeConfig.SerInclude include, @Nullable Boolean value) {
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> true;
            case NON_NULL, NON_ABSENT, NON_EMPTY -> value != null;
            case NON_DEFAULT -> value != null && value;
            case NEVER -> false;
        };
    }

    /**
     * Whether a {@link Character} property should be included. Mirrors {@code CharSerde}, which treats
     * the NUL character as the default value.
     *
     * @param include The resolved inclusion
     * @param value   The property value
     * @return {@code true} if the property should be serialized
     */
    public static boolean shouldSerializeCharacter(SerdeConfig.SerInclude include, @Nullable Character value) {
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> true;
            case NON_NULL, NON_ABSENT, NON_EMPTY -> value != null;
            // Character.MIN_VALUE is the NUL character CharSerde reports as the default value
            case NON_DEFAULT -> value != null && !value.equals(Character.MIN_VALUE);
            case NEVER -> false;
        };
    }

    /**
     * Default-value check matching the number serdes registered for each boxed type. Only the boxed
     * primitive wrappers override {@code Serializer#isDefault}; {@link java.math.BigInteger},
     * {@link java.math.BigDecimal} and any other {@link Number} keep the {@code false} default, so
     * treating their zero as a default value here would omit values the runtime path writes.
     *
     * @param value The value
     * @return {@code true} if the value is the default value for its type
     */
    private static boolean isDefaultNumber(Number value) {
        return switch (value) {
            case Integer integer -> integer.equals(0);
            case Long longValue -> longValue.equals(0L);
            case Double doubleValue -> doubleValue.equals(0D);
            case Float floatValue -> floatValue.equals(0F);
            case Short shortValue -> shortValue.equals((short) 0);
            case Byte byteValue -> byteValue.equals((byte) 0);
            default -> false;
        };
    }
}
