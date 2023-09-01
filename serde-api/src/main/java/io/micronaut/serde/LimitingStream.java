/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Utility class to check data limits in encoders and decoders. Currently, the only limit that is
 * checked is the nesting depth.
 * <p>
 * This class can be used in both implementation "patterns" for {@link Encoder} (and
 * {@link Decoder}): For the same-object pattern, where {@link Encoder#encodeObject(Argument)}
 * returns the same instance, the encoder should call {@link #increaseDepth()}, with a
 * corresponding {@link #decreaseDepth()} call in {@link Encoder#finishStructure()}. Conversely, if
 * the encoder is implemented using the child-object pattern, where
 * {@link Encoder#encodeObject(Argument)} returns a child encoder responsible for the subtree, that
 * child encoder should be instantiated using the new limits returned by {@link #childLimits()}.
 * <p>
 * If there is a limit violation, {@link #childLimits()} or {@link #increaseDepth()} will throw an
 * exception.
 *
 * @author Jonas Konrad
 * @since 2.0.0
 */
@Internal
public abstract class LimitingStream {
    /**
     * Default nesting depth limit.
     */
    public static final int DEFAULT_MAXIMUM_DEPTH = 1024;
    /**
     * Default limits, only use this when no {@link SerdeConfiguration} is available.
     */
    public static final RemainingLimits DEFAULT_LIMITS = new RemainingLimits(DEFAULT_MAXIMUM_DEPTH);

    private int remainingDepth;

    public LimitingStream(@NonNull RemainingLimits remainingLimits) {
        this.remainingDepth = remainingLimits.remainingDepth;
    }

    /**
     * Get a snapshot of our current limits to be passed to the constructor. This can be used for
     * buffering decoders, when a new decoder takes over but no change in depth takes place.
     *
     * @return The current limits
     */
    @NonNull
    protected final RemainingLimits ourLimits() {
        return new RemainingLimits(remainingDepth);
    }

    /**
     * Get the limits of a new child encoder.
     *
     * @return The new limits
     * @throws SerdeException If there is a nesting depth limit violation
     */
    @NonNull
    protected final RemainingLimits childLimits() throws SerdeException {
        if (remainingDepth == 0) {
            reportMaxDepthExceeded();
        }
        return new RemainingLimits(remainingDepth - 1);
    }

    /**
     * Increase the current depth.
     *
     * @throws SerdeException If there is a nesting depth limit violation
     */
    protected final void increaseDepth() throws SerdeException {
        if (remainingDepth == 0) {
            reportMaxDepthExceeded();
        }
        remainingDepth--;
    }

    /**
     * Decrease the current depth, always needs a corresponding {@link #increaseDepth()} call.
     */
    protected final void decreaseDepth() {
        remainingDepth++;
    }

    private void reportMaxDepthExceeded() throws SerdeException {
        boolean encoder = this instanceof Encoder;
        throw new SerdeException("Maximum depth exceeded while " + (encoder ? "serializing" : "deserializing") + ". The maximum nesting depth can be increased, if necessary, using the " + SerdeConfiguration.PREFIX + ".maximum-nesting-depth config property.");
    }

    /**
     * Get the configured limits.
     *
     * @param configuration The serde configuration
     * @return The configured limits
     */
    public static RemainingLimits limitsFromConfiguration(SerdeConfiguration configuration) {
        return new RemainingLimits(configuration.getMaximumNestingDepth());
    }

    /**
     * This data structure contains the limits for this stream.
     */
    public static final class RemainingLimits {
        final int remainingDepth;

        private RemainingLimits(int remainingDepth) {
            this.remainingDepth = remainingDepth;
        }
    }
}
