package io.micronaut.json;

import java.util.Arrays;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

/**
 * Extends the {@link io.micronaut.core.type.Argument} interface to allow modelling
 * of {@link java.lang.reflect.WildcardType} computed at build time.
 *
 * TODO: Move to core
 */
public interface WildcardArgument<T> extends Argument<T> {
    /**
     * Constant used to refer to {@code ?}.
     */
    WildcardArgument<?> ANY = new WildcardArgument<Object>() {
        @Override
        @NonNull
        public String getName() {
            return "?";
        }

        @Override
        public boolean equalsType(Argument<?> other) {
            return other == ANY;
        }

        @Override
        public int typeHashCode() {
            return Object.class.hashCode();
        }

        @Override
        public Class<Object> getType() {
            return Object.class;
        }
    };

    @NonNull
    default Argument<?>[] getUpperBounds() {
        return new Argument[] {
                Argument.OBJECT_ARGUMENT
        };
    }


    default @Nullable Argument<?>[] getLowerBounds() {
        return null;
    }

    /**
     * Create a new wild card argument for the given upper bounds.
     * @param upperBounds The upper bounds
     * @return The wild card argument
     */
    @NonNull
    static WildcardArgument<?> ofUpperBounds(Argument<?>...upperBounds) {
        if (ArrayUtils.isEmpty(upperBounds)) {
            throw new IllegalArgumentException("At least one upper bound is required");
        }
        final Argument<?>[] arrayCopy = Arrays.copyOf(upperBounds, upperBounds.length);
        return new WildcardUpperBounds<>(
                Object.class,
                "?",
                arrayCopy
        );
    }

    /**
     * Create a new wild card argument for the given lower bounds.
     * @param lowerBounds The lower bounds
     * @return The wild card argument
     */
    @NonNull
    static WildcardArgument<?> ofLowerBounds(Argument<?>...lowerBounds) {
        if (ArrayUtils.isEmpty(lowerBounds)) {
            throw new IllegalArgumentException("At least one lower bound is required");
        }

        final Argument<?>[] arrayCopy = Arrays.copyOf(lowerBounds, lowerBounds.length);
        return new WildcardLowerBounds<>(
                Object.class,
                "?",
                arrayCopy
        );
    }
}
