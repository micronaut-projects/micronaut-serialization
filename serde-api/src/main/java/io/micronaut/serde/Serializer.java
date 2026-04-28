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
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.PropertyReferenceManager;
import io.micronaut.serde.reference.SerializationReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Models a build time serializer. That is a class computed at build-time that can
 * be used to serialize an instance of {@link T}.
 *
 * @param <T> The type to be serialized
 * @author Jonas Konrad
 * @author graemerocher
 */
@Indexed(Serializer.class)
public interface Serializer<T> {

    /**
     * Create a more specific serializer for the given definition.
     * @param context The encoder context
     * @param type The type definition including any annotation metadata
     * @return The more specific serializer
     */
    default @NonNull
    Serializer<T> createSpecific(@NonNull EncoderContext context,
                                 @NonNull Argument<? extends T> type) throws
            SerdeException {
        return this;
    }

    /**
     * Serializes the given value using the passed {@link Encoder}.
     * @param encoder The encoder to use
     * @param context The encoder context, never {@code null}
     * @param type Models the generic type of the value
     * @param value The value, can be {@code null}
     * @throws IOException If an error occurs during serialization
     */
    void serialize(@NonNull Encoder encoder,
                   @NonNull EncoderContext context,
                   @NonNull Argument<? extends T> type,
                   @NonNull T value) throws IOException;

    /**
     * Used for {@code JsonInclude.Include#NON_EMPTY} checking.
     *
     * @param context The encoder context
     * @param value The check to check
     * @return Return {@code true} if the value is empty
     */
    default boolean isEmpty(@NonNull EncoderContext context, @Nullable T value) {
        return value == null;
    }

    /**
     * Used for {@code JsonInclude.Include#NON_ABSENT} checking.
     *
     * @param context The encoder context
     * @param value The value to check
     * @return Return {@code true} if the value is absent
     */
    default boolean isAbsent(@NonNull EncoderContext context, @Nullable T value) {
        return value == null;
    }

    /**
     * Used for {@code JsonInclude.Include#NON_DEFAULT} checking.
     *
     * @param context The encoder context
     * @param value The check to check
     * @return Return {@code true} if the value is the default value
     * @since 1.14
     */
    default boolean isDefault(@NonNull EncoderContext context, @NonNull T value) {
        return false;
    }

    /**
     * Context object passes to the
     * {@link #serialize(Encoder, EncoderContext, Argument, Object)}  method.
     */
    interface EncoderContext extends SerializerLocator, PropertyReferenceManager, NamingStrategyLocator {

        /**
         * @return Conversion service
         */
        @NonNull
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
        <B, P> SerializationReference<B, P> resolveReference(
                @NonNull SerializationReference<B, P> reference
        );

        /**
         * Get the {@link SerdeConfiguration} for this context.
         *
         * @return The {@link SerdeConfiguration}, or an empty optional if the default should be used
         * @since 2.7.0
         */
        @NonNull
        default Optional<SerdeConfiguration> getSerdeConfiguration() {
            return Optional.empty();
        }

        /**
         * Get the {@link SerializationConfiguration} for this context.
         *
         * @return The {@link SerializationConfiguration}, or an empty optional if the default should be used
         * @since 2.7.0
         */
        @NonNull
        default Optional<SerializationConfiguration> getSerializationConfiguration() {
            return Optional.empty();
        }

        /**
         * Get the active serialization format features for this context.
         *
         * @return The active serialization format features
         * @since 3.0
         */
        @NonNull
        default Set<SerdeConfiguration.Feature> getFeatures() {
            return SerdeConfiguration.serializationFeatures(getSerializationConfiguration().orElse(null));
        }

        /**
         * Create a context with serialization format features overridden by annotation metadata.
         *
         * @param featuresWith    Features to enable
         * @param featuresWithout Features to disable
         * @return The derived context
         * @since 3.0
         */
        @NonNull
        default EncoderContext withFeatures(@NonNull Set<SerdeConfiguration.Feature> featuresWith,
                                            @NonNull Set<SerdeConfiguration.Feature> featuresWithout) {
            if (featuresWith.isEmpty() && featuresWithout.isEmpty()) {
                return this;
            }
            return featureContext(this, overrideFeatures(getFeatures(), featuresWith, featuresWithout));
        }

        private static EncoderContext featureContext(EncoderContext delegate,
                                                     Set<SerdeConfiguration.Feature> features) {
            return new EncoderContext() {
                @Override
                public ConversionService getConversionService() {
                    return delegate.getConversionService();
                }

                @Override
                public boolean hasView(Class<?>... views) {
                    return delegate.hasView(views);
                }

                @Override
                public <B, P> SerializationReference<B, P> resolveReference(@NonNull SerializationReference<B, P> reference) {
                    return delegate.resolveReference(reference);
                }

                @Override
                public Optional<SerdeConfiguration> getSerdeConfiguration() {
                    return delegate.getSerdeConfiguration();
                }

                @Override
                public Optional<SerializationConfiguration> getSerializationConfiguration() {
                    return delegate.getSerializationConfiguration();
                }

                @Override
                public Set<SerdeConfiguration.Feature> getFeatures() {
                    return features;
                }

                @Override
                public EncoderContext withFeatures(@NonNull Set<SerdeConfiguration.Feature> featuresWith,
                                                   @NonNull Set<SerdeConfiguration.Feature> featuresWithout) {
                    if (featuresWith.isEmpty() && featuresWithout.isEmpty()) {
                        return this;
                    }
                    return featureContext(delegate, overrideFeatures(features, featuresWith, featuresWithout));
                }

                @Override
                public <T, D extends Serializer<? extends T>> D findCustomSerializer(@NonNull Class<? extends D> serializerClass) throws SerdeException {
                    return delegate.findCustomSerializer(serializerClass);
                }

                @Override
                public <T> Serializer<? super T> findSerializer(@NonNull Argument<? extends T> forType) throws SerdeException {
                    return delegate.findSerializer(forType);
                }

                @Override
                public <D extends PropertyNamingStrategy> D findNamingStrategy(@NonNull Class<? extends D> namingStrategyClass) throws SerdeException {
                    return delegate.findNamingStrategy(namingStrategyClass);
                }

                @Override
                public <B, P> void pushManagedRef(@NonNull PropertyReference<B, P> reference) {
                    delegate.pushManagedRef(reference);
                }

                @Override
                public void popManagedRef() {
                    delegate.popManagedRef();
                }
            };
        }

        private static Set<SerdeConfiguration.Feature> overrideFeatures(Set<SerdeConfiguration.Feature> base,
                                                                        Set<SerdeConfiguration.Feature> featuresWith,
                                                                        Set<SerdeConfiguration.Feature> featuresWithout) {
            EnumSet<SerdeConfiguration.Feature> features = EnumSet.noneOf(SerdeConfiguration.Feature.class);
            features.addAll(base);
            features.addAll(featuresWith);
            features.removeAll(featuresWithout);
            return Set.copyOf(features);
        }
    }
}
