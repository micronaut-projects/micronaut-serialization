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
package io.micronaut.serde;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.PropertyReferenceManager;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Interface that represents a deserializer.
 *
 * @param <T> The generic type that the deserializer can deserialize
 * @author Jonas Konrad
 * @author graemerocher
 */
@Indexed(Deserializer.class)
public interface Deserializer<T> {
    /**
     * Create a new child deserializer or return this if non is necessary for the given context.
     *
     * @param context The decoder context
     * @param type The context, including any annotation metadata and type information to narrow the deserializer type
     * @return An instance of the same type of deserializer
     */
    default Deserializer<T> createSpecific(DecoderContext context,
                                                    Argument<? super T> type) throws SerdeException {
        return this;
    }

    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}.
     *
     * @param decoder The decoder, never {@code null}
     * @param context The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     * @return The deserialized object or {@code null}
     * @throws IOException If an error occurs during deserialization of the object
     */
    @Nullable
    T deserialize(
            Decoder decoder,
            DecoderContext context,
            Argument<? super T> type) throws IOException;

    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}. If
     * the decoder value is {@code null}, this <i>must</i> be permitted. By default, in this case,
     * this method will return {@code null}.
     *
     * @param decoder The decoder, never {@code null}
     * @param context The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     * @return The deserialized object or {@code null}
     * @throws IOException If an error occurs during deserialization of the object
     * @since 2.0.0
     */
    default @Nullable T deserializeNullable(
        Decoder decoder,
        DecoderContext context,
        Argument<? super T> type) throws IOException {
        return decoder.decodeNull() ? null : deserialize(decoder, context, type);
    }

    /**
     * Obtains a default value that can be returned from this deserializer in the case where a value is absent.
     * @return The default value
     * @param context The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     */
    default @Nullable T getDefaultValue(DecoderContext context,
                                        Argument<? super T> type) {
        return null;
    }

    /**
     * Context object passed to the {@link #deserialize(Decoder, io.micronaut.serde.Deserializer.DecoderContext, io.micronaut.core.type.Argument)} method along with the decoder.
     */
    interface DecoderContext extends PropertyReferenceManager, DeserializerLocator, NamingStrategyLocator {

        /**
         * @return Conversion service
         */
        default ConversionService getConversionService() {
            return ConversionService.SHARED;
        }

        /**
         * @param views Views to check.
         * @return {@code true} iff any of the given views is enabled.
         */
        default boolean hasView(Class<?>... views) {
            return true;
        }


        /**
         * Resolve a reference for the given type and value.
         * @param reference The reference
         * @param <B> The bean type
         * @param <P> The generic type of the value
         * @return The existing reference, a new one or {@code null} if serialization should be skipped
         */
        @Internal
        @Nullable
        <B, P> PropertyReference<B, P> resolveReference(
                PropertyReference<B, P> reference
        );

        /**
         * Get the {@link SerdeConfiguration} for this context.
         *
         * @return The {@link SerdeConfiguration}, or an empty optional if the default should be used
         * @since 2.7.0
         */
        default Optional<SerdeConfiguration> getSerdeConfiguration() {
            return Optional.empty();
        }

        /**
         * Get the {@link DeserializationConfiguration} for this context.
         *
         * @return The {@link DeserializationConfiguration}, or an empty optional if the default should be used
         * @since 2.7.0
         */
        default Optional<DeserializationConfiguration> getDeserializationConfiguration() {
            return Optional.empty();
        }

        /**
         * Get the active deserialization format features for this context.
         *
         * @return The active deserialization format features
         * @since 3.0
         */
        default Set<DeserializationConfiguration.Feature> getFeatures() {
            return getDeserializationConfiguration()
                .map(configuration -> configuration.features())
                .orElseGet(() -> DeserializationConfiguration.features(null));
        }

        /**
         * Create a context with deserialization format features overridden by annotation metadata.
         *
         * @param includedFeatures Features to enable
         * @param excludedFeatures Features to disable
         * @return The derived context
         * @since 3.0
         */
        default DecoderContext withFeatures(Set<DeserializationConfiguration.Feature> includedFeatures,
                                            Set<DeserializationConfiguration.Feature> excludedFeatures) {
            if (includedFeatures.isEmpty() && excludedFeatures.isEmpty()) {
                return this;
            }
            return new FeatureDecoderContext(this, overrideFeatures(getFeatures(), includedFeatures, excludedFeatures));
        }

        private static Set<DeserializationConfiguration.Feature> overrideFeatures(Set<DeserializationConfiguration.Feature> base,
                                                                                  Set<DeserializationConfiguration.Feature> includedFeatures,
                                                                                  Set<DeserializationConfiguration.Feature> excludedFeatures) {
            EnumSet<DeserializationConfiguration.Feature> features = EnumSet.noneOf(DeserializationConfiguration.Feature.class);
            features.addAll(base);
            features.addAll(includedFeatures);
            features.removeAll(excludedFeatures);
            return Set.copyOf(features);
        }
    }
}
