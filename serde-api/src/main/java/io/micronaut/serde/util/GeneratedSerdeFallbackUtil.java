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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Fallback helpers used by generated serdes when a runtime object serde is required.
 *
 * @since 3.0
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeFallbackUtil {

    private GeneratedSerdeFallbackUtil() {
    }

    /**
     * Returns the generated deserializer unless the active features require the runtime object deserializer.
     *
     * @param generatedDeserializer The generated deserializer
     * @param context The decoder context
     * @param type The target type
     * @return The generated deserializer or a runtime object deserializer
     * @throws SerdeException If no deserializer can be resolved
     * @since 3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Deserializer<?> withRuntimeObjectFallback(Deserializer<?> generatedDeserializer,
                                                            Deserializer.DecoderContext context,
                                                            Argument<?> type) throws SerdeException {
        if (!context.getFeatures().contains(DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)) {
            return generatedDeserializer;
        }
        Deserializer<?> objectDeserializer = context.findDeserializer((Argument) Argument.OBJECT_ARGUMENT);
        return objectDeserializer.createSpecific(context, (Argument) type);
    }

    /**
     * Returns a runtime formatted deserializer for generated enum deserializers.
     *
     * @param context The decoder context
     * @param type The target type
     * @param format The format configuration
     * @return A runtime formatted enum deserializer
     * @throws SerdeException If no deserializer can be resolved
     * @since 3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Deserializer<?> runtimeFormattedEnumDeserializer(Deserializer.DecoderContext context,
                                                                   Argument<?> type,
                                                                   FormatConfiguration format) throws SerdeException {
        Deserializer<?> enumDeserializer = context.findDeserializer(Enum.class);
        if (enumDeserializer instanceof FormattedDeserializer formattedDeserializer) {
            return formattedDeserializer.createSpecific(context, type, format);
        }
        return enumDeserializer.createSpecific(context, (Argument) type);
    }

    /**
     * Returns a runtime deserializer for generated enum deserializers.
     *
     * @param context The decoder context
     * @param type The target type
     * @return A runtime enum deserializer
     * @throws SerdeException If no deserializer can be resolved
     * @since 3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Deserializer<?> runtimeEnumDeserializer(Deserializer.DecoderContext context,
                                                          Argument<?> type) throws SerdeException {
        Deserializer<?> enumDeserializer = context.findDeserializer(Enum.class);
        return enumDeserializer.createSpecific(context, (Argument) type);
    }

    /**
     * Returns the generated enum deserializer unless feature override metadata requires the runtime enum deserializer.
     *
     * @param generatedDeserializer The generated enum deserializer
     * @param context The decoder context
     * @param type The target type
     * @return The generated deserializer or a runtime enum deserializer
     * @throws SerdeException If no deserializer can be resolved
     * @since 3.0
     */
    public static Deserializer<?> withRuntimeEnumFallback(Deserializer<?> generatedDeserializer,
                                                          Deserializer.DecoderContext context,
                                                          Argument<?> type) throws SerdeException {
        return hasFeatureOverrides(type) ? runtimeEnumDeserializer(context, type) : generatedDeserializer;
    }

    /**
     * Returns the generated serializer unless the active features require the runtime object serializer.
     *
     * @param generatedSerializer The generated serializer
     * @param context The encoder context
     * @param type The target type
     * @return The generated serializer or a runtime object serializer
     * @throws SerdeException If no serializer can be resolved
     * @since 3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Serializer<?> withRuntimeObjectFallback(Serializer<?> generatedSerializer,
                                                          Serializer.EncoderContext context,
                                                          Argument<?> type) throws SerdeException {
        if (!hasUnsupportedSerializationFeatures(context)) {
            return generatedSerializer;
        }
        Serializer<?> objectSerializer = context.findSerializer((Argument) Argument.OBJECT_ARGUMENT);
        return objectSerializer.createSpecific(context, (Argument) type);
    }

    /**
     * Returns a runtime formatted serializer for generated enum serializers.
     *
     * @param context The encoder context
     * @param type The target type
     * @param format The format configuration
     * @return A runtime formatted enum serializer
     * @throws SerdeException If no serializer can be resolved
     * @since 3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Serializer<?> runtimeFormattedEnumSerializer(Serializer.EncoderContext context,
                                                               Argument<?> type,
                                                               FormatConfiguration format) throws SerdeException {
        Serializer<?> enumSerializer = context.findSerializer(Enum.class);
        if (enumSerializer instanceof FormattedSerializer formattedSerializer) {
            return formattedSerializer.createSpecific(context, type, format);
        }
        return enumSerializer.createSpecific(context, (Argument) type);
    }

    /**
     * Returns a runtime serializer for generated enum serializers.
     *
     * @param context The encoder context
     * @param type The target type
     * @return A runtime enum serializer
     * @throws SerdeException If no serializer can be resolved
     * @since 3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Serializer<?> runtimeEnumSerializer(Serializer.EncoderContext context,
                                                      Argument<?> type) throws SerdeException {
        Serializer<?> enumSerializer = context.findSerializer(Enum.class);
        return enumSerializer.createSpecific(context, (Argument) type);
    }

    /**
     * Returns the generated enum serializer unless feature override metadata requires the runtime enum serializer.
     *
     * @param generatedSerializer The generated enum serializer
     * @param context The encoder context
     * @param type The target type
     * @return The generated serializer or a runtime enum serializer
     * @throws SerdeException If no serializer can be resolved
     * @since 3.0
     */
    public static Serializer<?> withRuntimeEnumFallback(Serializer<?> generatedSerializer,
                                                        Serializer.EncoderContext context,
                                                        Argument<?> type) throws SerdeException {
        return hasFeatureOverrides(type) ? runtimeEnumSerializer(context, type) : generatedSerializer;
    }

    private static boolean hasFeatureOverrides(Argument<?> type) {
        AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        return annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITH).length > 0
            || annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITHOUT).length > 0;
    }

    private static boolean hasUnsupportedSerializationFeatures(Serializer.EncoderContext context) {
        return false;
    }
}
