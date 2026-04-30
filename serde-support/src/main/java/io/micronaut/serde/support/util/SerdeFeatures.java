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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * Helpers for resolving format feature overrides from annotation metadata.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public final class SerdeFeatures {

    private SerdeFeatures() {
    }

    /**
     * @param annotationMetadata The annotation metadata
     * @return Serialization features to enable
     */
    @NonNull
    public static Set<SerdeConfiguration.Feature> serializationFeaturesWith(@NonNull AnnotationMetadata annotationMetadata) {
        return features(annotationMetadata, SerdeConfig.FEATURES_WITH, SerdeConfiguration.Feature.class);
    }

    /**
     * @param annotationMetadata The annotation metadata
     * @return Serialization features to disable
     */
    @NonNull
    public static Set<SerdeConfiguration.Feature> serializationFeaturesWithout(@NonNull AnnotationMetadata annotationMetadata) {
        return features(annotationMetadata, SerdeConfig.FEATURES_WITHOUT, SerdeConfiguration.Feature.class);
    }

    /**
     * @param annotationMetadata The annotation metadata
     * @return Deserialization features to enable
     */
    @NonNull
    public static Set<DeserializationConfiguration.Feature> deserializationFeaturesWith(@NonNull AnnotationMetadata annotationMetadata) {
        return features(annotationMetadata, SerdeConfig.FEATURES_WITH, DeserializationConfiguration.Feature.class);
    }

    /**
     * @param annotationMetadata The annotation metadata
     * @return Deserialization features to disable
     */
    @NonNull
    public static Set<DeserializationConfiguration.Feature> deserializationFeaturesWithout(@NonNull AnnotationMetadata annotationMetadata) {
        return features(annotationMetadata, SerdeConfig.FEATURES_WITHOUT, DeserializationConfiguration.Feature.class);
    }

    /**
     * Create a serialization context with annotation feature overrides.
     *
     * @param context            The encoder context
     * @param annotationMetadata The annotation metadata
     * @return The derived encoder context
     */
    public static Serializer.EncoderContext withFeatures(Serializer.EncoderContext context,
                                                         @NonNull AnnotationMetadata annotationMetadata) {
        return context.withFeatures(
            serializationFeaturesWith(annotationMetadata),
            serializationFeaturesWithout(annotationMetadata)
        );
    }

    /**
     * Create a deserialization context with annotation feature overrides.
     *
     * @param context            The decoder context
     * @param annotationMetadata The annotation metadata
     * @return The derived decoder context
     */
    public static Deserializer.DecoderContext withFeatures(Deserializer.DecoderContext context,
                                                           @NonNull AnnotationMetadata annotationMetadata) {
        return context.withFeatures(
            deserializationFeaturesWith(annotationMetadata),
            deserializationFeaturesWithout(annotationMetadata)
        );
    }

    private static <E extends Enum<E>> Set<E> features(AnnotationMetadata annotationMetadata,
                                                       String member,
                                                       Class<E> featureType) {
        EnumSet<E> features = EnumSet.noneOf(featureType);
        for (String featureName : annotationMetadata.stringValues(SerdeConfig.class, member)) {
            try {
                features.add(Enum.valueOf(featureType, featureName));
            } catch (IllegalArgumentException ignored) {
                // The member can contain features for the opposite direction.
            }
        }
        return Set.copyOf(features);
    }
}
